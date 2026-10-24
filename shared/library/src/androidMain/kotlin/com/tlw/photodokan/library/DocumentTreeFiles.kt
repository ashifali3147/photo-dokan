package com.tlw.photodokan.library

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.io.IOException
import java.io.FileNotFoundException
import java.io.FileOutputStream

/**
 * [LibraryFiles] in a folder the operator picked with Android's document picker (a "tree"), e.g.
 * Documents/Photo Dokan or a folder on the SD card. Unlike app storage it stays when the app is
 * uninstalled, and it can be copied to the PC over USB.
 *
 * Each folder listing is a query to the folder's provider, so everything a listing returns is
 * remembered and a path is looked up again only when it isn't known, or turns out to be gone.
 */
class DocumentTreeFiles(
    private val resolver: ContentResolver,
    private val tree: Uri,
    private val rootId: String = DocumentsContract.getTreeDocumentId(tree),
) : LibraryFiles {

    private class Entry(val id: String, val isDirectory: Boolean)

    private val known = HashMap<String, Entry>() // path → entry
    private val lock = Any()

    override fun exists(path: String) = find(path) != null

    override fun isDirectory(path: String) = find(path)?.isDirectory == true

    override fun list(path: String): List<String> {
        val folder = find(path)?.takeIf { it.isDirectory } ?: return emptyList()
        return children(key(path), folder.id).keys.toList()
    }

    override fun createDirectories(path: String) {
        val parts = parts(path)
        // Rare enough (once per new customer) to check afresh: the day may have been deleted outside.
        parts.firstOrNull()?.let(::forget)
        for (i in parts.indices) {
            val walked = parts.take(i + 1).joinToString("/")
            val entry = find(walked)
            when {
                entry == null -> createDirectory(walked) || throw IOException("Could not create the folder $walked")
                !entry.isDirectory -> throw IOException("$walked is a file, not a folder")
            }
        }
    }

    override fun createDirectory(path: String): Boolean {
        val (parentPath, name) = split(path)
        val parent = folder(parentPath)
        if (name in children(parentPath, parent.id)) return false
        val created = DocumentsContract.createDocument(resolver, uri(parent.id), Document.MIME_TYPE_DIR, name)
            ?: throw IOException("Could not create the folder $path")
        // A provider gives a taken name a suffix, "0001 (1)", rather than failing.
        if (children(parentPath, parent.id)[name]?.id == DocumentsContract.getDocumentId(created)) return true
        DocumentsContract.deleteDocument(resolver, created)
        return false
    }

    override fun read(path: String): ByteArray = try {
        readOnce(path)
    } catch (e: FileNotFoundException) {
        forget(key(path)) // deleted or replaced from outside since it was listed
        readOnce(path)
    }

    /**
     * Writes a temporary file, then swaps it in. Providers can't rename over a file, so the old one
     * is deleted just before the rename; a crash in that moment leaves the new file under its
     * temporary name rather than a half-written one.
     */
    override fun write(path: String, bytes: ByteArray) {
        val (parentPath, name) = split(path)
        val parent = folder(parentPath)
        val siblings = children(parentPath, parent.id)
        val temp = ".$name.tmp"
        siblings[temp]?.let { DocumentsContract.deleteDocument(resolver, uri(it.id)) }
        val tempUri = DocumentsContract.createDocument(resolver, uri(parent.id), "application/octet-stream", temp)
            ?: throw IOException("Could not create $path")
        writeFully(tempUri, bytes)

        siblings[name]?.let { DocumentsContract.deleteDocument(resolver, uri(it.id)) }
        val renamed = try {
            DocumentsContract.renameDocument(resolver, tempUri, name)
        } catch (e: UnsupportedOperationException) {
            null
        }
        if (renamed == null) {
            // A provider that can't rename: write the file in place instead.
            DocumentsContract.deleteDocument(resolver, tempUri)
            val target = DocumentsContract.createDocument(resolver, uri(parent.id), "application/octet-stream", name)
                ?: throw IOException("Could not create $path")
            writeFully(target, bytes)
        }
        children(parentPath, parent.id) // the files' ids may have changed
    }

    /** True while the folder is still there and the app may still use it. */
    fun isReachable(): Boolean = try {
        resolver.query(uri(rootId), arrayOf(Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.moveToFirst() } == true
    } catch (e: Exception) {
        false // SecurityException once the permission is gone, FileNotFoundException once the folder is
    }

    private fun readOnce(path: String): ByteArray {
        val file = find(path)?.takeIf { !it.isDirectory } ?: throw FileNotFoundException("No file $path")
        return resolver.openInputStream(uri(file.id))?.use { it.readBytes() } ?: throw IOException("Could not open $path")
    }

    private fun writeFully(target: Uri, bytes: ByteArray) {
        val descriptor = resolver.openFileDescriptor(target, "wt") ?: throw IOException("Could not open $target")
        descriptor.use {
            FileOutputStream(it.fileDescriptor).use { out ->
                out.write(bytes)
                out.flush()
                it.fileDescriptor.sync() // safely stored before the old file goes
            }
        }
    }

    private fun folder(path: String): Entry =
        find(path)?.takeIf { it.isDirectory } ?: throw IOException("No folder ${path.ifEmpty { "for photos" }}")

    /** The entry at [path], or null when any part of it is missing. */
    private fun find(path: String, retry: Boolean = true): Entry? {
        val key = key(path)
        if (key.isEmpty()) return Entry(rootId, isDirectory = true)
        synchronized(lock) { known[key] }?.let { return it }
        val parentKey = key.substringBeforeLast('/', "")
        val parent = find(parentKey)?.takeIf { it.isDirectory } ?: return null
        val entry = children(parentKey, parent.id)[key.substringAfterLast('/')]
        if (entry == null && retry && parentKey.isNotEmpty() && !stillThere(parent.id)) {
            forget(parentKey) // a remembered folder was deleted from outside: look it up again
            return find(path, retry = false)
        }
        return entry
    }

    private fun stillThere(id: String): Boolean = try {
        resolver.query(uri(id), arrayOf(Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.moveToFirst() } == true
    } catch (e: Exception) {
        false
    }

    /** Lists the folder at [path] by name, and remembers what is in it now in place of what was. */
    private fun children(path: String, id: String): Map<String, Entry> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        val cursor = resolver.query(uri, columns, null, null, null) ?: throw IOException("Could not read the photo folder")
        val entries = cursor.use {
            buildMap {
                while (it.moveToNext()) put(it.getString(1), Entry(it.getString(0), it.getString(2) == Document.MIME_TYPE_DIR))
            }
        }
        val prefix = if (path.isEmpty()) "" else "$path/"
        synchronized(lock) {
            known.keys.removeAll { it.startsWith(prefix) && it.length > prefix.length && it.substring(prefix.length).substringBefore('/') !in entries }
            for ((name, entry) in entries) {
                val previous = known.put(prefix + name, entry)
                // Same name, different document: what was remembered below it is out of date.
                if (previous != null && previous.id != entry.id) known.keys.removeAll { it.startsWith("$prefix$name/") }
            }
        }
        return entries
    }

    /** Forgets [path] and everything under it. */
    private fun forget(path: String) = synchronized(lock) { known.keys.removeAll { it == path || it.startsWith("$path/") } }

    private fun uri(id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    private fun parts(path: String) = path.split('/').filter { it.isNotEmpty() }

    private fun key(path: String) = parts(path).joinToString("/")

    private fun split(path: String): Pair<String, String> {
        val parts = parts(path)
        require(parts.isNotEmpty()) { "No file name in \"$path\"" }
        return parts.dropLast(1).joinToString("/") to parts.last()
    }

    companion object {
        const val FOLDER_NAME = "Photo Dokan"

        /**
         * The library inside the folder the operator picked: that folder itself when it is already
         * called Photo Dokan, otherwise a Photo Dokan folder in it, made if needed.
         */
        fun open(resolver: ContentResolver, tree: Uri): DocumentTreeFiles {
            val picked = DocumentTreeFiles(resolver, tree)
            val pickedName = resolver.query(picked.uri(picked.rootId), arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
            if (pickedName == FOLDER_NAME) return picked
            picked.createDirectories(FOLDER_NAME)
            return DocumentTreeFiles(resolver, tree, rootId = checkNotNull(picked.find(FOLDER_NAME)).id)
        }
    }
}

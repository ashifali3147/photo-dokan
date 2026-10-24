package com.tlw.photodokan.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.tlw.photodokan.library.DocumentTreeFiles
import com.tlw.photodokan.library.LibraryFiles
import kotlinx.io.IOException

/**
 * The folder the operator picked for the photo library, remembered across restarts. The library
 * is built once at start-up, so this stands in for the folder and passes everything on to it;
 * picking the folder again (after it was moved or its card taken out) needs no restart.
 */
class PhotoFolder(private val context: Context) : LibraryFiles {

    private val prefs = context.getSharedPreferences("photo-folder", Context.MODE_PRIVATE)

    @Volatile
    private var files: DocumentTreeFiles? = null

    /** Whether a folder was ever picked, so a missing one is "lost" rather than "not set up yet". */
    val wasPicked: Boolean get() = prefs.contains(KEY_TREE)

    /** True when a folder has been picked, the app may still use it and it is still there. */
    fun isReady(): Boolean {
        val tree = prefs.getString(KEY_TREE, null)?.let(Uri::parse) ?: return false
        val allowed = context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission && it.isWritePermission }
        if (!allowed) return false
        val current = files ?: try {
            DocumentTreeFiles.open(context.contentResolver, tree)
        } catch (e: Exception) {
            Log.w(TAG, "The photo folder could not be opened", e)
            return false
        }
        files = current
        return current.isReachable()
    }

    /** Keeps [tree], just picked, as the photo folder from now on. False when it can't be used. */
    fun use(tree: Uri): Boolean {
        val resolver = context.contentResolver
        val access = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            resolver.takePersistableUriPermission(tree, access)
            files = DocumentTreeFiles.open(resolver, tree)
        } catch (e: Exception) {
            Log.w(TAG, "The picked folder could not be used", e)
            return false
        }
        prefs.edit().putString(KEY_TREE, tree.toString()).apply()
        // Android keeps a limited number of these: let go of folders picked before.
        resolver.persistedUriPermissions.filter { it.uri != tree }.forEach { resolver.releasePersistableUriPermission(it.uri, access) }
        return true
    }

    private val current: LibraryFiles get() = files ?: throw IOException("Choose a folder for the photos first.")

    override fun exists(path: String) = current.exists(path)
    override fun isDirectory(path: String) = current.isDirectory(path)
    override fun list(path: String) = current.list(path)
    override fun createDirectories(path: String) = current.createDirectories(path)
    override fun createDirectory(path: String) = current.createDirectory(path)
    override fun read(path: String) = current.read(path)
    override fun write(path: String, bytes: ByteArray) = current.write(path, bytes)

    private companion object {
        const val KEY_TREE = "tree"
        const val TAG = "PhotoDokanFolder"
    }
}

package com.tlw.photodokan.library

import kotlinx.io.IOException
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * The few file operations the photo library needs, on paths relative to the library folder with
 * "/" between parts ("2026/10/22/0001"). The PC uses plain files; the phone uses a folder the
 * operator picked through Android's document picker, which has no ordinary file paths.
 */
interface LibraryFiles {
    fun exists(path: String): Boolean
    fun isDirectory(path: String): Boolean

    /** Names of the entries in the folder at [path], or none when it isn't a folder. */
    fun list(path: String): List<String>

    /** Creates the folder at [path] and any missing parents; does nothing when it exists. */
    fun createDirectories(path: String)

    /** Creates the folder at [path], whose parent exists. False when something is already there. */
    fun createDirectory(path: String): Boolean

    fun read(path: String): ByteArray

    /** Replaces the file at [path] so a crash or power cut never leaves it half-written. */
    fun write(path: String, bytes: ByteArray)
}

/** [LibraryFiles] in a folder on disk. */
class SystemLibraryFiles(
    private val root: Path,
    private val fs: FileSystem = SystemFileSystem,
) : LibraryFiles {

    override fun exists(path: String) = fs.exists(resolve(path))

    override fun isDirectory(path: String) = fs.metadataOrNull(resolve(path))?.isDirectory == true

    override fun list(path: String): List<String> =
        if (isDirectory(path)) fs.list(resolve(path)).map { it.name } else emptyList()

    override fun createDirectories(path: String) = fs.createDirectories(resolve(path))

    override fun createDirectory(path: String): Boolean = try {
        fs.createDirectories(resolve(path), mustCreate = true)
        true
    } catch (e: IOException) {
        if (exists(path)) false else throw e
    }

    override fun read(path: String): ByteArray = fs.readBytes(resolve(path))

    override fun write(path: String, bytes: ByteArray) = fs.writeAtomically(resolve(path), bytes)

    private fun resolve(path: String): Path {
        val parts = path.split('/').filter { it.isNotEmpty() }
        return if (parts.isEmpty()) root else Path(root, *parts.toTypedArray())
    }
}

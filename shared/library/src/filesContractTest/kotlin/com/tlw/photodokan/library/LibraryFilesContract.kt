package com.tlw.photodokan.library

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What every [LibraryFiles] must do for the photo library to work on it. */
abstract class LibraryFilesContract {

    /** An empty library folder. */
    abstract val files: LibraryFiles

    @Test
    fun foldersAreCreatedWithTheirParents() {
        files.createDirectories("2026/10/22")
        files.createDirectories("2026/10/22") // again: nothing happens
        assertTrue(files.isDirectory("2026"))
        assertTrue(files.isDirectory("2026/10/22"))
        assertEquals(listOf("10"), files.list("2026"))
    }

    @Test
    fun aFolderIsCreatedOnlyOnce() {
        files.createDirectories("2026/10/22")
        assertTrue(files.createDirectory("2026/10/22/0001"))
        assertFalse(files.createDirectory("2026/10/22/0001"), "already there")
        assertEquals(listOf("0001"), files.list("2026/10/22"))
    }

    @Test
    fun filesAreReadBackByteForByte() {
        files.createDirectories("a")
        val bytes = ByteArray(300_000) { (it * 31).toByte() }
        files.write("a/original.jpg", bytes)
        assertTrue(files.exists("a/original.jpg"))
        assertFalse(files.isDirectory("a/original.jpg"))
        assertContentEquals(bytes, files.read("a/original.jpg"))
    }

    @Test
    fun writingAgainReplacesTheWholeFile() {
        files.createDirectories("a")
        files.write("a/recipe.json", "a much longer first version".encodeToByteArray())
        files.write("a/recipe.json", "short".encodeToByteArray())
        assertEquals("short", files.read("a/recipe.json").decodeToString())
        assertEquals(listOf("recipe.json"), files.list("a"), "no temporary file left behind")
    }

    @Test
    fun missingThingsAreNotThere() {
        assertFalse(files.exists("2026"))
        assertFalse(files.isDirectory("2026"))
        assertEquals(emptyList(), files.list("2026"))
        files.createDirectories("a")
        files.write("a/file.json", byteArrayOf(1))
        assertEquals(emptyList(), files.list("a/file.json"), "a file has no entries")
        assertFalse(files.exists("a/file.json/customer.json"))
    }

    @Test
    fun theRootListsTheYears() {
        files.createDirectories("2025/12/31")
        files.createDirectories("2026/01/01")
        assertEquals(listOf("2025", "2026"), files.list("").sorted())
    }
}

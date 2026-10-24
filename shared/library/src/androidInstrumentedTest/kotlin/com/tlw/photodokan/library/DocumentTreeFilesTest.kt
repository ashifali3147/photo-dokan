package com.tlw.photodokan.library

import androidx.test.platform.app.InstrumentationRegistry
import com.tlw.photodokan.core.library.StudioDate
import com.tlw.photodokan.core.model.EditRecipe
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The phone's library folder, through the same document calls the picked folder gets. */
class DocumentTreeFilesTest : LibraryFilesContract() {

    private val context = InstrumentationRegistry.getInstrumentation().context
    private val disk: File = TestDocumentsProvider.reset(context)
    override val files = DocumentTreeFiles(context.contentResolver, TestDocumentsProvider.tree())

    private val today = StudioDate(2026, 10, 24)
    private val photo = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())

    @Test
    fun thePhotoLibraryWorksInAPickedFolder() {
        val library = FilePhotoLibrary(files)
        val first = library.create(today, "Ramesh Kumar", null, photo)
        val second = library.create(today, null, "98765 43210", photo)
        library.saveEdit(first, EditRecipe(presetId = "studio-slot"), photo)

        assertEquals(listOf(1, 2), library.customersOn(today).map { it.number })
        assertEquals(3, library.nextNumber(today))
        assertContentEquals(photo, library.readOriginal(second))
        assertContentEquals(photo, library.readFinal(first))
        assertEquals("studio-slot", library.readRecipe(first)?.presetId)
        assertEquals(listOf(second), library.search("43210"))
        assertTrue(File(disk, "2026/10/24/0001_Ramesh-Kumar/final.jpg").isFile, "ordinary folders on the card")
    }

    @Test
    fun aPhotoDokanFolderIsMadeInsideThePickedOne() {
        File(disk, "Documents").mkdir()
        val library = FilePhotoLibrary(DocumentTreeFiles.open(context.contentResolver, TestDocumentsProvider.tree("root/Documents")))
        library.create(today, null, null, photo)
        assertTrue(File(disk, "Documents/Photo Dokan/2026/10/24/0001/original.jpg").isFile)
    }

    @Test
    fun aPickedPhotoDokanFolderIsUsedAsItIs() {
        File(disk, "Photo Dokan").mkdir()
        val library = FilePhotoLibrary(DocumentTreeFiles.open(context.contentResolver, TestDocumentsProvider.tree("root/Photo Dokan")))
        library.create(today, null, null, photo)
        assertTrue(File(disk, "Photo Dokan/2026/10/24/0001/original.jpg").isFile)
        assertFalse(File(disk, "Photo Dokan/Photo Dokan").exists())
    }

    @Test
    fun foldersChangedFromOutsideAreLookedUpAgain() {
        val library = FilePhotoLibrary(files)
        library.create(today, null, null, photo)
        assertEquals(1, library.customersOn(today).size)

        // The operator deletes the day in a file manager, then takes the next customer.
        File(disk, "2026/10/24").deleteRecursively()
        assertEquals(emptyList(), library.customersOn(today))
        val again = library.create(today, null, null, photo)
        assertEquals(1, again.number)
        assertContentEquals(photo, library.readOriginal(again))
    }

    @Test
    fun aFileReplacedFromOutsideIsReadAfresh() {
        files.createDirectories("a")
        files.write("a/customer.json", "first".encodeToByteArray())
        assertEquals("first", files.read("a/customer.json").decodeToString())
        File(disk, "a/customer.json").writeText("second")
        assertEquals("second", files.read("a/customer.json").decodeToString())
    }

    @Test
    fun aFolderThatIsGoneIsNotReachable() {
        val picked = File(disk, "Photo Dokan").apply { mkdir() }
        val folder = DocumentTreeFiles(context.contentResolver, TestDocumentsProvider.tree("root/Photo Dokan"))
        assertTrue(folder.isReachable())
        picked.deleteRecursively()
        assertFalse(folder.isReachable())
    }
}

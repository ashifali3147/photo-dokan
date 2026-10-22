package com.tlw.photodokan.library

import com.tlw.photodokan.core.library.LibraryLayout
import com.tlw.photodokan.core.library.StudioDate
import com.tlw.photodokan.core.model.EditRecipe
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readByteArray
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FilePhotoLibraryTest {

    private val root = Path(SystemTemporaryDirectory, "photo-dokan-test-${Random.nextLong().toULong()}")
    private val library = FilePhotoLibrary(root)
    private val today = StudioDate(2026, 10, 22)
    private val yesterday = StudioDate(2026, 10, 21)
    private val photo = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())

    @AfterTest
    fun cleanUp() = deleteTree(root)

    private fun deleteTree(path: Path) {
        val fs = SystemFileSystem
        if (fs.metadataOrNull(path)?.isDirectory == true) fs.list(path).forEach(::deleteTree)
        if (fs.exists(path)) fs.delete(path)
    }

    private fun file(vararg parts: String) = Path(root, *parts)
    private fun read(path: Path) = SystemFileSystem.source(path).buffered().use { it.readByteArray() }

    @Test
    fun customersAreNumberedPerDayInYearMonthDayFolders() {
        val first = library.create(today, "Ramesh Kumar", null, photo)
        val second = library.create(today, null, null, photo)
        val other = library.create(yesterday, null, null, photo)

        assertEquals(listOf(1, 2, 1), listOf(first.number, second.number, other.number))
        assertTrue(SystemFileSystem.exists(file("2026", "10", "22", "0001_Ramesh-Kumar", "customer.json")))
        assertTrue(SystemFileSystem.exists(file("2026", "10", "22", "0002", "original.jpg")))
        assertTrue(SystemFileSystem.exists(file("2026", "10", "21", "0001")))
    }

    @Test
    fun originalIsStoredByteForByte() {
        val customer = library.create(today, null, null, photo)
        assertContentEquals(photo, library.readOriginal(customer))
    }

    @Test
    fun numbersContinueAfterTheStudiosExistingFolders() {
        SystemFileSystem.createDirectories(file("2026", "10", "22", "0005_Old"))
        assertEquals(6, library.nextNumber(today))
        assertEquals(6, library.create(today, null, null, photo).number)
    }

    @Test
    fun nextNumberIsWhatCreateGives() {
        assertEquals(1, library.nextNumber(today))
        assertEquals(1, library.create(today, null, null, photo).number)
        assertEquals(2, library.nextNumber(today))
        assertEquals(1, library.nextNumber(yesterday))
    }

    @Test
    fun editSavesRecipeAndFinalAndLeavesTheOriginalAlone() {
        val customer = library.create(today, null, null, photo)
        assertNull(library.readRecipe(customer))
        assertNull(library.readFinal(customer))

        val recipe = EditRecipe(presetId = "studio-slot", exposure = 0.2)
        val final = byteArrayOf(9, 9, 9)
        library.saveEdit(customer, recipe, final)
        library.saveEdit(customer, recipe.copy(exposure = 0.3), final)

        assertEquals(0.3, library.readRecipe(customer)?.exposure)
        assertContentEquals(final, library.readFinal(customer))
        assertContentEquals(photo, library.readOriginal(customer))
        val leftovers = SystemFileSystem.list(file("2026", "10", "22", "0001")).map { it.name }.filter { it.endsWith(".tmp") }
        assertEquals(emptyList(), leftovers)
    }

    @Test
    fun dayListIsInNumberOrderAndSkipsForeignFolders() {
        repeat(3) { library.create(today, "Customer $it", null, photo) }
        SystemFileSystem.createDirectories(file("2026", "10", "22", "Scans"))

        assertEquals(listOf(1, 2, 3), library.customersOn(today).map { it.number })
        assertEquals(emptyList(), library.customersOn(StudioDate(2026, 1, 1)))
    }

    @Test
    fun searchFindsByNumberNameOrPhoneNewestFirst() {
        library.create(yesterday, "Ramesh", "98765 43210", photo)
        library.create(today, "Suresh", null, photo)
        library.create(today, "Ramesh", null, photo)

        val rameshes = library.search("ramesh")
        assertEquals(listOf(today to 2, yesterday to 1), rameshes.map { it.date to it.number })
        assertEquals(listOf("Ramesh"), library.search("3210").map { it.name })
        assertEquals(listOf(today, yesterday), library.search("1").map { it.date })
        assertEquals(3, library.search("").size)
    }

    @Test
    fun pngOriginalKeepsItsFormatInTheName() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)
        val customer = library.create(today, null, null, png)
        assertTrue(SystemFileSystem.exists(file("2026", "10", "22", "0001", "original.png")))
        assertFalse(SystemFileSystem.exists(file("2026", "10", "22", "0001", "original.jpg")))
        assertContentEquals(png, library.readOriginal(customer))
    }

    @Test
    fun recipesFromNewerVersionsStillOpen() {
        val customer = library.create(today, null, null, photo)
        library.saveEdit(customer, EditRecipe(presetId = "studio-slot"), byteArrayOf(1))
        val recipeFile = file("2026", "10", "22", "0001", LibraryLayout.RECIPE_FILE)
        SystemFileSystem.sink(recipeFile).buffered().use {
            it.write("""{"presetId":"studio-slot","exposure":0.1,"addedInAFutureVersion":true}""".encodeToByteArray())
        }
        assertEquals(0.1, library.readRecipe(customer)?.exposure)
    }

    @Test
    fun customDayPatternIsUsedForFoldersAndSearch() {
        val studio = FilePhotoLibrary(root, LibraryLayout("{yyyy}/{dd}-{MM}-{yyyy}"))
        studio.create(today, "Ramesh", null, photo)
        assertTrue(SystemFileSystem.exists(file("2026", "22-10-2026", "0001_Ramesh")))
        assertEquals(1, studio.customersOn(today).size)
        assertEquals(1, studio.search("ramesh").size)
    }

    @Test
    fun recipeJsonIsReadableByPeople() {
        val customer = library.create(today, null, null, photo)
        library.saveEdit(customer, EditRecipe(presetId = "studio-slot"), byteArrayOf(1))
        val text = read(file("2026", "10", "22", "0001", "recipe.json")).decodeToString()
        assertTrue("\"presetId\": \"studio-slot\"" in text, text)
        assertTrue("\"mode\": \"BLUR\"" in text, "defaults are written out, so the file says what it means")
    }
}

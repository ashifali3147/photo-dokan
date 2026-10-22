package com.tlw.photodokan.library

import com.tlw.photodokan.core.library.Customer
import com.tlw.photodokan.core.library.StudioDate
import com.tlw.photodokan.core.model.PrintQueue
import com.tlw.photodokan.core.model.SheetState
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FilePrintQueueStoreTest {

    private val dir = Path(SystemTemporaryDirectory, "photo-dokan-test-${Random.nextLong().toULong()}")
    private val file = Path(dir, "settings", "print-queue.json")
    private val store = FilePrintQueueStore(file)

    @AfterTest
    fun cleanUp() = deleteTree(dir)

    private fun deleteTree(path: Path) {
        val fs = SystemFileSystem
        if (fs.metadataOrNull(path)?.isDirectory == true) fs.list(path).forEach(::deleteTree)
        if (fs.exists(path)) fs.delete(path)
    }

    @Test
    fun nothingSavedMeansAnEmptyQueueAndAFreshSheet() {
        assertEquals(PrintQueue(), store.load())
    }

    @Test
    fun savedQueueAndUsedSlotsComeBack() {
        val queue = PrintQueue(sheet = SheetState(setOf(0, 1, 2, 3))).add(Customer(StudioDate(2026, 10, 22), 7, "Ramesh"), 5)
        store.save(queue)
        assertEquals(queue, FilePrintQueueStore(file).load())
    }

    @Test
    fun aDamagedFileStartsOver() {
        SystemFileSystem.createDirectories(file.parent!!)
        SystemFileSystem.sink(file).buffered().use { it.write("{ not json".encodeToByteArray()) }
        assertEquals(PrintQueue(), store.load())
    }
}

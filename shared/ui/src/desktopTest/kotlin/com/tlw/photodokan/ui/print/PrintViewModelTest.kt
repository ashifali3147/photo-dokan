package com.tlw.photodokan.ui.print

import com.tlw.photodokan.core.library.Customer
import com.tlw.photodokan.core.library.StudioDate
import com.tlw.photodokan.core.model.EditRecipe
import com.tlw.photodokan.core.model.PrintQueue
import com.tlw.photodokan.core.model.SheetState
import com.tlw.photodokan.core.model.SheetTemplate
import com.tlw.photodokan.ui.customers.InMemoryPhotoLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PrintViewModelTest {

    private val template = SheetTemplate.STUDIO_A4_5X8 // 40 slots
    private val today = StudioDate(2026, 10, 22)
    private val library = InMemoryPhotoLibrary()
    private val store = InMemoryPrintQueueStore()
    private lateinit var orders: PrintOrders
    private lateinit var vm: PrintViewModel

    private val jpeg = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(36, 34, BufferedImage.TYPE_INT_RGB), "jpg", it) }.toByteArray()

    /** A saved customer with a final.jpg, as the Customers screen leaves them. */
    private fun customer(): Customer = library.create(today, null, null, jpeg).also {
        library.saveEdit(it, EditRecipe(presetId = "studio-slot"), jpeg)
    }

    private fun start(saved: PrintQueue = PrintQueue()) {
        store.saved = saved
        orders = PrintOrders(store)
        vm = PrintViewModel(orders, library, template, work = Dispatchers.Unconfined)
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val state get() = vm.state.value

    @Test
    fun emptyQueueStillShowsTheSheetInThePrinter() {
        start(PrintQueue(sheet = SheetState((0..9).toSet())))
        val sheet = state.sheets.single()
        assertTrue(sheet.placements.isEmpty())
        assertEquals((0..9).toSet(), sheet.previouslyUsedSlots)
    }

    @Test
    fun queuedCopiesFillTheFreeSlotsWithTheirPhotos() {
        val a = customer()
        val b = customer()
        start(PrintQueue(sheet = SheetState((0..4).toSet())).add(a, 4).add(b, 5))

        val sheet = state.sheets.single()
        assertTrue(sheet.reusesPartialSheet)
        assertEquals((5..13).toList(), sheet.placements.map { it.slotIndex })
        assertEquals(state.queue.orders.map { it.id }.toSet(), state.photos.keys)
    }

    @Test
    fun changesFromTheCustomersScreenShowUpHere() {
        start()
        val a = customer()
        orders.update { it.add(a, 5) }
        assertEquals(5, state.pack.copiesPlaced)
        assertEquals(setOf(state.queue.orders.single().id), state.photos.keys)
    }

    @Test
    fun copiesCanBeChangedAndOrdersRemoved() {
        start(PrintQueue().add(customer(), 5).add(customer(), 5))
        val first = state.queue.orders.first()

        vm.setCopies(first, 10)
        assertEquals(listOf(10, 5), state.queue.orders.map { it.copies })
        vm.setCopies(state.queue.orders.first(), 0)
        assertEquals(1, state.queue.orders.first().copies, "never fewer than one copy")

        vm.remove(state.queue.orders.first())
        assertEquals(1, state.queue.orders.size)
        assertEquals(state.queue, store.saved)
    }

    @Test
    fun overflowShowsSeveralSheets() {
        start(PrintQueue(sheet = SheetState((0 until 35).toSet())).add(customer(), 10))
        assertEquals(2, state.sheets.size)
        vm.showSheet(1)
        assertFalse(state.shownSheet.reusesPartialSheet)
        vm.showSheet(5)
        assertEquals(1, state.sheetIndex)
    }

    @Test
    fun markingPrintedEmptiesTheQueueAndKeepsUsedSlots() {
        start(PrintQueue().add(customer(), 4).add(customer(), 5))
        vm.markPrinted()

        assertTrue(state.queue.orders.isEmpty())
        assertEquals((0..8).toSet(), store.saved.sheet.usedSlots)
        assertEquals("Marked 9 photos as printed. 31 slots are left on this sheet.", state.message)
    }

    @Test
    fun aFullSheetAsksForANewOne() {
        start(PrintQueue(sheet = SheetState((0 until 35).toSet())).add(customer(), 5))
        vm.markPrinted()
        assertEquals(SheetState.FRESH, store.saved.sheet)
        assertEquals("Marked 5 photos as printed. Put a new sheet in the printer.", state.message)
    }

    @Test
    fun newSheetFreesEverySlotButKeepsTheQueue() {
        start(PrintQueue(sheet = SheetState(setOf(0, 1, 2))).add(customer(), 5))
        vm.newSheet()
        assertEquals(SheetState.FRESH, store.saved.sheet)
        assertEquals(1, state.queue.orders.size)
        assertEquals((0..4).toList(), state.shownSheet.placements.map { it.slotIndex })
    }

    @Test
    fun aCustomerWithoutAFinalPhotoStillGetsSlots() {
        val bare = library.create(today, null, null, jpeg)
        start(PrintQueue().add(bare, 5))
        assertEquals(5, state.pack.copiesPlaced)
        assertTrue(state.photos.isEmpty())
    }
}

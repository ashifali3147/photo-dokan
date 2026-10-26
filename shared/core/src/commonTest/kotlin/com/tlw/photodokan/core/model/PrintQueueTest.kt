package com.tlw.photodokan.core.model

import com.tlw.photodokan.core.library.Customer
import com.tlw.photodokan.core.library.StudioDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrintQueueTest {

    private val template = SheetTemplate.STUDIO_A4_5X8 // 40 slots
    private val today = StudioDate(2026, 10, 22)
    private val c1 = Customer(today, 1)
    private val c2 = Customer(today, 2)
    private val c3 = Customer(today, 3)

    @Test
    fun ordersKeepTheOrderTheyWereAdded() {
        val queue = PrintQueue().add(c2, 5).add(c1, 10)
        assertEquals(listOf(c2, c1), queue.orders.map { it.customer })
        assertEquals(15, queue.copies)
    }

    @Test
    fun addingACustomerAgainChangesCopiesInPlace() {
        val queue = PrintQueue().add(c1, 5).add(c2, 5).add(c1.copy(name = "Ramesh"), 10)
        assertEquals(listOf(10, 5), queue.orders.map { it.copies })
        assertEquals("Ramesh", queue.orders.first().customer.name)
        assertEquals(10, queue.orderFor(c1)?.copies)
    }

    @Test
    fun removeDropsOnlyThatCustomer() {
        val queue = PrintQueue().add(c1, 5).add(c2, 4).remove(c1)
        assertEquals(listOf(c2), queue.orders.map { it.customer })
        assertNull(queue.orderFor(c1))
    }

    @Test
    fun sameNumberOnAnotherDayIsAnotherCustomer() {
        val yesterdays = Customer(today.previous(), 1)
        val queue = PrintQueue().add(c1, 5).add(yesterdays, 5)
        assertEquals(2, queue.orders.size)
        assertEquals(listOf("2026-10-22/0001", "2026-10-21/0001"), queue.orders.map { it.id })
    }

    @Test
    fun packingStartsAtTheFreeSlotsOfTheSheetInThePrinter() {
        val queue = PrintQueue(sheet = SheetState((0..4).toSet())).add(c1, 5)
        val sheet = queue.pack(template).sheets.single()
        assertTrue(sheet.reusesPartialSheet)
        assertEquals((5..9).toList(), sheet.placements.map { it.slotIndex })
    }

    @Test
    fun printedEmptiesTheQueueAndRemembersUsedSlots() {
        val queue = PrintQueue().add(c1, 4).add(c2, 5).printed(template)
        assertEquals(emptyList(), queue.orders)
        assertEquals((0..8).toSet(), queue.sheet.usedSlots)

        // The next customer goes into the gap the 4-copy order left.
        val next = queue.add(c3, 5).pack(template).sheets.single()
        assertEquals((9..13).toList(), next.placements.map { it.slotIndex })
    }

    @Test
    fun ordersChangedWhilePrintingStayQueued() {
        val job = PrintQueue().add(c1, 5).add(c2, 5)
        // While the job printed, c2 went up to 10 copies and c3 was added.
        val now = job.add(c2, 10).add(c3, 4)

        val after = now.afterPrinting(job, template)
        assertEquals(listOf(c2 to 10, c3 to 4), after.orders.map { it.customer to it.copies })
        assertEquals((0..9).toSet(), after.sheet.usedSlots)
    }

    @Test
    fun aSheetThatComesOutFullLeavesAFreshOne() {
        val queue = PrintQueue(sheet = SheetState((0 until 35).toSet())).add(c1, 5).printed(template)
        assertEquals(SheetState.FRESH, queue.sheet)
    }

    @Test
    fun overflowContinuesOnASecondSheet() {
        val queue = PrintQueue(sheet = SheetState((0 until 35).toSet())).add(c1, 10)
        assertEquals(2, queue.pack(template).sheets.size)
        assertEquals((0..4).toSet(), queue.printed(template).sheet.usedSlots)
    }

    @Test
    fun newSheetKeepsTheQueue() {
        val queue = PrintQueue(sheet = SheetState(setOf(0, 1))).add(c1, 5).newSheet()
        assertEquals(SheetState.FRESH, queue.sheet)
        assertEquals(1, queue.orders.size)
    }

    @Test
    fun copiesMustBeInRange() {
        assertFailsWith<IllegalArgumentException> { PrintQueue().add(c1, 0) }
        assertFailsWith<IllegalArgumentException> { PrintQueue().add(c1, Order.MAX_COPIES + 1) }
    }

    @Test
    fun survivesJson() {
        val queue = PrintQueue(sheet = SheetState(setOf(0, 1, 2))).add(c1.copy(name = "Ramesh"), 5).add(c2, 10)
        assertEquals(queue, Json.decodeFromString<PrintQueue>(Json.encodeToString(queue)))
    }

    @Test
    fun aSlotCanBeLeftBlankAndFilledAgain() {
        val queue = PrintQueue().add(c1, 4)
        val blank = queue.toggleBlank(template, 0, 2)
        assertEquals(mapOf(0 to setOf(2)), blank.blanks)
        assertEquals(listOf(0, 1, 3, 4), blank.pack(template).sheets[0].placements.map { it.slotIndex })
        assertEquals(queue, blank.toggleBlank(template, 0, 2))
    }

    @Test
    fun aPrintedSlotCannotBeLeftBlank() {
        val queue = PrintQueue(sheet = SheetState(setOf(0, 1)))
        assertEquals(queue, queue.toggleBlank(template, 0, 1))
    }

    @Test
    fun theLastSlotASheetCanPrintOnCannotBeLeftBlank() {
        val queue = PrintQueue(sheet = SheetState((0..37).toSet())).toggleBlank(template, 0, 38)
        assertEquals(mapOf(0 to setOf(38)), queue.blanks)
        assertEquals(queue, queue.toggleBlank(template, 0, 39))
    }

    @Test
    fun printedSlotsOfTheSheetInThePrinterDontLimitTheNextSheet() {
        val queue = PrintQueue(sheet = SheetState(setOf(0, 1))).toggleBlank(template, 1, 0)
        assertEquals(mapOf(1 to setOf(0)), queue.blanks)
    }

    @Test
    fun printingClearsTheBlanksAndLeavesThoseSlotsFree() {
        val queue = PrintQueue().add(c1, 4).toggleBlank(template, 0, 2)
        val after = queue.printed(template)
        assertEquals(SheetState(setOf(0, 1, 3, 4)), after.sheet)
        assertEquals(emptyMap(), after.blanks)
    }

    @Test
    fun aNewSheetClearsTheBlanks() {
        val queue = PrintQueue(sheet = SheetState(setOf(0))).add(c1, 4).toggleBlank(template, 0, 3)
        val fresh = queue.newSheet()
        assertEquals(emptyMap(), fresh.blanks)
        assertEquals(queue.orders, fresh.orders)
    }

    @Test
    fun blanksSurviveJson() {
        val queue = PrintQueue().add(c1, 5).toggleBlank(template, 0, 4).toggleBlank(template, 1, 0)
        assertEquals(queue, Json.decodeFromString<PrintQueue>(Json.encodeToString(queue)))
    }

    @Test
    fun aQueueSavedBeforeBlanksExistedStillLoads() {
        val saved = """{"orders":[],"sheet":{"usedSlots":[1,2]}}"""
        assertEquals(PrintQueue(sheet = SheetState(setOf(1, 2))), Json.decodeFromString<PrintQueue>(saved))
    }
}

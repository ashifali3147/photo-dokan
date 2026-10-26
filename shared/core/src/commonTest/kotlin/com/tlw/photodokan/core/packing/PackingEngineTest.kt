package com.tlw.photodokan.core.packing

import com.tlw.photodokan.core.geometry.PaperSizes
import com.tlw.photodokan.core.geometry.SizeMm
import com.tlw.photodokan.core.model.Order
import com.tlw.photodokan.core.model.SheetState
import com.tlw.photodokan.core.model.SheetTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PackingEngineTest {

    private val sheet = SheetTemplate.STUDIO_A4_5X8 // 40 slots

    private fun order(id: String, copies: Int) = Order(id, "Customer $id", copies)

    private fun slotsOf(result: PackResult, sheetIndex: Int, orderId: String) =
        result.sheets[sheetIndex].placements.filter { it.orderId == orderId }.map { it.slotIndex }

    @Test
    fun fiveCopiesFillOneRow() {
        val result = PackingEngine.pack(sheet, SheetState.FRESH, listOf(order("0001", 5)))
        assertEquals(1, result.sheets.size)
        assertEquals(listOf(0, 1, 2, 3, 4), slotsOf(result, 0, "0001"))
        assertEquals(SheetState((0..4).toSet()), result.newState)
    }

    @Test
    fun tenCopiesFillTwoRows() {
        val result = PackingEngine.pack(sheet, SheetState.FRESH, listOf(order("0001", 10)))
        assertEquals((0..9).toList(), slotsOf(result, 0, "0001"))
    }

    @Test
    fun fourCopyOrderLeavesItsSlotForTheNextCustomer() {
        val result = PackingEngine.pack(sheet, SheetState.FRESH, listOf(order("A", 4), order("B", 5)))
        assertEquals(listOf(0, 1, 2, 3), slotsOf(result, 0, "A"))
        assertEquals(listOf(4, 5, 6, 7, 8), slotsOf(result, 0, "B"))
    }

    @Test
    fun reusedSheetPrintsOnlyIntoFreeSlots() {
        val first = PackingEngine.pack(sheet, SheetState.FRESH, listOf(order("A", 4)))
        val second = PackingEngine.pack(sheet, first.newState, listOf(order("B", 5)))

        val reused = second.sheets.single()
        assertTrue(reused.reusesPartialSheet)
        assertEquals(setOf(0, 1, 2, 3), reused.previouslyUsedSlots)
        assertEquals(listOf(4, 5, 6, 7, 8), reused.placements.map { it.slotIndex })
        assertTrue(reused.placements.none { it.slotIndex in reused.previouslyUsedSlots })
        assertEquals(SheetState((0..8).toSet()), second.newState)
    }

    @Test
    fun overflowContinuesOnAFreshSheet() {
        val used = SheetState((0 until 37).toSet()) // 3 slots left
        val result = PackingEngine.pack(sheet, used, listOf(order("A", 5)))

        assertEquals(2, result.sheets.size)
        assertEquals(listOf(37, 38, 39), result.sheets[0].placements.map { it.slotIndex })
        assertTrue(result.sheets[0].reusesPartialSheet)
        assertEquals(listOf(0, 1), result.sheets[1].placements.map { it.slotIndex })
        assertFalse(result.sheets[1].reusesPartialSheet)
        assertEquals(listOf(4, 5), result.sheets[1].placements.map { it.copy })
        assertEquals(SheetState(setOf(0, 1)), result.newState)
    }

    @Test
    fun exactlyFullSheetResetsStateToFresh() {
        val orders = (1..8).map { order("C$it", 5) }
        val result = PackingEngine.pack(sheet, SheetState.FRESH, orders)
        assertEquals(1, result.sheets.size)
        assertEquals(40, result.copiesPlaced)
        assertEquals(SheetState.FRESH, result.newState)
    }

    @Test
    fun fullStateStartsANewSheet() {
        val full = SheetState((0 until 40).toSet())
        val result = PackingEngine.pack(sheet, full, listOf(order("A", 5)))
        assertFalse(result.sheets.single().reusesPartialSheet)
        assertEquals(listOf(0, 1, 2, 3, 4), result.sheets.single().placements.map { it.slotIndex })
    }

    @Test
    fun batchOfCustomersSharesOneSheet() {
        val result = PackingEngine.pack(
            sheet,
            SheetState.FRESH,
            listOf(order("A", 5), order("B", 10), order("C", 4), order("D", 5)),
        )
        assertEquals(1, result.sheets.size)
        assertEquals(24, result.copiesPlaced)
        assertEquals((19..23).toList(), slotsOf(result, 0, "D"))
    }

    @Test
    fun noOrdersChangesNothing() {
        val state = SheetState(setOf(0, 1))
        val result = PackingEngine.pack(sheet, state, emptyList())
        assertTrue(result.sheets.isEmpty())
        assertEquals(state, result.newState)
    }

    @Test
    fun placementsNeverOverlapAndStayOnThePaper() {
        val result = PackingEngine.pack(sheet, SheetState.FRESH, listOf(order("A", 40)))
        val rects = result.sheets.single().placements.map { it.rect }
        for (i in rects.indices) {
            assertTrue(rects[i].isInside(PaperSizes.A4, sheet.minMarginMm), "Slot $i leaves the printable area")
            for (j in i + 1 until rects.size) {
                assertFalse(rects[i].overlaps(rects[j]), "Slots $i and $j overlap")
            }
        }
    }

    @Test
    fun rejectsStateWithSlotsOutsideTheSheet() {
        assertFailsWith<IllegalArgumentException> {
            PackingEngine.pack(sheet, SheetState(setOf(40)), listOf(order("A", 1)))
        }
    }

    @Test
    fun worksForOtherGrids() {
        val small = SheetTemplate("4 × 6 in, 2 × 4", PaperSizes.PHOTO_4X6, SizeMm(35.0, 35.0), 2, 4, 2.0)
        val result = PackingEngine.pack(small, SheetState.FRESH, listOf(order("A", 10)))
        assertEquals(listOf(8, 2), result.sheets.map { it.placements.size })
    }

    // Blank slots: the operator taps a slot to leave it empty on this print.

    @Test
    fun copiesSkipABlankSlot() {
        val result = PackingEngine.pack(sheet, SheetState.FRESH, listOf(order("A", 4)), blanks = mapOf(0 to setOf(2)))
        assertEquals(listOf(0, 1, 3, 4), slotsOf(result, 0, "A"))
        assertEquals(setOf(2), result.sheets[0].blankSlots)
    }

    @Test
    fun aBlankAtTheEndOfARowStartsTheNextCustomerOnANewRow() {
        val result = PackingEngine.pack(sheet, SheetState.FRESH, listOf(order("A", 4), order("B", 5)), blanks = mapOf(0 to setOf(4)))
        assertEquals(listOf(0, 1, 2, 3), slotsOf(result, 0, "A"))
        assertEquals(listOf(5, 6, 7, 8, 9), slotsOf(result, 0, "B"))
    }

    @Test
    fun blankSlotsGetNoInkSoTheyStayFreeAfterPrinting() {
        val result = PackingEngine.pack(sheet, SheetState.FRESH, listOf(order("A", 4)), blanks = mapOf(0 to setOf(2)))
        assertEquals(SheetState(setOf(0, 1, 3, 4)), result.newState)
    }

    @Test
    fun aSheetFullOnlyBecauseOfBlanksIsStillPartUsedAfterwards() {
        val result = PackingEngine.pack(sheet, SheetState.FRESH, listOf(order("A", 39)), blanks = mapOf(0 to setOf(39)))
        assertEquals(1, result.sheets.size)
        assertEquals(SheetState((0..38).toSet()), result.newState)
    }

    @Test
    fun blanksPushCopiesOntoTheNextSheet() {
        val result = PackingEngine.pack(sheet, SheetState.FRESH, listOf(order("A", 35)), blanks = mapOf(0 to (0..9).toSet()))
        assertEquals((10..39).toList(), slotsOf(result, 0, "A"))
        assertEquals((0..4).toList(), slotsOf(result, 1, "A"))
        assertEquals(35, result.copiesPlaced)
        assertEquals(SheetState((0..4).toSet()), result.newState)
    }

    @Test
    fun eachSheetHasItsOwnBlanks() {
        val result = PackingEngine.pack(sheet, SheetState.FRESH, listOf(order("A", 42)), blanks = mapOf(1 to setOf(0)))
        assertEquals((0..39).toList(), slotsOf(result, 0, "A"))
        assertEquals(listOf(1, 2), slotsOf(result, 1, "A"))
        assertEquals(emptySet(), result.sheets[0].blankSlots)
        assertEquals(setOf(0), result.sheets[1].blankSlots)
    }

    @Test
    fun aBlankOnAnAlreadyPrintedSlotIsIgnored() {
        val state = SheetState(setOf(0, 1, 2))
        val result = PackingEngine.pack(sheet, state, listOf(order("A", 3)), blanks = mapOf(0 to setOf(1, 5)))
        assertEquals(listOf(3, 4, 6), slotsOf(result, 0, "A"))
        assertEquals(setOf(5), result.sheets[0].blankSlots)
    }

    @Test
    fun blanksThatWouldLeaveNoSlotToPrintOnAreIgnored() {
        // Otherwise the part-used sheet would go through the printer with nothing on it.
        val state = SheetState((0..38).toSet())
        val result = PackingEngine.pack(sheet, state, listOf(order("A", 1)), blanks = mapOf(0 to setOf(39)))
        assertEquals(listOf(39), slotsOf(result, 0, "A"))
        assertEquals(emptySet(), result.sheets[0].blankSlots)
        assertEquals(SheetState.FRESH, result.newState)
    }

    @Test
    fun aWhollyBlankSheetCannotStopPacking() {
        val result = PackingEngine.pack(sheet, SheetState.FRESH, listOf(order("A", 3)), blanks = mapOf(0 to (0 until 40).toSet()))
        assertEquals(listOf(0, 1, 2), slotsOf(result, 0, "A"))
    }

    @Test
    fun blanksOutsideTheSheetAreIgnored() {
        val result = PackingEngine.pack(sheet, SheetState.FRESH, listOf(order("A", 2)), blanks = mapOf(0 to setOf(-1, 40)))
        assertEquals(listOf(0, 1), slotsOf(result, 0, "A"))
        assertEquals(emptySet(), result.sheets[0].blankSlots)
    }
}

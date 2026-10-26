package com.tlw.photodokan.core.packing

import com.tlw.photodokan.core.geometry.RectMm
import com.tlw.photodokan.core.model.Order
import com.tlw.photodokan.core.model.SheetState
import com.tlw.photodokan.core.model.SheetTemplate

/** One printed copy placed in one slot. [copy] counts from 1. */
data class Placement(
    val slotIndex: Int,
    val rect: RectMm,
    val orderId: String,
    val copy: Int,
)

/**
 * One physical sheet to print. When [reusesPartialSheet] is true, this is the sheet already in
 * the printer and [previouslyUsedSlots] must be left blank. [blankSlots] are free slots the
 * operator chose to leave blank on this print; they get no ink and stay free afterwards.
 */
data class PackedSheet(
    val placements: List<Placement>,
    val previouslyUsedSlots: Set<Int>,
    val reusesPartialSheet: Boolean,
    val blankSlots: Set<Int> = emptySet(),
)

data class PackResult(
    val sheets: List<PackedSheet>,
    /** State of the last sheet after printing; FRESH when it came out full. */
    val newState: SheetState,
) {
    val copiesPlaced: Int get() = sheets.sumOf { it.placements.size }
}

/**
 * Packs orders into the free slots of a sheet so no photo paper is wasted.
 *
 * Copies fill the lowest free slot first, in reading order, and an order's copies stay
 * consecutive. A 4-copy order therefore leaves its fifth slot for the next order. When a sheet
 * fills up, packing continues on a fresh sheet. Pure and deterministic: no I/O, no clock.
 *
 * [blanks] are slots to leave blank, by sheet: 0 is the first sheet printed (the one in the
 * printer when it is part-used), 1 the next, and so on. Copies skip them. A blank slot gets no
 * ink, so it is still free in [PackResult.newState]. Blanks on a slot already used, or outside
 * the sheet, are ignored; so are all of a sheet's blanks when they would leave it no slot to
 * print on, because that sheet could never be filled.
 */
object PackingEngine {

    fun pack(
        template: SheetTemplate,
        state: SheetState,
        orders: List<Order>,
        blanks: Map<Int, Set<Int>> = emptyMap(),
    ): PackResult {
        val capacity = template.capacity
        require(state.usedSlots.all { it in 0 until capacity }) {
            "Sheet state has slots outside 0 until $capacity: ${state.usedSlots.filter { it !in 0 until capacity }}"
        }
        if (orders.isEmpty()) return PackResult(emptyList(), state)

        val sheets = mutableListOf<PackedSheet>()
        val startState = if (state.isFull(capacity)) SheetState.FRESH else state

        fun blanksFor(sheetIndex: Int, taken: Set<Int>): Set<Int> {
            val wanted = blanks[sheetIndex].orEmpty().filter { it in 0 until capacity && it !in taken }.toSet()
            return if (taken.size + wanted.size >= capacity) emptySet() else wanted
        }

        var sheetIndex = 0
        var previouslyUsed = startState.usedSlots
        var used = startState.usedSlots.toMutableSet()
        var skipped = blanksFor(0, previouslyUsed)
        var placements = mutableListOf<Placement>()
        var reusing = previouslyUsed.isNotEmpty()

        fun closeSheet() {
            sheets += PackedSheet(placements.toList(), previouslyUsed, reusing, skipped)
            sheetIndex++
            previouslyUsed = emptySet()
            used = mutableSetOf()
            skipped = blanksFor(sheetIndex, emptySet())
            placements = mutableListOf()
            reusing = false
        }

        for (order in orders) {
            for (copy in 1..order.copies) {
                // Blanks never overlap used slots, so this counts every slot that can't take a copy.
                if (used.size + skipped.size >= capacity) closeSheet()
                val slot = (0 until capacity).first { it !in used && it !in skipped }
                used += slot
                placements += Placement(slot, template.slotRect(slot), order.id, copy)
            }
        }

        val endState = if (used.size >= capacity) SheetState.FRESH else SheetState(used.toSet())
        closeSheet()
        return PackResult(sheets, endState)
    }
}

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
 * the printer and [previouslyUsedSlots] must be left blank.
 */
data class PackedSheet(
    val placements: List<Placement>,
    val previouslyUsedSlots: Set<Int>,
    val reusesPartialSheet: Boolean,
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
 */
object PackingEngine {

    fun pack(template: SheetTemplate, state: SheetState, orders: List<Order>): PackResult {
        val capacity = template.capacity
        require(state.usedSlots.all { it in 0 until capacity }) {
            "Sheet state has slots outside 0 until $capacity: ${state.usedSlots.filter { it !in 0 until capacity }}"
        }
        if (orders.isEmpty()) return PackResult(emptyList(), state)

        val sheets = mutableListOf<PackedSheet>()
        val startState = if (state.isFull(capacity)) SheetState.FRESH else state

        var previouslyUsed = startState.usedSlots
        var used = startState.usedSlots.toMutableSet()
        var placements = mutableListOf<Placement>()
        var reusing = previouslyUsed.isNotEmpty()

        fun closeSheet() {
            sheets += PackedSheet(placements.toList(), previouslyUsed, reusing)
            previouslyUsed = emptySet()
            used = mutableSetOf()
            placements = mutableListOf()
            reusing = false
        }

        for (order in orders) {
            for (copy in 1..order.copies) {
                if (used.size >= capacity) closeSheet()
                val slot = (0 until capacity).first { it !in used }
                used += slot
                placements += Placement(slot, template.slotRect(slot), order.id, copy)
            }
        }

        val endState = if (used.size >= capacity) SheetState.FRESH else SheetState(used.toSet())
        closeSheet()
        return PackResult(sheets, endState)
    }
}

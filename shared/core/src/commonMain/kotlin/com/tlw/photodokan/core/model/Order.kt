package com.tlw.photodokan.core.model

import kotlinx.serialization.Serializable

/** One customer's request: a single edited photo printed [copies] times. Usually 5 or 10. */
@Serializable
data class Order(
    val id: String,
    val customerLabel: String,
    val copies: Int,
) {
    init {
        require(id.isNotBlank()) { "Order id must not be blank" }
        require(copies in 1..MAX_COPIES) { "Copies must be between 1 and $MAX_COPIES, was $copies" }
    }

    companion object {
        const val MAX_COPIES = 200
    }
}

/** Which slots on the sheet currently in the printer are already printed. */
@Serializable
data class SheetState(val usedSlots: Set<Int> = emptySet()) {
    fun freeSlots(capacity: Int): List<Int> = (0 until capacity).filterNot { it in usedSlots }

    fun isFull(capacity: Int): Boolean = usedSlots.size >= capacity

    companion object {
        val FRESH = SheetState()
    }
}

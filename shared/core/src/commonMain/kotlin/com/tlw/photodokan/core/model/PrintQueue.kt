package com.tlw.photodokan.core.model

import com.tlw.photodokan.core.library.Customer
import com.tlw.photodokan.core.packing.PackResult
import com.tlw.photodokan.core.packing.PackingEngine
import kotlinx.serialization.Serializable

/** A saved customer waiting to be printed [copies] times. */
@Serializable
data class QueuedOrder(val customer: Customer, val copies: Int) {
    init {
        require(copies in 1..Order.MAX_COPIES) { "Copies must be between 1 and ${Order.MAX_COPIES}, was $copies" }
    }

    /** Unique across days: customer numbers restart every day. */
    val id: String get() = with(customer.date) { "$year-${month.pad2()}-${day.pad2()}/${customer.label}" }

    fun toOrder(): Order = Order(id, customer.label, copies)

    private fun Int.pad2() = toString().padStart(2, '0')
}

/**
 * What is waiting to print, in the order it was added, and which slots of the sheet in the
 * printer are already used. Saved between runs, so a part-used sheet is remembered overnight.
 */
@Serializable
data class PrintQueue(
    val orders: List<QueuedOrder> = emptyList(),
    val sheet: SheetState = SheetState.FRESH,
    /**
     * Slots the operator chose to leave blank on the next print, by sheet: 0 is the first sheet
     * printed, 1 the next, and so on (see [PackingEngine.pack]). Cleared once that print is done
     * or a new sheet goes in. Queues saved before blanks existed load with none.
     */
    val blanks: Map<Int, Set<Int>> = emptyMap(),
) {
    val copies: Int get() = orders.sumOf { it.copies }

    fun orderFor(customer: Customer): QueuedOrder? = orders.firstOrNull { it.customer.sameAs(customer) }

    /** Adds the customer at the end, or changes their copies where they already are in the queue. */
    fun add(customer: Customer, copies: Int): PrintQueue {
        val order = QueuedOrder(customer, copies)
        val index = orders.indexOfFirst { it.customer.sameAs(customer) }
        return copy(orders = if (index < 0) orders + order else orders.toMutableList().also { it[index] = order })
    }

    fun remove(customer: Customer): PrintQueue = copy(orders = orders.filterNot { it.customer.sameAs(customer) })

    /** Where every copy goes, starting from the free slots of the sheet in the printer. */
    fun pack(template: SheetTemplate): PackResult = PackingEngine.pack(template, sheet, orders.map { it.toOrder() }, blanks)

    /**
     * Leaves [slot] of sheet [sheetIndex] blank, or fills it again when it already is. Refused,
     * returning this queue unchanged, for a slot already printed on the sheet in the printer, and
     * for the last slot a sheet could still print on.
     */
    fun toggleBlank(template: SheetTemplate, sheetIndex: Int, slot: Int): PrintQueue {
        require(sheetIndex >= 0) { "Sheet index must not be negative, was $sheetIndex" }
        require(slot in 0 until template.capacity) { "Slot must be in 0 until ${template.capacity}, was $slot" }
        val taken = if (sheetIndex == 0) slotsAlreadyUsed(template) else emptySet()
        val current = blanks[sheetIndex].orEmpty() - taken
        val next = when {
            slot in current -> current - slot
            slot in taken || taken.size + current.size + 1 >= template.capacity -> return this
            else -> current + slot
        }
        return copy(blanks = if (next.isEmpty()) blanks - sheetIndex else blanks + (sheetIndex to next))
    }

    /** The slots of the first sheet that already have a photo: none when the sheet in the printer is full, as printing starts a fresh one. */
    private fun slotsAlreadyUsed(template: SheetTemplate): Set<Int> =
        if (sheet.isFull(template.capacity)) emptySet() else sheet.usedSlots

    /** After printing: the queue is empty and the sheet in the printer has the printed slots used. */
    fun printed(template: SheetTemplate): PrintQueue = afterPrinting(this, template)

    /**
     * This queue once [job], an earlier copy of it, has printed: the sheet is as [job] left it, and
     * only [job]'s orders leave the queue. Orders added or changed while it printed stay queued.
     * The blanks were for that print, so none are kept; the blank slots are free again.
     */
    fun afterPrinting(job: PrintQueue, template: SheetTemplate): PrintQueue =
        PrintQueue(orders = orders.filterNot { it in job.orders }, sheet = job.pack(template).newState)

    /** A blank sheet went into the printer. The queue stays as it is; blanks chosen for the old sheet go. */
    fun newSheet(): PrintQueue = copy(sheet = SheetState.FRESH, blanks = emptyMap())

    // Name and phone can change after queueing; a customer is the same by date and number.
    private fun Customer.sameAs(other: Customer) = date == other.date && number == other.number
}

/** Keeps the [PrintQueue] between runs. Implementations do the I/O. */
interface PrintQueueStore {
    /** The saved queue, or an empty one with a fresh sheet when nothing readable is saved. */
    fun load(): PrintQueue

    fun save(queue: PrintQueue)
}

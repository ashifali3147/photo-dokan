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
    fun pack(template: SheetTemplate): PackResult = PackingEngine.pack(template, sheet, orders.map { it.toOrder() })

    /** After printing: the queue is empty and the sheet in the printer has the printed slots used. */
    fun printed(template: SheetTemplate): PrintQueue = afterPrinting(this, template)

    /**
     * This queue once [job], an earlier copy of it, has printed: the sheet is as [job] left it, and
     * only [job]'s orders leave the queue. Orders added or changed while it printed stay queued.
     */
    fun afterPrinting(job: PrintQueue, template: SheetTemplate): PrintQueue =
        PrintQueue(orders = orders.filterNot { it in job.orders }, sheet = job.pack(template).newState)

    /** A blank sheet went into the printer. The queue stays as it is. */
    fun newSheet(): PrintQueue = copy(sheet = SheetState.FRESH)

    // Name and phone can change after queueing; a customer is the same by date and number.
    private fun Customer.sameAs(other: Customer) = date == other.date && number == other.number
}

/** Keeps the [PrintQueue] between runs. Implementations do the I/O. */
interface PrintQueueStore {
    /** The saved queue, or an empty one with a fresh sheet when nothing readable is saved. */
    fun load(): PrintQueue

    fun save(queue: PrintQueue)
}

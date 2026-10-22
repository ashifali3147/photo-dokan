package com.tlw.photodokan.ui.print

import com.tlw.photodokan.core.model.PrintQueue
import com.tlw.photodokan.core.model.PrintQueueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

/**
 * The print queue shared by the Customers and Print screens. Every change is saved straight
 * away, so the queue and the sheet in the printer survive closing the app. The file is a few
 * hundred bytes, so saving on the calling thread is fine.
 */
class PrintOrders(private val store: PrintQueueStore) {
    private val current = MutableStateFlow(store.load())

    val queue: StateFlow<PrintQueue> = current.asStateFlow()

    fun update(change: (PrintQueue) -> PrintQueue) {
        store.save(current.updateAndGet(change))
    }
}

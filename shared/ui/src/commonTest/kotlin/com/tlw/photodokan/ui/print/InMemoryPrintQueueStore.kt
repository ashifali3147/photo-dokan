package com.tlw.photodokan.ui.print

import com.tlw.photodokan.core.model.PrintQueue
import com.tlw.photodokan.core.model.PrintQueueStore

/** Test double for screens: the file store has its own tests. */
class InMemoryPrintQueueStore(var saved: PrintQueue = PrintQueue()) : PrintQueueStore {
    override fun load() = saved

    override fun save(queue: PrintQueue) {
        saved = queue
    }
}

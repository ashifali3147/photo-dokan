package com.tlw.photodokan.ui.print

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tlw.photodokan.core.model.Order
import com.tlw.photodokan.core.model.PrintQueue
import com.tlw.photodokan.core.model.QueuedOrder
import com.tlw.photodokan.core.model.SheetTemplate
import com.tlw.photodokan.core.packing.PackResult
import com.tlw.photodokan.core.packing.PackedSheet
import com.tlw.photodokan.core.library.PhotoLibrary
import com.tlw.photodokan.ui.photo.decodePreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PrintState(
    val queue: PrintQueue,
    /** Where every queued copy goes; more than one sheet when the queue overflows. */
    val pack: PackResult,
    /** Small copies of each order's final.jpg, by order id, for the sheet preview. */
    val photos: Map<String, ImageBitmap> = emptyMap(),
    /** Which of [sheets] the preview shows. */
    val sheetIndex: Int = 0,
    val message: String? = null,
) {
    /**
     * The sheets to preview. With nothing queued it is just the sheet in the printer, so the
     * operator still sees which slots are used.
     */
    val sheets: List<PackedSheet>
        get() = pack.sheets.ifEmpty { listOf(PackedSheet(emptyList(), queue.sheet.usedSlots, queue.sheet.usedSlots.isNotEmpty())) }

    val shownSheet: PackedSheet get() = sheets[sheetIndex.coerceIn(sheets.indices)]
}

/**
 * The Print screen: the queue of customers to print, where their copies land on the sheet in
 * the printer, and keeping track of which slots that sheet has used.
 */
class PrintViewModel(
    private val orders: PrintOrders,
    private val library: PhotoLibrary,
    val template: SheetTemplate,
    private val work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _state = MutableStateFlow(stateFor(orders.queue.value))
    val state: StateFlow<PrintState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            orders.queue.collect { queue ->
                _state.update { it.copy(queue = queue, pack = queue.pack(template), sheetIndex = 0) }
                loadPhotos(reload = false)
            }
        }
    }

    fun setCopies(order: QueuedOrder, copies: Int) =
        orders.update { it.add(order.customer, copies.coerceIn(1, Order.MAX_COPIES)) }

    fun remove(order: QueuedOrder) = orders.update { it.remove(order.customer) }

    fun showSheet(index: Int) = _state.update { it.copy(sheetIndex = index.coerceIn(it.sheets.indices)) }

    /** The sheet has come out of the printer: empty the queue and remember the used slots. */
    fun markPrinted() {
        val before = _state.value
        if (before.queue.orders.isEmpty()) return
        orders.update { it.printed(template) }
        val free = template.capacity - orders.queue.value.sheet.usedSlots.size
        val copies = before.queue.copies
        val photos = if (copies == 1) "1 photo" else "$copies photos"
        val next = if (free == template.capacity) "Put a new sheet in the printer." else "$free slots are left on this sheet."
        _state.update { it.copy(message = "Marked $photos as printed. $next") }
    }

    /** A blank sheet went into the printer. */
    fun newSheet() {
        orders.update { it.newSheet() }
        _state.update { it.copy(message = "New sheet: all ${template.capacity} slots are free.") }
    }

    /** Reads the photos again, e.g. after a customer's crop was changed and saved. */
    fun refreshPhotos() = viewModelScope.launch { loadPhotos(reload = true) }

    private suspend fun loadPhotos(reload: Boolean) {
        val queued = _state.value.queue.orders
        val known = if (reload) emptyMap() else _state.value.photos
        val missing = queued.filter { it.id !in known }
        val loaded = if (missing.isEmpty()) emptyMap() else withContext(work) {
            missing.mapNotNull { order ->
                val image = try {
                    library.readFinal(order.customer)?.let { decodePreview(it, THUMBNAIL_SIDE)?.image }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null // the slot shows its colour without a photo
                }
                image?.let { order.id to it }
            }.toMap()
        }
        val ids = queued.map { it.id }.toSet()
        _state.update { s -> s.copy(photos = (known + loaded).filterKeys { it in ids }) }
    }

    private fun stateFor(queue: PrintQueue) = PrintState(queue, queue.pack(template))

    companion object {
        /** Enough for a slot on screen; printing reads the full-resolution photo. */
        const val THUMBNAIL_SIDE = 240
    }
}

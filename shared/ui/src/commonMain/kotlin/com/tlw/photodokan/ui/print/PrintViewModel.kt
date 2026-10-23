package com.tlw.photodokan.ui.print

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tlw.photodokan.core.geometry.mmToPixels
import com.tlw.photodokan.core.imaging.ColorAdjustment
import com.tlw.photodokan.core.imaging.CropGeometry
import com.tlw.photodokan.core.model.EditRecipe
import com.tlw.photodokan.core.model.Order
import com.tlw.photodokan.core.model.PhotoSizePreset
import com.tlw.photodokan.core.model.PrintQueue
import com.tlw.photodokan.core.model.QueuedOrder
import com.tlw.photodokan.core.model.SheetTemplate
import com.tlw.photodokan.core.packing.PackResult
import com.tlw.photodokan.core.packing.PackedSheet
import com.tlw.photodokan.core.library.PhotoLibrary
import com.tlw.photodokan.print.CalibrationMeasurement
import com.tlw.photodokan.print.CalibrationSheet
import com.tlw.photodokan.print.PhotoSettings
import com.tlw.photodokan.print.PrintCalibration
import com.tlw.photodokan.print.PrintDocument
import com.tlw.photodokan.print.PrintException
import com.tlw.photodokan.print.SheetPrinter
import com.tlw.photodokan.print.sheetPage
import com.tlw.photodokan.ui.editor.renderCrop
import com.tlw.photodokan.ui.photo.decodePreview
import com.tlw.photodokan.ui.platform.encodeJpeg
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class PrintState(
    val queue: PrintQueue,
    /** Where every queued copy goes; more than one sheet when the queue overflows. */
    val pack: PackResult,
    /** Small copies of each order's final.jpg, by order id, for the sheet preview. */
    val photos: Map<String, ImageBitmap> = emptyMap(),
    /** Which of [sheets] the preview shows. */
    val sheetIndex: Int = 0,
    /** The printer jobs go to, as Windows names it; null when unknown or none is set up. */
    val printerName: String? = null,
    /** Whether photo paper and quality are saved for that printer. */
    val photoSettings: PhotoSettings = PhotoSettings.NotSupported,
    /** The printer's settings window is open. */
    val choosingSettings: Boolean = false,
    /** The size correction saved with the photo settings; null when the printer keeps none. */
    val calibration: PrintCalibration? = null,
    /** The Calibrate window is open. */
    val calibrating: Boolean = false,
    /** Why the measurements entered in the Calibrate window were not saved. */
    val calibrationError: String? = null,
    val printing: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
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
 * the printer, printing it, and keeping track of which slots that sheet has used. Without a
 * [printer] (Android for now) sheets are marked as printed by hand.
 */
class PrintViewModel(
    private val orders: PrintOrders,
    private val library: PhotoLibrary,
    val template: SheetTemplate,
    private val printer: SheetPrinter? = null,
    private val work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    val hasPrinter: Boolean get() = printer != null

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

    /**
     * Prints the queue: each photo rendered from the full-resolution original with its saved edit,
     * at the printer's resolution, one page per sheet. Once the printer has the job, the printed
     * orders leave the queue and their slots count as used. On failure nothing changes.
     */
    fun print() {
        val printer = printer ?: return
        val job = _state.value.queue
        if (job.orders.isEmpty() || _state.value.printing || _state.value.choosingSettings) return
        viewModelScope.launch {
            _state.update { it.copy(printing = true, message = null) }
            try {
                withContext(work) {
                    val dpi = printer.photoDpi()
                    val photos = job.orders.associate { it.id to renderForPrint(it, dpi) }
                    val pages = job.pack(template).sheets.map { sheet -> sheetPage(template, sheet) { photos.getValue(it) } }
                    printer.print(PrintDocument(pages), jobName(job))
                }
                orders.update { it.afterPrinting(job, template) }
                _state.update { it.copy(message = "Printed ${photosText(job.copies)}. ${nextSheetText()}", messageIsError = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PrintException) {
                error(e.message ?: "Printing failed.")
            } catch (e: Exception) {
                error("Printing failed: ${e.message ?: e::class.simpleName}")
            } finally {
                _state.update { it.copy(printing = false) }
            }
        }
    }

    /** The sheet came out of a printer some other way: empty the queue and remember the used slots. */
    fun markPrinted() {
        val before = _state.value
        if (before.queue.orders.isEmpty()) return
        orders.update { it.printed(template) }
        _state.update { it.copy(message = "Marked ${photosText(before.queue.copies)} as printed. ${nextSheetText()}", messageIsError = false) }
    }

    /**
     * Opens the printer's own settings window so the operator can pick photo paper and quality
     * once; every later print uses them.
     */
    fun choosePhotoSettings() {
        val printer = printer ?: return
        if (_state.value.printing || _state.value.choosingSettings) return
        viewModelScope.launch {
            _state.update { it.copy(choosingSettings = true, message = null) }
            try {
                val saved = withContext(work) { printer.choosePhotoSettings() }
                val (status, calibration) = withContext(work) { printer.photoSettings() to printer.calibration() }
                _state.update { it.copy(photoSettings = status, calibration = calibration) }
                if (saved) _state.update { it.copy(message = "Photo settings saved. Every print now uses them.", messageIsError = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PrintException) {
                error(e.message ?: "Could not open the printer settings.")
            } catch (e: Exception) {
                error("Could not open the printer settings: ${e.message ?: e::class.simpleName}")
            } finally {
                _state.update { it.copy(choosingSettings = false) }
            }
        }
    }

    fun openCalibration() {
        if (_state.value.calibration == null) return
        _state.update { it.copy(calibrating = true, calibrationError = null, message = null) }
    }

    fun closeCalibration() = _state.update { it.copy(calibrating = false, calibrationError = null) }

    /** Prints the calibration sheet with the photo settings and the current correction. */
    fun printCalibrationSheet() {
        val printer = printer ?: return
        if (_state.value.printing || _state.value.choosingSettings) return
        viewModelScope.launch {
            _state.update { it.copy(printing = true, calibrationError = null, message = null) }
            try {
                withContext(work) { printer.print(PrintDocument(listOf(CalibrationSheet.page())), "Photo Dokan calibration sheet") }
                _state.update { it.copy(message = "Calibration sheet printed. Measure it and enter the numbers.", messageIsError = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val text = (e as? PrintException)?.message ?: "Printing failed: ${e.message ?: e::class.simpleName}"
                _state.update { it.copy(calibrationError = text) }
            } finally {
                _state.update { it.copy(printing = false) }
            }
        }
    }

    /**
     * Works out a new correction from what the operator measured, in mm as typed, on a
     * calibration sheet printed with the current correction, and saves it. Implausible numbers
     * are refused with the reason, and the Calibrate window stays open to fix them.
     */
    fun saveCalibration(squareWidth: String, squareHeight: String, leftGap: String, topGap: String) {
        val printer = printer ?: return
        val current = _state.value.calibration ?: return
        val numbers = listOf(squareWidth, squareHeight, leftGap, topGap).map(CalibrationMeasurement::parseMm)
        if (numbers.any { it == null }) return calibrationError("Enter all four measurements in mm, for example 100.5.")
        val (w, h, left, top) = numbers.map { it!! }
        val measured = CalibrationMeasurement(w, h, left, top)
        measured.problem()?.let { return calibrationError(it) }
        val corrected = runCatching { current.corrected(measured) }.getOrNull()
            ?: return calibrationError("That correction is larger than any printer needs. Print the calibration sheet again and measure once more.")
        storeCalibration(corrected, "Size correction saved: ${corrected.describe()}. Print the calibration sheet again to check it.")
    }

    /** Goes back to printing without any correction. */
    fun removeCalibration() = storeCalibration(PrintCalibration.NONE, "Size correction removed.")

    private fun storeCalibration(calibration: PrintCalibration, done: String) {
        val printer = printer ?: return
        viewModelScope.launch {
            try {
                withContext(work) { printer.saveCalibration(calibration) }
                _state.update {
                    it.copy(calibration = calibration, calibrating = false, calibrationError = null, message = done, messageIsError = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                calibrationError((e as? PrintException)?.message ?: "Could not save the correction: ${e.message ?: e::class.simpleName}")
            }
        }
    }

    private fun calibrationError(text: String) = _state.update { it.copy(calibrationError = text) }

    /** A blank sheet went into the printer. */
    fun newSheet() {
        orders.update { it.newSheet() }
        _state.update { it.copy(message = "New sheet: all ${template.capacity} slots are free.", messageIsError = false) }
    }

    /**
     * Reads the photos, the printer name and its photo settings again, e.g. after a customer's
     * crop was saved or the default printer changed.
     */
    fun refresh() = viewModelScope.launch {
        loadPhotos(reload = true)
        val printer = printer ?: return@launch
        val name = withContext(work) { runCatching { printer.printerName() }.getOrNull() }
        val settings = withContext(work) { runCatching { printer.photoSettings() }.getOrDefault(PhotoSettings.NotSaved) }
        val calibration = withContext(work) { runCatching { printer.calibration() }.getOrNull() }
        _state.update { it.copy(printerName = name, photoSettings = settings, calibration = calibration) }
    }

    private fun renderForPrint(order: QueuedOrder, dpi: Double): ByteArray {
        val customer = order.customer
        val photo = decodePreview(library.readOriginal(customer), maxSide = Int.MAX_VALUE)
            ?: throw PrintException("The photo of customer ${customer.label} could not be opened.")
        val recipe = library.readRecipe(customer) ?: EditRecipe(presetId = PhotoSizePreset.STUDIO_SLOT.id)
        val slot = template.photo
        val image = photo.image
        val rect = CropGeometry.rect(image.width, image.height, slot.width / slot.height, recipe.crop)
        val width = mmToPixels(slot.width, dpi).roundToInt()
        val height = mmToPixels(slot.height, dpi).roundToInt()
        return encodeJpeg(renderCrop(image, rect, width, height, ColorAdjustment.matrix(recipe)), quality = PRINT_JPEG_QUALITY)
    }

    private fun nextSheetText(): String {
        val free = template.capacity - orders.queue.value.sheet.usedSlots.size
        return if (free == template.capacity) "Put a new sheet in the printer." else "$free slots are left on this sheet."
    }

    private fun error(text: String) = _state.update { it.copy(message = text, messageIsError = true) }

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
        const val PRINT_JPEG_QUALITY = 95

        private fun photosText(n: Int) = if (n == 1) "1 photo" else "$n photos"

        /** Shown in the Windows print queue, e.g. "Photo Dokan 0001, 0002". */
        internal fun jobName(queue: PrintQueue): String {
            val labels = queue.orders.map { it.customer.label }
            val shown = labels.take(5).joinToString(", ")
            return "Photo Dokan " + if (labels.size > 5) "$shown …" else shown
        }
    }
}

package com.tlw.photodokan.ui.print

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tlw.photodokan.core.geometry.mmToPixels
import com.tlw.photodokan.core.imaging.ColorAdjustment
import com.tlw.photodokan.core.imaging.CropGeometry
import com.tlw.photodokan.core.matte.Matte
import com.tlw.photodokan.core.model.BackgroundMode
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
import com.tlw.photodokan.print.PdfRenderer
import com.tlw.photodokan.print.PdfSheetReader
import com.tlw.photodokan.print.PrintDocument
import com.tlw.photodokan.print.PrintException
import com.tlw.photodokan.print.PrintTone
import com.tlw.photodokan.print.SheetPrinter
import com.tlw.photodokan.print.SimplePdfRenderer
import com.tlw.photodokan.print.ToneStrip
import com.tlw.photodokan.print.sheetPage
import com.tlw.photodokan.station.StationConnector
import com.tlw.photodokan.station.StationException
import com.tlw.photodokan.station.StationLinkStore
import com.tlw.photodokan.ui.editor.renderCrop
import com.tlw.photodokan.ui.editor.backdrop
import com.tlw.photodokan.ui.editor.frameLine
import com.tlw.photodokan.ui.editor.painted
import com.tlw.photodokan.ui.editor.painting
import com.tlw.photodokan.ui.photo.decodePreview
import com.tlw.photodokan.ui.photo.photoSize
import com.tlw.photodokan.ui.platform.encodeJpeg
import com.tlw.photodokan.ui.platform.imageBitmapOf
import com.tlw.photodokan.ui.platform.timed
import com.tlw.photodokan.vision.MattePng
import com.tlw.photodokan.vision.PortraitMatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    /** A sheet PDF from the phone, read and waiting for the operator to confirm printing it. */
    val pendingPdf: PendingPdf? = null,
    /** The Calibrate window is open. */
    val calibrating: Boolean = false,
    /** Why the measurements entered in the Calibrate window were not saved. */
    val calibrationError: String? = null,
    /** How much brighter and more colourful photos print than on screen; null when the printer keeps no tone. */
    val tone: PrintTone? = null,
    /** The Match colours window is open. */
    val matchingTone: Boolean = false,
    /** Why the patch number entered in the Match colours window was not saved. */
    val toneError: String? = null,
    val printing: Boolean = false,
    /** The PC this phone is paired with, by name; null when it isn't paired with one. */
    val stationName: String? = null,
    /** The PC a sheet is on its way to, while it is sent and printed there. */
    val sendingTo: String? = null,
    /** A sheet that couldn't be sent to the PC, kept so it can be shared instead without making it again. */
    val unsent: UnsentSheet? = null,
    val message: String? = null,
    val messageIsError: Boolean = false,
) {
    /**
     * The sheets to preview. With nothing queued it is just the sheet in the printer, so the
     * operator still sees which slots are used.
     */
    val sheets: List<PackedSheet>
        get() = pack.sheets.ifEmpty {
            val used = queue.sheet.usedSlots
            listOf(PackedSheet(emptyList(), used, used.isNotEmpty(), queue.blanks[0].orEmpty() - used))
        }

    val shownSheet: PackedSheet get() = sheets[sheetIndex.coerceIn(sheets.indices)]
}

/** A sheet PDF the PC didn't print: why ([reason]), and the PDF itself to share instead. */
class UnsentSheet(val reason: String, val pdf: ByteArray, val fileName: String)

/** A sheet PDF from the phone: its file [name] and the pages read from it. */
class PendingPdf(val name: String, val document: PrintDocument) {
    val pages: Int get() = document.pages.size
}

/**
 * The Print screen: the queue of customers to print, where their copies land on the sheet in
 * the printer, printing it, and keeping track of which slots that sheet has used. Without a
 * [printer] (the phone) the sheet is shared as a PDF through [sharer] and marked as printed by
 * hand once the PC has printed it, or printed through the phone's print dialog with [phonePrinter].
 * A phone paired with the PC's Print Station ([station] and [links]) sends the sheet straight to it.
 * Each photo's background is changed as its recipe says, with the customer's kept matte or one
 * worked out by [matter].
 */
class PrintViewModel(
    private val orders: PrintOrders,
    private val library: PhotoLibrary,
    val template: SheetTemplate,
    private val printer: SheetPrinter? = null,
    private val sharer: SheetSharer? = null,
    private val pdf: PdfRenderer = SimplePdfRenderer(),
    private val pdfReader: PdfSheetReader? = null,
    private val phonePrinter: PhonePrinter? = null,
    private val station: StationConnector? = null,
    private val links: StationLinkStore? = null,
    private val matter: PortraitMatter? = null,
    private val work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    val hasPrinter: Boolean get() = printer != null

    val canShare: Boolean get() = sharer != null

    /** The phone prints straight to a Wi-Fi printer through its print dialog. */
    val canPrintOnPhone: Boolean get() = phonePrinter != null

    /** The phone sends sheets to the PC it is paired with; see [PrintState.stationName]. */
    val canSend: Boolean get() = station != null && links != null

    /** The PC prints sheet PDFs shared from the phone. */
    val canPrintPdf: Boolean get() = printer != null && pdfReader != null

    private val _state = MutableStateFlow(stateFor(orders.queue.value))
    val state: StateFlow<PrintState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            orders.queue.collect { queue ->
                val pack = queue.pack(template)
                // Stay on the sheet shown, e.g. while leaving its slots blank, as long as it is still there.
                _state.update { it.copy(queue = queue, pack = pack, sheetIndex = it.sheetIndex.coerceIn(0, maxOf(pack.sheets.size - 1, 0))) }
                loadPhotos(reload = false)
            }
        }
    }

    fun setCopies(order: QueuedOrder, copies: Int) =
        orders.update { it.add(order.customer, copies.coerceIn(1, Order.MAX_COPIES)) }

    fun remove(order: QueuedOrder) = orders.update { it.remove(order.customer) }

    fun showSheet(index: Int) = _state.update { it.copy(sheetIndex = index.coerceIn(it.sheets.indices)) }

    /**
     * Leaves [slot] of sheet [sheetIndex] blank on this print, or fills it again. Not while a
     * sheet is printing, so what prints and what is remembered afterwards stay the same.
     */
    fun toggleBlank(sheetIndex: Int, slot: Int) {
        if (_state.value.printing || _state.value.choosingSettings) return
        val before = orders.queue.value
        orders.update { it.toggleBlank(template, sheetIndex, slot) }
        if (orders.queue.value == before) {
            error(if (sheetIndex == 0 && slot in before.sheet.usedSlots) "That slot is already printed." else "Keep at least one slot free to print on.")
        } else if (_state.value.messageIsError) {
            _state.update { it.copy(message = null, messageIsError = false) }
        }
    }

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
                    val photos = timed("Render ${job.orders.size} customers at ${dpi.roundToInt()} dpi") { renderAll(job, dpi) }
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

    /**
     * Makes the queue's sheet PDF, photos rendered from the originals at [SHARE_DPI] and laid out
     * on this device's sheet, and hands it to [sharer]. Nothing counts as printed yet: the queue
     * stays until the operator marks it printed, once the PC has printed the PDF.
     */
    fun sharePdf() {
        val sharer = sharer ?: return
        withSheet { job, document ->
            sharer.share(withContext(work) { timed("Make shared PDF") { pdf.render(document) } }, pdfName(job))
            shareInstructions(job)
        }
    }

    /**
     * Sends the queue's sheet PDF to the paired PC, which prints it as soon as it arrives. Once the
     * PC says it printed, the queue empties and its slots count as used, as when the PC prints its
     * own queue. Otherwise nothing changes, and the PDF is kept in [PrintState.unsent] so the
     * operator can share it instead.
     */
    fun sendToPc() {
        val station = station ?: return
        val links = links ?: return
        withSheet { job, document ->
            val bytes = withContext(work) { timed("Make sent PDF") { pdf.render(document) } }
            val fileName = pdfName(job)
            val link = withContext(work) { links.load() }
            if (link == null) {
                _state.update { it.copy(stationName = null) }
                notSent("This phone isn't paired with a PC any more. Pair it again in Settings.", bytes, fileName)
            }
            _state.update { it.copy(sendingTo = link.stationName) }
            val result = try {
                withContext(work) { station.send(link, bytes, fileName.removeSuffix(".pdf")) }
            } catch (e: StationException) {
                if (e.reason == StationException.Reason.NotPaired) {
                    // The PC removed this phone: pairing again is the only way to send to it.
                    withContext(work) { links.save(null) }
                    _state.update { it.copy(stationName = null) }
                }
                return@withSheet notSent(e.message ?: "The PC refused the sheet.", bytes, fileName)
            } finally {
                _state.update { it.copy(sendingTo = null) }
            }
            if (!result.printed) return@withSheet notSent(result.message, bytes, fileName)
            orders.update { it.afterPrinting(job, template) }
            "Printed ${photosText(job.copies)} on ${link.stationName}. ${nextSheetText()}"
        }
    }

    /** Shares the sheet that couldn't be sent, as made for sending. */
    fun shareUnsent() {
        val sharer = sharer ?: return
        val unsent = _state.value.unsent ?: return
        _state.update { it.copy(unsent = null) }
        try {
            sharer.share(unsent.pdf, unsent.fileName)
            _state.update { it.copy(message = shareInstructions(_state.value.queue), messageIsError = false) }
        } catch (e: Exception) {
            error("The PDF could not be shared: ${e.message ?: e::class.simpleName}")
        }
    }

    fun keepUnsent() = _state.update { it.copy(unsent = null) }

    private fun notSent(reason: String, pdf: ByteArray, fileName: String): Nothing =
        throw NotSent(UnsentSheet(reason, pdf, fileName))

    /** Ends [withSheet] with the sheet kept for sharing; the reason is the message. */
    private class NotSent(val sheet: UnsentSheet) : Exception(sheet.reason)

    /**
     * Opens the phone's print dialog with the same sheet. As with sharing, nothing counts as
     * printed until the operator marks it, once the sheet has come out right.
     */
    fun printOnPhone() {
        val phonePrinter = phonePrinter ?: return
        withSheet { job, document ->
            phonePrinter.print(document, pdfName(job).removeSuffix(".pdf"))
            phonePrintInstructions(job)
        }
    }

    /**
     * Lays out the queue's sheets with photos rendered off the main thread, then hands them to
     * [use] on it; [use] returns the message for the operator.
     */
    private fun withSheet(use: suspend (PrintQueue, PrintDocument) -> String) {
        val job = _state.value.queue
        if (job.orders.isEmpty() || _state.value.printing) return
        viewModelScope.launch {
            _state.update { it.copy(printing = true, message = null) }
            try {
                val document = withContext(work) {
                    // A phone can't hold a 50-megapixel photo at full size twice over; this is plenty for 600 dpi.
                    val photos = timed("Render ${job.orders.size} customers at ${SHARE_DPI.toInt()} dpi") {
                        renderAll(job, SHARE_DPI, maxSide = SHARE_DECODE_SIDE)
                    }
                    PrintDocument(job.pack(template).sheets.map { sheet -> sheetPage(template, sheet) { photos.getValue(it) } })
                }
                val message = use(job, document)
                _state.update { it.copy(message = message, messageIsError = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NotSent) {
                _state.update { it.copy(unsent = e.sheet.takeIf { sharer != null }) }
                error(e.sheet.reason)
            } catch (e: PrintException) {
                error(e.message ?: "The PDF could not be made.")
            } catch (e: Exception) {
                error("The PDF could not be made: ${e.message ?: e::class.simpleName}")
            } finally {
                _state.update { it.copy(printing = false) }
            }
        }
    }

    /** Reads a sheet PDF from the phone; printing waits for the operator to confirm. */
    fun openPdf(file: PickedFile) {
        val reader = pdfReader ?: return
        if (_state.value.printing) return
        viewModelScope.launch {
            _state.update { it.copy(message = null) }
            try {
                val document = withContext(work) { reader.read(file.read()) }
                _state.update { it.copy(pendingPdf = PendingPdf(file.name, document)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PrintException) {
                error(e.message ?: "The PDF could not be read.")
            } catch (e: Exception) {
                error("\"${file.name}\" could not be opened: ${e.message ?: e::class.simpleName}")
            }
        }
    }

    fun cancelPdf() = _state.update { it.copy(pendingPdf = null) }

    /**
     * Prints the confirmed PDF from the phone, with this printer's photo settings and correction.
     * The phone keeps track of that sheet, so this PC's queue and sheet don't change.
     */
    fun printPdf() {
        val printer = printer ?: return
        val pending = _state.value.pendingPdf ?: return
        if (_state.value.printing) return
        viewModelScope.launch {
            _state.update { it.copy(pendingPdf = null, printing = true, message = null) }
            try {
                // Shown in the Windows print queue; the phone's file names already start with "Photo Dokan".
                val name = pending.name.removeSuffix(".pdf").let { if (it.startsWith("Photo Dokan")) it else "Photo Dokan $it" }
                withContext(work) { printer.print(pending.document, name) }
                _state.update { it.copy(message = "Printed \"${pending.name}\". Press Mark as printed on the phone.", messageIsError = false) }
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

    /** What to do with a shared PDF: which sheet the PC prints it on, then mark it printed here. */
    private fun shareInstructions(job: PrintQueue): String =
        "PDF ready. Send it to the PC and print it there on ${sheetText(job)}. Then press Mark as printed."

    private fun phonePrintInstructions(job: PrintQueue): String =
        "Put ${sheetText(job)} in the printer. In the print screen choose A4 paper, and photo paper and best quality if it offers them. " +
            "Once the sheet has come out right, press Mark as printed."

    private fun sheetText(job: PrintQueue): String {
        val used = job.sheet.usedSlots.size
        return if (used > 0) "the part-used sheet ($used of ${template.capacity} slots used)" else "a blank photo sheet"
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
                val tone = withContext(work) { printer.tone() }
                _state.update { it.copy(photoSettings = status, calibration = calibration, tone = tone) }
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

    fun openTone() {
        if (_state.value.tone == null) return
        _state.update { it.copy(matchingTone = true, toneError = null, message = null) }
    }

    fun closeTone() = _state.update { it.copy(matchingTone = false, toneError = null) }

    /**
     * Prints the colour strip with the photo settings. Its patches carry their own tones, so the
     * saved tone is left out.
     */
    fun printToneStrip() {
        val printer = printer ?: return
        if (_state.value.printing || _state.value.choosingSettings) return
        viewModelScope.launch {
            _state.update { it.copy(printing = true, toneError = null, message = null) }
            try {
                withContext(work) {
                    val page = ToneStrip.page { argb, width, height -> encodeJpeg(imageBitmapOf(argb, width, height), quality = 95) }
                    printer.print(PrintDocument(listOf(page), applyTone = false), "Photo Dokan colour strip")
                }
                _state.update { it.copy(message = "Colour strip printed. Find the patch that looks like the screen.", messageIsError = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val text = (e as? PrintException)?.message ?: "Printing failed: ${e.message ?: e::class.simpleName}"
                _state.update { it.copy(toneError = text) }
            } finally {
                _state.update { it.copy(printing = false) }
            }
        }
    }

    /** Saves the tone of the colour strip's patch [number], as typed, for every later print. */
    fun saveTone(number: String) {
        val tone = number.trim().toIntOrNull()?.let(ToneStrip::toneOf)
            ?: return _state.update { it.copy(toneError = "Enter the number under the patch, from 1 to ${ToneStrip.PATCHES}.") }
        storeTone(tone, "Print colours saved: ${tone.describe().replaceFirstChar { it.lowercase() }}.")
    }

    /** Goes back to printing photos exactly as they look on screen. */
    fun removeTone() = storeTone(PrintTone.NONE, "Print colours set back to no change.")

    private fun storeTone(tone: PrintTone, done: String) {
        val printer = printer ?: return
        viewModelScope.launch {
            try {
                withContext(work) { printer.saveTone(tone) }
                _state.update { it.copy(tone = tone, matchingTone = false, toneError = null, message = done, messageIsError = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val text = (e as? PrintException)?.message ?: "Could not save the print colours: ${e.message ?: e::class.simpleName}"
                _state.update { it.copy(toneError = text) }
            }
        }
    }

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
        links?.let { links ->
            val name = withContext(work) { runCatching { links.load()?.stationName }.getOrNull() }
            _state.update { it.copy(stationName = name) }
        }
        val printer = printer ?: return@launch
        val name = withContext(work) { runCatching { printer.printerName() }.getOrNull() }
        val settings = withContext(work) { runCatching { printer.photoSettings() }.getOrDefault(PhotoSettings.NotSaved) }
        val calibration = withContext(work) { runCatching { printer.calibration() }.getOrNull() }
        val tone = withContext(work) { runCatching { printer.tone() }.getOrNull() }
        _state.update { it.copy(printerName = name, photoSettings = settings, calibration = calibration, tone = tone) }
    }

    /**
     * Every order's photo for its slots, by order id. A few at a time, on separate cores: each one
     * is mostly decoding its original, and more at once would only cost memory.
     */
    private suspend fun renderAll(job: PrintQueue, dpi: Double, maxSide: Int = Int.MAX_VALUE): Map<String, ByteArray> = coroutineScope {
        val gate = Semaphore(RENDER_AT_ONCE)
        job.orders.map { order -> async { order.id to gate.withPermit { renderForPrint(order, dpi, maxSide) } } }.awaitAll().toMap()
    }

    /**
     * The order's photo for one slot at [dpi]: its crop of the original, decoded only as large as
     * the crop needs to fill the slot (and at most [maxSide]), so a 50 MP photo prints quickly.
     */
    private fun renderForPrint(order: QueuedOrder, dpi: Double, maxSide: Int = Int.MAX_VALUE): ByteArray {
        val customer = order.customer
        val original = library.readOriginal(customer)
        val unreadable = PrintException("The photo of customer ${customer.label} could not be opened.")
        val recipe = library.readRecipe(customer) ?: EditRecipe(presetId = PhotoSizePreset.STUDIO_SLOT.id)
        val slot = template.photo
        val aspect = slot.width / slot.height
        val width = mmToPixels(slot.width, dpi).roundToInt()
        val height = mmToPixels(slot.height, dpi).roundToInt()
        val (fullWidth, fullHeight) = photoSize(original) ?: throw unreadable
        val needed = CropGeometry.sourceSideFor(fullWidth, fullHeight, aspect, recipe.crop, width)
        val photo = decodePreview(original, maxSide = minOf(needed, maxSide)) ?: throw unreadable
        val image = photo.image.painted(matteFor(order, photo.image, recipe), recipe.painting)
        val rect = CropGeometry.rect(image.width, image.height, aspect, recipe.crop)
        return encodeJpeg(
            renderCrop(image, rect, width, height, ColorAdjustment.matrix(recipe), backdrop(recipe), frameLine(recipe, slot.width)),
            quality = PRINT_JPEG_QUALITY,
        )
    }

    /**
     * The customer's kept matte, or, when their background needs one and none is kept (e.g. saved
     * before mattes were kept), one worked out from [image] and kept. Null prints the photo as shot,
     * as the editor shows it when the person can't be separated.
     */
    private fun matteFor(order: QueuedOrder, image: ImageBitmap, recipe: EditRecipe): Matte? {
        if (recipe.background.mode == BackgroundMode.KEEP) return null
        val customer = order.customer
        runCatching { library.readMatte(customer)?.let(MattePng::decode) }.getOrNull()?.let { return it }
        val matter = matter ?: return null
        val argb = IntArray(image.width * image.height).also { image.readPixels(it) }
        val matte = runCatching { timed("Separate the person of ${customer.label}") { matter.matte(argb, image.width, image.height) } }.getOrNull() ?: return null
        runCatching { library.saveMatte(customer, MattePng.encode(matte)) }
        return matte
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
        private const val RENDER_AT_ONCE = 3

        /** Shared PDFs are printed on the PC; 600 dpi is more than a photo printer resolves. */
        const val SHARE_DPI = 600.0
        const val SHARE_DECODE_SIDE = 4000

        private fun photosText(n: Int) = if (n == 1) "1 photo" else "$n photos"

        /** The shared file, e.g. "Photo Dokan 0001 0003.pdf", or "Photo Dokan 0001 0002 0003 and 2 more.pdf". */
        internal fun pdfName(queue: PrintQueue): String {
            val labels = queue.orders.map { it.customer.label }
            val more = if (labels.size > 3) " and ${labels.size - 3} more" else ""
            return "Photo Dokan ${labels.take(3).joinToString(" ")}$more.pdf"
        }

        /** Shown in the Windows print queue, e.g. "Photo Dokan 0001, 0002". */
        internal fun jobName(queue: PrintQueue): String {
            val labels = queue.orders.map { it.customer.label }
            val shown = labels.take(5).joinToString(", ")
            return "Photo Dokan " + if (labels.size > 5) "$shown …" else shown
        }
    }
}

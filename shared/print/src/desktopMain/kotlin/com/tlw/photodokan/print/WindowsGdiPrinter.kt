package com.tlw.photodokan.print

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.tlw.photodokan.print.Gdi32.Companion as G
import java.awt.Window
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * Prints through the Windows printer driver with GDI, at actual size: every position comes from
 * millimetres and the resolution and unprintable border the driver reports (see [PrinterPage]),
 * never from "fit to page".
 *
 * Prints to the Windows default printer. Paper type and quality come from the photo settings
 * the operator chose once in the driver's own window and [profiles] keeps, together with the
 * size correction and print tone measured on them; without them the driver's defaults apply. [owner] is the
 * app window that settings window opens over.
 */
class WindowsGdiPrinter(
    private val profiles: FilePrintProfileStore,
    private val owner: () -> Window? = { null },
) : SheetPrinter {

    private val gdi get() = Win32.gdi
    private val spool get() = Win32.spool

    override fun printerName(): String? {
        val size = IntByReference(0)
        Win32.spool.GetDefaultPrinterW(null, size)
        if (size.value <= 0) return null
        val buffer = CharArray(size.value)
        if (!Win32.spool.GetDefaultPrinterW(buffer, size)) return null
        return String(buffer, 0, size.value - 1).takeIf { it.isNotBlank() }
    }

    /** The resolution depends on the quality chosen, so it is read with the photo settings applied. */
    override fun photoDpi(): Double {
        val name = printerName() ?: return FALLBACK_DPI
        // A problem with the saved settings is reported when printing; here the default will do.
        val devMode = runCatching { photoDevMode(name, savedProfile(name)) }.getOrNull()
        val ic = gdi.CreateICW(null, WString(name), null, devMode) ?: return FALLBACK_DPI
        try {
            val dpi = gdi.GetDeviceCaps(ic, G.LOGPIXELSX)
            return if (dpi > 0) minOf(dpi, MAX_PHOTO_DPI).toDouble() else FALLBACK_DPI
        } finally {
            gdi.DeleteDC(ic)
        }
    }

    override fun photoSettings(): PhotoSettings {
        val name = printerName() ?: return PhotoSettings.NotSaved
        return if (savedProfile(name) != null) PhotoSettings.Saved else PhotoSettings.NotSaved
    }

    override fun choosePhotoSettings(): Boolean {
        val name = printerName() ?: throw PrintException(NO_PRINTER)
        val saved = savedProfile(name)
        val window = owner()?.let { runCatching { Native.getWindowPointer(it) }.getOrNull() }
        val chosen = withPrinter(name) { documentProperties(it, name, saved?.devMode, prompt = true, window) } ?: return false
        // The correction and tone were measured on this printer; keep them until they are measured again.
        profiles.save(PrintProfile(name, chosen, saved?.calibration ?: PrintCalibration.NONE, saved?.tone ?: PrintTone.NONE))
        return true
    }

    override fun calibration(): PrintCalibration? = printerName()?.let(::savedProfile)?.calibration

    override fun saveCalibration(calibration: PrintCalibration) {
        val name = printerName() ?: throw PrintException(NO_PRINTER)
        val saved = savedProfile(name) ?: throw PrintException("Save the Photo settings first: the size correction goes with them.")
        profiles.save(PrintProfile(name, saved.devMode, calibration, saved.tone))
    }

    override fun tone(): PrintTone? = printerName()?.let(::savedProfile)?.tone

    override fun saveTone(tone: PrintTone) {
        val name = printerName() ?: throw PrintException(NO_PRINTER)
        val saved = savedProfile(name) ?: throw PrintException("Save the Photo settings first: the print colours go with them.")
        profiles.save(PrintProfile(name, saved.devMode, saved.calibration, tone))
    }

    override fun print(document: PrintDocument, jobName: String) {
        val name = printerName() ?: throw PrintException(NO_PRINTER)
        val profile = savedProfile(name)
        val devMode = photoDevMode(name, profile)
        val hdc = gdi.CreateDCW(null, WString(name), null, devMode) ?: throw PrintException("Could not open the printer \"$name\". Check it is switched on.")
        try {
            val page = pageOf(hdc, profile?.calibration ?: PrintCalibration.NONE)
            val tone = profile?.tone?.takeIf { document.applyTone } ?: PrintTone.NONE
            // Check every page before anything prints: half a job on photo paper is worse than none.
            for (p in document.pages) page.problemWith(p)?.let { throw PrintException(it) }

            if (gdi.StartDocW(hdc, DocInfo(jobName)) <= 0) throw PrintException("The printer \"$name\" did not accept the job.")
            var finished = false
            try {
                for (p in document.pages) {
                    if (gdi.StartPage(hdc) <= 0) throw PrintException("The printer stopped before the page could start.")
                    for (item in p.items) draw(hdc, page, tone, item)
                    if (gdi.EndPage(hdc) <= 0) throw PrintException("The printer stopped while printing the page.")
                }
                if (gdi.EndDoc(hdc) <= 0) throw PrintException("The printer stopped at the end of the job.")
                finished = true
            } finally {
                if (!finished) gdi.AbortDoc(hdc)
            }
        } finally {
            gdi.DeleteDC(hdc)
        }
    }

    /** The profile saved for [name]; another printer's is never used. */
    private fun savedProfile(name: String): PrintProfile? = profiles.load()?.takeIf { it.isFor(name) }

    /**
     * The [saved] photo settings for [name], passed through the driver once more so that settings
     * saved before a driver update still suit it. Null when none are saved for this printer.
     */
    private fun photoDevMode(name: String, saved: PrintProfile?): Memory? {
        saved ?: return null
        val merged = withPrinter(name) { documentProperties(it, name, saved.devMode, prompt = false, window = null) }
            ?: throw PrintException("The saved photo settings no longer suit \"$name\". Choose Photo settings again.")
        return Memory(merged.size.toLong()).apply { write(0, merged, 0, merged.size) }
    }

    /**
     * Asks the driver for its settings with [input] applied, after showing its settings window
     * over [window] when [prompt] is set. Null when the operator cancelled.
     */
    private fun documentProperties(printer: Pointer, name: String, input: ByteArray?, prompt: Boolean, window: Pointer?): ByteArray? {
        val device = WString(name)
        val size = spool.DocumentPropertiesW(window, printer, device, null, null, 0)
        if (size <= 0) throw PrintException("Could not read the settings of \"$name\".")
        val output = Memory(size.toLong()).apply { clear() }
        val inputMemory = input?.let { Memory(it.size.toLong()).apply { write(0, it, 0, it.size) } }
        var mode = Winspool.DM_OUT_BUFFER
        if (inputMemory != null) mode = mode or Winspool.DM_IN_BUFFER
        if (prompt) mode = mode or Winspool.DM_IN_PROMPT
        return when (spool.DocumentPropertiesW(window, printer, device, output, inputMemory, mode)) {
            Winspool.IDOK -> DevMode.trim(output.getByteArray(0, size))
                ?: throw PrintException("The printer \"$name\" returned settings that could not be read.")
            Winspool.IDCANCEL -> null
            else -> throw PrintException("Could not read the settings of \"$name\".")
        }
    }

    private inline fun <T> withPrinter(name: String, block: (Pointer) -> T): T {
        val handle = PointerByReference()
        if (!spool.OpenPrinterW(WString(name), handle, null)) throw PrintException("Could not open the printer \"$name\". Check it is switched on.")
        try {
            return block(handle.value)
        } finally {
            spool.ClosePrinter(handle.value)
        }
    }

    private fun pageOf(hdc: Pointer, calibration: PrintCalibration): PrinterPage {
        fun cap(index: Int) = gdi.GetDeviceCaps(hdc, index)
        return PrinterPage(
            dpiX = cap(G.LOGPIXELSX),
            dpiY = cap(G.LOGPIXELSY),
            paperWidthPx = cap(G.PHYSICALWIDTH),
            paperHeightPx = cap(G.PHYSICALHEIGHT),
            offsetXPx = cap(G.PHYSICALOFFSETX),
            offsetYPx = cap(G.PHYSICALOFFSETY),
            printableWidthPx = cap(G.HORZRES),
            printableHeightPx = cap(G.VERTRES),
            calibration = calibration,
        )
    }

    private fun draw(hdc: Pointer, page: PrinterPage, tone: PrintTone, item: PageItem) = when (item) {
        is PageItem.Image -> drawImage(hdc, page.rect(item.rect), item.jpeg, tone)
        is PageItem.Line -> withPen(hdc, page.width(item.widthMm), item.gray) {
            val l = item.line
            gdi.MoveToEx(hdc, page.x(l.x1), page.y(l.y1), null)
            gdi.LineTo(hdc, page.x(l.x2), page.y(l.y2))
        }
        is PageItem.Box -> withPen(hdc, page.width(item.widthMm), item.gray) {
            val r = page.rect(item.rect)
            gdi.MoveToEx(hdc, r.left, r.top, null)
            gdi.LineTo(hdc, r.right, r.top)
            gdi.LineTo(hdc, r.right, r.bottom)
            gdi.LineTo(hdc, r.left, r.bottom)
            gdi.LineTo(hdc, r.left, r.top)
        }
        is PageItem.Text -> drawText(hdc, page, item)
    }

    /**
     * Stretches the photo into [dest] exactly; it was rendered at the printer's resolution, so this
     * is about 1:1. The printer's [tone] is applied on the way.
     */
    private fun drawImage(hdc: Pointer, dest: DeviceRect, jpeg: ByteArray, tone: PrintTone) {
        val image = ImageIO.read(ByteArrayInputStream(jpeg)) ?: throw PrintException("A photo could not be read for printing.")
        val rgb = if (image.type == BufferedImage.TYPE_INT_RGB) image else {
            BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB).also {
                it.createGraphics().apply { drawImage(image, 0, 0, null); dispose() }
            }
        }
        // TYPE_INT_RGB pixels are 0x00RRGGBB: in memory B, G, R, 0, which is what a 32-bit DIB expects.
        val pixels = (rgb.raster.dataBuffer as DataBufferInt).data
        tone.applyTo(pixels)
        gdi.SetStretchBltMode(hdc, G.HALFTONE)
        gdi.SetBrushOrgEx(hdc, 0, 0, null)
        val lines = gdi.StretchDIBits(
            hdc, dest.left, dest.top, dest.width, dest.height,
            0, 0, rgb.width, rgb.height,
            pixels, BitmapInfoHeader(rgb.width, rgb.height), G.DIB_RGB_COLORS, G.SRCCOPY,
        )
        if (lines == 0 || lines == G.GDI_ERROR) throw PrintException("The printer could not draw a photo.")
    }

    private fun drawText(hdc: Pointer, page: PrinterPage, text: PageItem.Text) {
        // A negative height asks for the character (em) size, the same as a PDF font size.
        val font = gdi.CreateFontW(
            -page.width(text.sizeMm), 0, 0, 0, G.FW_NORMAL, 0, 0, 0,
            G.DEFAULT_CHARSET, 0, 0, 0, 0, WString("Arial"),
        ) ?: return
        val previous = gdi.SelectObject(hdc, font)
        try {
            gdi.SetTextAlign(hdc, G.TA_BASELINE)
            gdi.SetBkMode(hdc, G.TRANSPARENT)
            gdi.SetTextColor(hdc, grayColor(text.gray))
            gdi.TextOutW(hdc, page.x(text.x), page.y(text.baselineY), WString(text.text), text.text.length)
        } finally {
            previous?.let { gdi.SelectObject(hdc, it) }
            gdi.DeleteObject(font)
        }
    }

    private inline fun withPen(hdc: Pointer, width: Int, gray: Double, draw: () -> Unit) {
        val pen = gdi.CreatePen(G.PS_SOLID, width, grayColor(gray)) ?: return
        val previousPen = gdi.SelectObject(hdc, pen)
        val previousBrush = gdi.GetStockObject(G.NULL_BRUSH)?.let { gdi.SelectObject(hdc, it) }
        try {
            draw()
        } finally {
            previousBrush?.let { gdi.SelectObject(hdc, it) }
            previousPen?.let { gdi.SelectObject(hdc, it) }
            gdi.DeleteObject(pen)
        }
    }

    /** A COLORREF (0x00BBGGRR) for [gray] from 0 (black) to 1 (white). */
    private fun grayColor(gray: Double): Int {
        val v = (gray.coerceIn(0.0, 1.0) * 255).roundToInt()
        return v or (v shl 8) or (v shl 16)
    }

    companion object {
        /** Epson photo modes report 360, 720 or more; beyond 720 dpi a passport photo gains nothing. */
        const val MAX_PHOTO_DPI = 720
        const val FALLBACK_DPI = 300.0
        private const val NO_PRINTER = "No printer is set up in Windows. Set the photo printer as the default printer."

        val isAvailable: Boolean get() = System.getProperty("os.name").orEmpty().startsWith("Windows")
    }
}

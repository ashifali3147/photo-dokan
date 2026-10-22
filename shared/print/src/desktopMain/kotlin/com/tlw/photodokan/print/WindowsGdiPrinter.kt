package com.tlw.photodokan.print

import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.tlw.photodokan.print.Gdi32.Companion as G
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
 * Prints to the Windows default printer with its current settings. The saved photo-paper
 * profile (paper type and quality) comes with the printer profile task.
 */
class WindowsGdiPrinter : SheetPrinter {

    private val gdi get() = Win32.gdi

    override fun printerName(): String? {
        val size = IntByReference(0)
        Win32.spool.GetDefaultPrinterW(null, size)
        if (size.value <= 0) return null
        val buffer = CharArray(size.value)
        if (!Win32.spool.GetDefaultPrinterW(buffer, size)) return null
        return String(buffer, 0, size.value - 1).takeIf { it.isNotBlank() }
    }

    override fun photoDpi(): Double {
        val name = printerName() ?: return FALLBACK_DPI
        val ic = gdi.CreateICW(null, WString(name), null, null) ?: return FALLBACK_DPI
        try {
            val dpi = gdi.GetDeviceCaps(ic, G.LOGPIXELSX)
            return if (dpi > 0) minOf(dpi, MAX_PHOTO_DPI).toDouble() else FALLBACK_DPI
        } finally {
            gdi.DeleteDC(ic)
        }
    }

    override fun print(document: PrintDocument, jobName: String) {
        val name = printerName() ?: throw PrintException("No printer is set up in Windows. Set the photo printer as the default printer.")
        val hdc = gdi.CreateDCW(null, WString(name), null, null) ?: throw PrintException("Could not open the printer \"$name\". Check it is switched on.")
        try {
            val page = pageOf(hdc)
            // Check every page before anything prints: half a job on photo paper is worse than none.
            for (p in document.pages) page.problemWith(p)?.let { throw PrintException(it) }

            if (gdi.StartDocW(hdc, DocInfo(jobName)) <= 0) throw PrintException("The printer \"$name\" did not accept the job.")
            var finished = false
            try {
                for (p in document.pages) {
                    if (gdi.StartPage(hdc) <= 0) throw PrintException("The printer stopped before the page could start.")
                    for (item in p.items) draw(hdc, page, item)
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

    private fun pageOf(hdc: Pointer): PrinterPage {
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
        )
    }

    private fun draw(hdc: Pointer, page: PrinterPage, item: PageItem) = when (item) {
        is PageItem.Image -> drawImage(hdc, page.rect(item.rect), item.jpeg)
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

    /** Stretches the photo into [dest] exactly; it was rendered at the printer's resolution, so this is about 1:1. */
    private fun drawImage(hdc: Pointer, dest: DeviceRect, jpeg: ByteArray) {
        val image = ImageIO.read(ByteArrayInputStream(jpeg)) ?: throw PrintException("A photo could not be read for printing.")
        val rgb = if (image.type == BufferedImage.TYPE_INT_RGB) image else {
            BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB).also {
                it.createGraphics().apply { drawImage(image, 0, 0, null); dispose() }
            }
        }
        // TYPE_INT_RGB pixels are 0x00RRGGBB: in memory B, G, R, 0, which is what a 32-bit DIB expects.
        val pixels = (rgb.raster.dataBuffer as DataBufferInt).data
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

        val isAvailable: Boolean get() = System.getProperty("os.name").orEmpty().startsWith("Windows")
    }
}

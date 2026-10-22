package com.tlw.photodokan.print

import com.tlw.photodokan.core.geometry.MM_PER_INCH
import com.tlw.photodokan.core.geometry.RectMm
import com.tlw.photodokan.core.geometry.SizeMm
import com.tlw.photodokan.core.geometry.mmToPixels
import kotlin.math.abs
import kotlin.math.roundToInt

/** A rectangle in printer pixels, measured from the top-left of the printable area. */
data class DeviceRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * The page as a printer driver reports it: its resolution, the whole sheet of paper, and the
 * printable area inside it, all in printer pixels.
 *
 * Drivers draw from the corner of the printable area, not of the paper, so every position is
 * shifted back by the unprintable [offsetXPx] / [offsetYPx]. Without that shift everything
 * would land a few millimetres too far right and down.
 */
data class PrinterPage(
    val dpiX: Int,
    val dpiY: Int,
    val paperWidthPx: Int,
    val paperHeightPx: Int,
    val offsetXPx: Int,
    val offsetYPx: Int,
    val printableWidthPx: Int,
    val printableHeightPx: Int,
) {
    init {
        require(dpiX > 0 && dpiY > 0) { "Resolution must be positive, was $dpiX × $dpiY dpi" }
        require(paperWidthPx > 0 && paperHeightPx > 0) { "Paper size must be positive" }
        require(printableWidthPx > 0 && printableHeightPx > 0) { "Printable area must be positive" }
    }

    val paper: SizeMm get() = SizeMm(paperWidthPx * MM_PER_INCH / dpiX, paperHeightPx * MM_PER_INCH / dpiY)

    /** Paper x in mm to a printer pixel column. Each edge is rounded on its own, so sizes never drift. */
    fun x(mm: Double): Int = mmToPixels(mm, dpiX.toDouble()).roundToInt() - offsetXPx

    fun y(mm: Double): Int = mmToPixels(mm, dpiY.toDouble()).roundToInt() - offsetYPx

    fun rect(r: RectMm): DeviceRect = DeviceRect(x(r.x), y(r.y), x(r.right), y(r.bottom))

    /** A line width in printer pixels, never thinner than one pixel. */
    fun width(mm: Double): Int = maxOf(1, mmToPixels(mm, dpiX.toDouble()).roundToInt())

    private fun isPrintable(r: DeviceRect) = r.left >= 0 && r.top >= 0 && r.right <= printableWidthPx && r.bottom <= printableHeightPx

    /**
     * Why [page] can't be printed at actual size on this page, in words for the operator, or null
     * when it can. The paper must match within [PAPER_TOLERANCE_MM], and photos and boxes must not
     * reach into the unprintable border, where the printer would silently cut them off.
     */
    fun problemWith(page: PrintPage): String? {
        val actual = paper
        if (abs(actual.width - page.paper.width) > PAPER_TOLERANCE_MM || abs(actual.height - page.paper.height) > PAPER_TOLERANCE_MM) {
            return "The printer is set to ${actual.describe()} paper, but this sheet needs ${page.paper.describe()}. " +
                "Choose that paper size, upright (portrait), in the printer settings."
        }
        val cut = page.items.any {
            when (it) {
                is PageItem.Image -> !isPrintable(rect(it.rect))
                is PageItem.Box -> !isPrintable(rect(it.rect))
                else -> false
            }
        }
        if (cut) return "Part of the sheet falls in the printer's unprintable border, so photos would be cut off."
        return null
    }

    companion object {
        const val PAPER_TOLERANCE_MM = 1.5

        private fun SizeMm.describe() = "${width.roundToInt()} × ${height.roundToInt()} mm"
    }
}

/** Prints documents at exact physical size. One implementation per platform. */
interface SheetPrinter {
    /** The printer jobs go to, as the operator knows it, or null when none is set up. */
    fun printerName(): String?

    /** Pixels per inch to render photos at, so the printer draws them without resizing. */
    fun photoDpi(): Double

    /** Prints every page as one job. Throws [PrintException] with a message for the operator. */
    fun print(document: PrintDocument, jobName: String)
}

class PrintException(message: String) : Exception(message)

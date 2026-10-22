package com.tlw.photodokan.print

import com.tlw.photodokan.core.geometry.LineMm
import com.tlw.photodokan.core.geometry.RectMm
import com.tlw.photodokan.core.geometry.SizeMm

/**
 * What to print, in millimetres with the origin at the top-left of the paper.
 * Renderers convert to points or pixels only when they draw.
 */
data class PrintDocument(val pages: List<PrintPage>) {
    init {
        require(pages.isNotEmpty()) { "A print document needs at least one page" }
    }
}

data class PrintPage(val paper: SizeMm, val items: List<PageItem>)

sealed interface PageItem {

    /** A JPEG stretched to fill [rect] exactly; the imaging pipeline crops it to the slot's aspect ratio. */
    class Image(val rect: RectMm, val jpeg: ByteArray) : PageItem {
        init {
            require(jpeg.isNotEmpty()) { "Image data must not be empty" }
        }
    }

    /** A straight line; [gray] runs from 0 (black) to 1 (white). */
    data class Line(val line: LineMm, val widthMm: Double = 0.1, val gray: Double = 0.6) : PageItem {
        init {
            require(widthMm > 0) { "Line width must be positive, was $widthMm" }
            require(gray in 0.0..1.0) { "Gray must be in 0..1, was $gray" }
        }
    }
}

/** Turns a [PrintDocument] into PDF bytes. One implementation per platform. */
interface PdfRenderer {
    fun render(document: PrintDocument): ByteArray
}

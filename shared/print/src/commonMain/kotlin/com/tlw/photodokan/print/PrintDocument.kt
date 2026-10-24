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

    /** An unfilled rectangle. The stroke is centred on [rect]'s edges, so the outline measures exactly [rect]. */
    data class Box(val rect: RectMm, val widthMm: Double = 0.2, val gray: Double = 0.0) : PageItem {
        init {
            require(widthMm > 0) { "Box line width must be positive, was $widthMm" }
            require(gray in 0.0..1.0) { "Gray must be in 0..1, was $gray" }
        }
    }

    /**
     * One line of text in Helvetica, starting at ([x], [baselineY]). Stick to Latin-1 characters
     * (× and ± are fine): the built-in PDF font cannot draw other scripts.
     */
    data class Text(val x: Double, val baselineY: Double, val sizeMm: Double, val text: String, val gray: Double = 0.0) : PageItem {
        init {
            require(sizeMm > 0) { "Text size must be positive, was $sizeMm" }
            require(gray in 0.0..1.0) { "Gray must be in 0..1, was $gray" }
        }
    }
}

/** Turns a [PrintDocument] into PDF bytes. One implementation per platform. */
interface PdfRenderer {
    fun render(document: PrintDocument): ByteArray
}

/**
 * Reads a sheet PDF that Photo Dokan made, on this device or another, back into a [PrintDocument],
 * so it prints exactly like a sheet made here. Throws [PrintException] for any other PDF.
 */
interface PdfSheetReader {
    fun read(pdf: ByteArray): PrintDocument
}

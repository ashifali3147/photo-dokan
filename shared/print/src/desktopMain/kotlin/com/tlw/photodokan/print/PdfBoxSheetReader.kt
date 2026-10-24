package com.tlw.photodokan.print

import com.tlw.photodokan.core.geometry.LineMm
import com.tlw.photodokan.core.geometry.RectMm
import com.tlw.photodokan.core.geometry.SizeMm
import com.tlw.photodokan.core.geometry.pointsToMm
import org.apache.pdfbox.Loader
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceGray
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import org.apache.pdfbox.pdmodel.graphics.image.PDImage
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.util.Matrix
import org.apache.pdfbox.util.Vector
import java.awt.geom.Point2D
import java.io.IOException
import kotlin.math.abs

/**
 * Reads sheet PDFs with PDFBox. It accepts exactly what Photo Dokan's PDF renderers write: JPEG
 * photos (taken back byte for byte, not re-rendered), straight lines and outlines. Anything else,
 * such as text, fills, curves or other kinds of image, can't be reproduced at exact size, so the
 * whole file is refused rather than printed differently.
 */
class PdfBoxSheetReader : PdfSheetReader {

    override fun read(pdf: ByteArray): PrintDocument {
        val document = try {
            Loader.loadPDF(pdf)
        } catch (e: IOException) {
            throw PrintException("This file is not a PDF, or it is damaged.")
        }
        return document.use { doc ->
            if (doc.numberOfPages == 0) throw PrintException("This PDF has no pages.")
            PrintDocument(doc.pages.map { page -> Reader(page).read() })
        }
    }

    /** Collects one page's items in drawing order, in mm from the top-left of the paper. */
    private class Reader(private val page: PDPage) : PDFGraphicsStreamEngine(page) {
        private val box = page.mediaBox
        private val items = mutableListOf<PageItem>()
        private val path = mutableListOf<Any>() // LineMm or RectMm until stroked
        private var current = Point2D.Float()
        private var unsupported: String? = null

        fun read(): PrintPage {
            processPage(page)
            unsupported?.let { throw PrintException("This PDF wasn't made by Photo Dokan ($it), so it can't be printed here at exact size. Open it in a PDF viewer and print it at Actual size instead.") }
            return PrintPage(SizeMm(mm(box.width), mm(box.height)), items)
        }

        private fun mm(points: Float) = pointsToMm(points.toDouble())
        private fun x(points: Float) = mm(points - box.lowerLeftX)
        private fun y(points: Float) = mm(box.upperRightY - points) // PDF counts up from the bottom

        private fun refuse(what: String) {
            if (unsupported == null) unsupported = what
        }

        override fun drawImage(pdImage: PDImage) {
            val m: Matrix = graphicsState.currentTransformationMatrix
            val image = pdImage as? PDImageXObject
            if (image == null || image.cosObject.filters != COSName.DCT_DECODE) return refuse("an image that isn't a JPEG")
            if (abs(m.shearX) > 1e-4 || abs(m.shearY) > 1e-4 || m.scalingFactorX <= 0 || m.scaleY <= 0) return refuse("a turned image")
            val jpeg = image.cosObject.createRawInputStream().use { it.readBytes() }
            if (Jpeg.frame(jpeg) == null) return refuse("an image that can't be read")
            val left = x(m.translateX)
            val top = y(m.translateY + m.scaleY)
            items += PageItem.Image(RectMm(left, top, mm(m.scalingFactorX), mm(m.scaleY)), jpeg)
        }

        override fun moveTo(x: Float, y: Float) {
            current = Point2D.Float(x, y)
        }

        override fun lineTo(x: Float, y: Float) {
            path += LineMm(x(current.x), y(current.y), x(x), y(y))
            current = Point2D.Float(x, y)
        }

        override fun appendRectangle(p0: Point2D, p1: Point2D, p2: Point2D, p3: Point2D) {
            val left = minOf(p0.x, p2.x).toFloat()
            val right = maxOf(p0.x, p2.x).toFloat()
            val bottom = minOf(p0.y, p2.y).toFloat()
            val top = maxOf(p0.y, p2.y).toFloat()
            if (abs(p1.y - p0.y) > 1e-3 && abs(p1.x - p0.x) > 1e-3) return refuse("a turned outline")
            path += RectMm(x(left), y(top), mm(right - left), mm(top - bottom))
        }

        override fun strokePath() {
            val width = mm(graphicsState.lineWidth * graphicsState.currentTransformationMatrix.scalingFactorX)
            val gray = strokeGray() ?: return refuse("coloured lines").also { path.clear() }
            for (part in path) items += when (part) {
                is LineMm -> PageItem.Line(part, width, gray)
                else -> PageItem.Box(part as RectMm, width, gray)
            }
            path.clear()
        }

        /** 0 (black) … 1 (white) for grey or RGB grey strokes; null for real colour. */
        private fun strokeGray(): Double? {
            val color = graphicsState.strokingColor
            val c = color.components
            return when (color.colorSpace) {
                is PDDeviceGray -> c[0].toDouble()
                is PDDeviceRGB -> if (abs(c[0] - c[1]) < 1e-3 && abs(c[1] - c[2]) < 1e-3) c[0].toDouble() else null
                else -> null
            }?.coerceIn(0.0, 1.0)
        }

        override fun endPath() = path.clear()
        override fun closePath() = Unit
        override fun getCurrentPoint(): Point2D = current
        override fun clip(windingRule: Int) = refuse("clipping")
        override fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) = refuse("curves")
        override fun fillPath(windingRule: Int) = refuse("filled shapes")
        override fun fillAndStrokePath(windingRule: Int) = refuse("filled shapes")
        override fun shadingFill(shadingName: COSName) = refuse("shading")
        override fun showGlyph(textRenderingMatrix: Matrix, font: org.apache.pdfbox.pdmodel.font.PDFont, code: Int, displacement: Vector) =
            refuse("text")
    }
}

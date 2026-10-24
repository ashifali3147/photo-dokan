package com.tlw.photodokan.print

import com.tlw.photodokan.core.geometry.RectMm
import com.tlw.photodokan.core.geometry.mmToPoints
import com.tlw.photodokan.core.model.Order
import com.tlw.photodokan.core.model.SheetState
import com.tlw.photodokan.core.model.SheetTemplate
import com.tlw.photodokan.core.packing.PackingEngine
import org.apache.pdfbox.Loader
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.graphics.image.PDImage
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import java.awt.Color
import java.awt.geom.Point2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What every [PdfRenderer] must do, checked by reading its output back with PDFBox: exact paper
 * size, photos exactly on their slots and embedded unchanged, lines, outlines and text in place.
 */
abstract class PdfRendererContract {

    abstract val renderer: PdfRenderer

    protected val template = SheetTemplate.STUDIO_A4_5X8

    private fun close(expected: Double, actual: Double, tolerance: Double = 0.01) =
        assertTrue(abs(expected - actual) < tolerance, "expected $expected pt, was $actual pt")

    @Test
    fun a4PageIsExactlyA4InPoints() {
        val pdf = render(listOf(Order("A", "0001", 1)))
        Loader.loadPDF(pdf).use { doc ->
            val box = doc.getPage(0).mediaBox
            close(595.2756, box.width.toDouble())
            close(841.8898, box.height.toDouble())
        }
    }

    @Test
    fun viewersAreAskedToPrintAtActualSize() {
        Loader.loadPDF(render(listOf(Order("A", "0001", 1)))).use { doc ->
            assertEquals("None", doc.documentCatalog.viewerPreferences?.printScaling)
        }
    }

    @Test
    fun photosLandExactlyOnTheirSlotRects() {
        val orders = listOf(Order("A", "0001", 5), Order("B", "0002", 10), Order("C", "0003", 4))
        val sheet = PackingEngine.pack(template, SheetState.FRESH, orders).sheets.single()
        val drawn = Loader.loadPDF(render(orders)).use { Recorder(it.getPage(0)).also(Recorder::run).images }

        assertEquals(sheet.placements.size, drawn.size)
        val pageHeight = mmToPoints(template.paper.height)
        for ((placement, image) in sheet.placements.zip(drawn)) {
            val r = placement.rect
            close(mmToPoints(r.x), image.x)
            close(pageHeight - mmToPoints(r.bottom), image.y)
            close(mmToPoints(r.width), image.width)
            close(mmToPoints(r.height), image.height)
        }
    }

    @Test
    fun cutLinesMatchTheTemplate() {
        val lines = Loader.loadPDF(render(listOf(Order("A", "0001", 5)))).use { Recorder(it.getPage(0)).also(Recorder::run).lines }
        val pageHeight = mmToPoints(template.paper.height)
        val expected = template.cutLineSegments()

        assertEquals(expected.size, lines.size)
        for ((mm, pt) in expected.zip(lines)) {
            close(mmToPoints(mm.x1), pt.x1)
            close(pageHeight - mmToPoints(mm.y1), pt.y1)
            close(mmToPoints(mm.x2), pt.x2)
            close(pageHeight - mmToPoints(mm.y2), pt.y2)
        }
    }

    @Test
    fun jpegIsEmbeddedWithoutReencoding() {
        val jpeg = testJpeg(Color.BLUE)
        val document = PrintDocument(listOf(PrintPage(template.paper, listOf(PageItem.Image(template.slotRect(0), jpeg)))))
        Loader.loadPDF(renderer.render(document)).use { doc ->
            val resources = doc.getPage(0).resources
            val image = resources.xObjectNames.map(resources::getXObject).filterIsInstance<PDImageXObject>().single()
            val raw = image.cosObject.createRawInputStream().use { it.readBytes() }
            assertEquals(COSName.DCT_DECODE, image.cosObject.filters)
            assertContentEquals(jpeg, raw)
        }
    }

    @Test
    fun boxOutlineMeasuresExactlyItsRect() {
        val square = RectMm(40.0, 50.0, 100.0, 100.0)
        val pdf = renderer.render(PrintDocument(listOf(PrintPage(template.paper, listOf(PageItem.Box(square))))))
        val box = Loader.loadPDF(pdf).use { Recorder(it.getPage(0)).also(Recorder::run).rects }.single()

        close(mmToPoints(40.0), box.x)
        close(mmToPoints(template.paper.height) - mmToPoints(150.0), box.y)
        close(283.4646, box.width)
        close(283.4646, box.height)
    }

    @Test
    fun textAppearsAtItsBaseline() {
        val text = PageItem.Text(x = 20.0, baselineY = 30.0, sizeMm = 4.0, text = "100 × 100 mm ±0.5")
        val pdf = renderer.render(PrintDocument(listOf(PrintPage(template.paper, listOf(text)))))
        val positions = mutableListOf<TextPosition>()
        val stripper = object : PDFTextStripper() {
            override fun writeString(string: String, textPositions: List<TextPosition>) {
                positions += textPositions
                super.writeString(string, textPositions)
            }
        }
        val extracted = Loader.loadPDF(pdf).use(stripper::getText)

        assertEquals(text.text, extracted.trim())
        close(mmToPoints(20.0), positions.first().xDirAdj.toDouble(), 0.05)
        close(mmToPoints(30.0), positions.first().yDirAdj.toDouble(), 0.05)
    }

    @Test
    fun overflowBecomesASecondPage() {
        val orders = listOf(Order("A", "0001", 30), Order("B", "0002", 15))
        Loader.loadPDF(render(orders)).use { assertEquals(2, it.numberOfPages) }
    }

    protected fun render(orders: List<Order>): ByteArray {
        val photos = mapOf("A" to testJpeg(Color.BLUE), "B" to testJpeg(Color.RED), "C" to testJpeg(Color.GREEN))
        val result = PackingEngine.pack(template, SheetState.FRESH, orders)
        return renderer.render(PrintDocument(result.sheets.map { sheetPage(template, it, photos::getValue) }))
    }

    protected fun testJpeg(color: Color): ByteArray {
        val image = BufferedImage(72, 68, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { this.color = color; fillRect(0, 0, 72, 68); dispose() }
        return ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
    }

    protected data class DrawnImage(val x: Double, val y: Double, val width: Double, val height: Double)
    protected data class DrawnLine(val x1: Double, val y1: Double, val x2: Double, val y2: Double)
    protected data class DrawnRect(val x: Double, val y: Double, val width: Double, val height: Double)

    /** Records where images and line segments end up on the page, in PDF points. */
    protected class Recorder(page: PDPage) : PDFGraphicsStreamEngine(page) {
        private val page = page
        val images = mutableListOf<DrawnImage>()
        val lines = mutableListOf<DrawnLine>()
        val rects = mutableListOf<DrawnRect>()
        private var current = Point2D.Float()

        fun run() = processPage(page)

        override fun drawImage(pdImage: PDImage) {
            val m = graphicsState.currentTransformationMatrix
            images += DrawnImage(m.translateX.toDouble(), m.translateY.toDouble(), m.scalingFactorX.toDouble(), m.scalingFactorY.toDouble())
        }

        override fun moveTo(x: Float, y: Float) { current = Point2D.Float(x, y) }
        override fun lineTo(x: Float, y: Float) {
            lines += DrawnLine(current.x.toDouble(), current.y.toDouble(), x.toDouble(), y.toDouble())
            current = Point2D.Float(x, y)
        }
        override fun getCurrentPoint(): Point2D = current
        override fun appendRectangle(p0: Point2D, p1: Point2D, p2: Point2D, p3: Point2D) {
            rects += DrawnRect(p0.x, p0.y, p2.x - p0.x, p2.y - p0.y)
        }
        override fun clip(windingRule: Int) = Unit
        override fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) = Unit
        override fun closePath() = Unit
        override fun endPath() = Unit
        override fun strokePath() = Unit
        override fun fillPath(windingRule: Int) = Unit
        override fun fillAndStrokePath(windingRule: Int) = Unit
        override fun shadingFill(shadingName: COSName) = Unit
    }
}

class PdfBoxRendererTest : PdfRendererContract() {
    override val renderer = PdfBoxRenderer()
}

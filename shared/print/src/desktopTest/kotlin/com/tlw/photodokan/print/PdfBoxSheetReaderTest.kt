package com.tlw.photodokan.print

import com.tlw.photodokan.core.geometry.PaperSizes
import com.tlw.photodokan.core.geometry.RectMm
import com.tlw.photodokan.core.model.Order
import com.tlw.photodokan.core.model.SheetState
import com.tlw.photodokan.core.model.SheetTemplate
import com.tlw.photodokan.core.packing.PackingEngine
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PdfBoxSheetReaderTest {

    private val template = SheetTemplate.STUDIO_A4_5X8
    private val reader = PdfBoxSheetReader()

    private fun jpeg(color: Color): ByteArray {
        val image = BufferedImage(72, 68, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { this.color = color; fillRect(0, 0, 72, 68); dispose() }
        return ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
    }

    /** A fresh sheet with cut lines, and an overflow page, as the phone shares them. */
    private fun sheets(): PrintDocument {
        val photos = mapOf("A" to jpeg(Color.BLUE), "B" to jpeg(Color.RED))
        val packed = PackingEngine.pack(template, SheetState((0..6).toSet()), listOf(Order("A", "0001", 30), Order("B", "0002", 15)))
        val fresh = PackingEngine.pack(template, SheetState.FRESH, listOf(Order("A", "0001", 5))).sheets
        return PrintDocument((fresh + packed.sheets).map { sheetPage(template, it, photos::getValue) })
    }

    private fun near(a: Double, b: Double) = abs(a - b) < 0.001

    private fun assertSameRect(expected: RectMm, actual: RectMm) =
        assertTrue(near(expected.x, actual.x) && near(expected.y, actual.y) && near(expected.width, actual.width) && near(expected.height, actual.height), "expected $expected, was $actual")

    /** Every item comes back in order, in place to a thousandth of a millimetre, photos byte for byte. */
    private fun assertRoundTrip(renderer: PdfRenderer) {
        val original = sheets()
        val read = reader.read(renderer.render(original))
        assertEquals(original.pages.size, read.pages.size)
        for ((page, back) in original.pages.zip(read.pages)) {
            assertTrue(near(page.paper.width, back.paper.width) && near(page.paper.height, back.paper.height), "${back.paper}")
            assertEquals(page.items.size, back.items.size)
            for ((item, got) in page.items.zip(back.items)) when (item) {
                is PageItem.Image -> {
                    got as PageItem.Image
                    assertSameRect(item.rect, got.rect)
                    assertContentEquals(item.jpeg, got.jpeg)
                }
                is PageItem.Line -> {
                    got as PageItem.Line
                    val (a, b) = item.line to got.line
                    assertTrue(near(a.x1, b.x1) && near(a.y1, b.y1) && near(a.x2, b.x2) && near(a.y2, b.y2), "expected $a, was $b")
                    assertTrue(near(item.widthMm, got.widthMm) && near(item.gray, got.gray), "line style $got")
                }
                else -> error("unexpected $item")
            }
        }
    }

    @Test
    fun aPdfFromThePhoneComesBackExactly() = assertRoundTrip(SimplePdfRenderer())

    @Test
    fun aPdfFromThePcComesBackExactly() = assertRoundTrip(PdfBoxRenderer())

    @Test
    fun outlinesComeBackAsBoxes() {
        val box = PageItem.Box(RectMm(40.0, 50.0, 100.0, 100.0), widthMm = 0.2, gray = 0.0)
        val read = reader.read(SimplePdfRenderer().render(PrintDocument(listOf(PrintPage(PaperSizes.A4, listOf(box))))))
        val back = read.pages.single().items.single() as PageItem.Box
        assertSameRect(box.rect, back.rect)
        assertTrue(near(0.2, back.widthMm))
    }

    @Test
    fun somethingThatIsNotAPdfIsRefused() {
        val e = assertFailsWith<PrintException> { reader.read(jpeg(Color.BLUE)) }
        assertEquals("This file is not a PDF, or it is damaged.", e.message)
    }

    @Test
    fun aPdfWithTextIsRefused() {
        // The calibration sheet is printed from the PC itself; as a received PDF its text can't be reproduced.
        val e = assertFailsWith<PrintException> { reader.read(SimplePdfRenderer().render(PrintDocument(listOf(CalibrationSheet.page())))) }
        assertTrue(e.message!!.startsWith("This PDF wasn't made by Photo Dokan (text)"), e.message)
    }

    @Test
    fun aPdfFromAnotherAppIsRefused() {
        val pdf = PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4).also(doc::addPage)
            PDPageContentStream(doc, page).use { it.setNonStrokingColor(0.5f); it.addRect(100f, 100f, 200f, 200f); it.fill() }
            ByteArrayOutputStream().also(doc::save).toByteArray()
        }
        val e = assertFailsWith<PrintException> { reader.read(pdf) }
        assertTrue(e.message!!.contains("(filled shapes)") && e.message!!.endsWith("print it at Actual size instead."), e.message)
    }
}

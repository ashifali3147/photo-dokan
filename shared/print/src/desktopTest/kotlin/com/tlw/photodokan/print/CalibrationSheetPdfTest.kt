package com.tlw.photodokan.print

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalibrationSheetPdfTest {

    private val pdf = PdfBoxRenderer().render(PrintDocument(listOf(CalibrationSheet.page())))

    @Test
    fun rendersAsOneA4Page() {
        Loader.loadPDF(pdf).use { doc ->
            assertEquals(1, doc.numberOfPages)
            val box = doc.getPage(0).mediaBox
            assertTrue(abs(595.2756 - box.width) < 0.01 && abs(841.8898 - box.height) < 0.01, "$box")
        }
    }

    @Test
    fun squareIs100mmInThePdf() {
        // 100 mm = 283.4646 pt. The square is the only 100 × 100 rectangle drawn.
        val square = Regex("""([\d.]+) ([\d.]+) 283\.46\d* 283\.46\d* re""")
        val content = Loader.loadPDF(pdf).use { doc -> doc.getPage(0).contents.readBytes().decodeToString() }
        assertEquals(1, square.findAll(content).count(), "expected one 100 mm square")
    }

    @Test
    fun instructionsAreReadableText() {
        val text = Loader.loadPDF(pdf).use(PDFTextStripper()::getText)
        assertTrue("100 × 100 mm" in text)
        assertTrue("Actual size (100%)" in text)
        assertTrue("±0.5 mm" in text)
    }
}

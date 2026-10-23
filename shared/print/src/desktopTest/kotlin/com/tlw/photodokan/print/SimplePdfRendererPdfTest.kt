package com.tlw.photodokan.print

import com.tlw.photodokan.core.model.Order
import com.tlw.photodokan.core.model.SheetState
import com.tlw.photodokan.core.packing.PackingEngine
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.awt.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The shared PDF writer, which the phone uses, read back with PDFBox and compared with the desktop's output. */
class SimplePdfRendererPdfTest : PdfRendererContract() {

    override val renderer = SimplePdfRenderer()

    private fun assertClose(expected: List<Double>, actual: List<Double>, what: String) {
        assertEquals(expected.size, actual.size, "$what count")
        for ((e, a) in expected.zip(actual)) assertTrue(abs(e - a) < 0.001, "$what: expected $e pt, was $a pt")
    }

    /** Both renderers' output for [document], drawn the same: page sizes, images, lines, outlines and text. */
    private fun assertSameAsPdfBox(document: PrintDocument) {
        val ours = renderer.render(document)
        val desktop = PdfBoxRenderer().render(document)
        Loader.loadPDF(ours).use { a ->
            Loader.loadPDF(desktop).use { b ->
                assertEquals(b.numberOfPages, a.numberOfPages)
                for (i in 0 until a.numberOfPages) {
                    val pa = a.getPage(i)
                    val pb = b.getPage(i)
                    assertClose(listOf(pb.mediaBox.width.toDouble(), pb.mediaBox.height.toDouble()), listOf(pa.mediaBox.width.toDouble(), pa.mediaBox.height.toDouble()), "page size")
                    val ra = Recorder(pa).also(Recorder::run)
                    val rb = Recorder(pb).also(Recorder::run)
                    assertClose(rb.images.flatMap { listOf(it.x, it.y, it.width, it.height) }, ra.images.flatMap { listOf(it.x, it.y, it.width, it.height) }, "images")
                    assertClose(rb.lines.flatMap { listOf(it.x1, it.y1, it.x2, it.y2) }, ra.lines.flatMap { listOf(it.x1, it.y1, it.x2, it.y2) }, "lines")
                    assertClose(rb.rects.flatMap { listOf(it.x, it.y, it.width, it.height) }, ra.rects.flatMap { listOf(it.x, it.y, it.width, it.height) }, "outlines")
                }
                assertEquals(PDFTextStripper().getText(b), PDFTextStripper().getText(a))
            }
        }
    }

    @Test
    fun aPackedSheetMatchesTheDesktopPdf() {
        val orders = listOf(Order("A", "0001", 5), Order("B", "0002", 10), Order("C", "0003", 4))
        val photos = mapOf("A" to testJpeg(Color.BLUE), "B" to testJpeg(Color.RED), "C" to testJpeg(Color.GREEN))
        val sheets = PackingEngine.pack(template, SheetState.FRESH, orders).sheets
        assertSameAsPdfBox(PrintDocument(sheets.map { sheetPage(template, it, photos::getValue) }))
    }

    @Test
    fun aPartUsedSheetAndAnOverflowMatchTheDesktopPdf() {
        val orders = listOf(Order("A", "0001", 30), Order("B", "0002", 15))
        val photos = mapOf("A" to testJpeg(Color.BLUE), "B" to testJpeg(Color.RED))
        val sheets = PackingEngine.pack(template, SheetState((0..6).toSet()), orders).sheets
        assertSameAsPdfBox(PrintDocument(sheets.map { sheetPage(template, it, photos::getValue) }))
    }

    @Test
    fun theCalibrationSheetMatchesTheDesktopPdf() = assertSameAsPdfBox(PrintDocument(listOf(CalibrationSheet.page())))
}

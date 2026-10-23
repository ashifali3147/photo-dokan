package com.tlw.photodokan.print

import com.tlw.photodokan.core.geometry.PaperSizes
import com.tlw.photodokan.core.geometry.RectMm
import com.tlw.photodokan.print.SimplePdfRenderer.Companion.num
import com.tlw.photodokan.print.SimplePdfRenderer.Companion.pdfText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The parts of the PDF writer that need no PDF reader; the desktop tests read its output with PDFBox. */
class SimplePdfRendererTest {

    /** Just enough of a JPEG for its header to be read: start, an APP0 segment, then a frame header. */
    private fun jpegHeader(width: Int, height: Int, components: Int = 3, frameMarker: Int = 0xC0): ByteArray {
        val app0 = listOf(0xFF, 0xE0, 0x00, 0x10) + List(14) { 0 }
        val frame = listOf(0xFF, frameMarker, 0x00, 8 + 3 * components, 8, height shr 8, height and 0xFF, width shr 8, width and 0xFF, components) +
            List(3 * components) { 1 }
        return (listOf(0xFF, 0xD8) + app0 + frame + listOf(0xFF, 0xD9)).map { it.toByte() }.toByteArray()
    }

    @Test
    fun numbersHaveUpToFourDecimalsAndNoExponent() {
        assertEquals("595.2756", num(595.2755905511812))
        assertEquals("841.8898", num(841.8897637795276))
        assertEquals("12", num(12.0))
        assertEquals("0.6", num(0.6))
        assertEquals("0.0001", num(0.00005))
        assertEquals("0", num(0.00004))
        assertEquals("-3.5", num(-3.5))
        assertEquals("1000000", num(1e6))
    }

    @Test
    fun textIsEscapedForPdfStrings() {
        assertEquals("100 \\327 100 mm \\2610.5", pdfText("100 × 100 mm ±0.5"))
        assertEquals("a\\(b\\)c\\\\d", pdfText("a(b)c\\d"))
        assertEquals("Photo ? Dokan", pdfText("Photo ক Dokan"))
    }

    @Test
    fun readsTheSizeAndChannelsFromTheJpegHeader() {
        assertEquals(Jpeg.Frame(1020, 963, 3), Jpeg.frame(jpegHeader(1020, 963)))
        assertEquals(Jpeg.Frame(300, 200, 1), Jpeg.frame(jpegHeader(300, 200, components = 1)))
        assertEquals(Jpeg.Frame(64, 48, 3), Jpeg.frame(jpegHeader(64, 48, frameMarker = 0xC2)), "progressive")
        assertTrue(Jpeg.dictionary(jpegHeader(300, 200, components = 1)).contains("/DeviceGray"))
        assertTrue(Jpeg.dictionary(jpegHeader(300, 200, components = 4)).contains("/DeviceCMYK"))
    }

    @Test
    fun anythingThatIsNotAJpegIsRefused() {
        assertNull(Jpeg.frame(byteArrayOf()))
        assertNull(Jpeg.frame("%PDF-1.4".encodeToByteArray()))
        assertNull(Jpeg.frame(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())), "no frame")
        val page = PrintPage(PaperSizes.A4, listOf(PageItem.Image(RectMm(10.0, 10.0, 36.0, 34.0), byteArrayOf(1, 2, 3))))
        assertFailsWith<PrintException> { SimplePdfRenderer().render(PrintDocument(listOf(page))) }
    }

    @Test
    fun everyCrossReferencePointsAtItsObject() {
        val jpeg = jpegHeader(10, 10)
        val page = PrintPage(PaperSizes.A4, listOf(PageItem.Image(RectMm(10.0, 10.0, 36.0, 34.0), jpeg), PageItem.Text(10.0, 60.0, 4.0, "0001")))
        val pdf = SimplePdfRenderer().render(PrintDocument(listOf(page, page)))
        val text = pdf.decodeToString()

        val start = text.substringAfterLast("startxref\n").substringBefore('\n').toInt()
        val xref = text.substring(start)
        assertTrue(xref.startsWith("xref\n0 "), "startxref points at the table")
        val entries = Regex("""(\d{10}) 00000 n """).findAll(xref).map { it.groupValues[1].toInt() }.toList()
        entries.forEachIndexed { i, offset ->
            assertTrue(text.startsWith("${i + 1} 0 obj", offset), "object ${i + 1} is at $offset")
        }
        assertTrue(text.endsWith("%%EOF\n"))
    }

    @Test
    fun aPhotoOnSeveralPagesIsStoredOnce() {
        val jpeg = jpegHeader(10, 10)
        val page = PrintPage(PaperSizes.A4, List(5) { PageItem.Image(RectMm(10.0 + 40 * it, 10.0, 36.0, 34.0), jpeg) })
        val pdf = SimplePdfRenderer().render(PrintDocument(listOf(page, page))).decodeToString()
        assertEquals(1, Regex("/Subtype /Image").findAll(pdf).count())
        assertEquals(10, Regex(" Do Q").findAll(pdf).count())
    }

    @Test
    fun a4IsExactInPoints() {
        val pdf = SimplePdfRenderer().render(PrintDocument(listOf(PrintPage(PaperSizes.A4, emptyList())))).decodeToString()
        assertTrue(pdf.contains("/MediaBox [0 0 595.2756 841.8898]"), pdf)
    }
}

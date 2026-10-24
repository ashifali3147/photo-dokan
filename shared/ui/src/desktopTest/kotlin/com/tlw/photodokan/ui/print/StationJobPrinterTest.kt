package com.tlw.photodokan.ui.print

import com.tlw.photodokan.core.library.StudioDate
import com.tlw.photodokan.core.model.SheetTemplate
import com.tlw.photodokan.print.PdfSheetReader
import com.tlw.photodokan.print.PrintDocument
import com.tlw.photodokan.print.PrintException
import com.tlw.photodokan.print.PrintPage
import com.tlw.photodokan.print.SheetPrinter
import com.tlw.photodokan.station.JobResult
import com.tlw.photodokan.ui.customers.InMemoryPhotoLibrary
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StationJobPrinterTest {

    private val today = StudioDate(2026, 10, 24)
    private val sheet = "%PDF-1.4 sheet".encodeToByteArray()
    private val page = PrintPage(SheetTemplate.STUDIO_A4_5X8.paper, emptyList())

    /** Reads every PDF as [pages] pages, or refuses it like a PDF Photo Dokan didn't make. */
    private class FakeReader(var pages: Int = 1, var refusal: String? = null, val page: PrintPage) : PdfSheetReader {
        override fun read(pdf: ByteArray): PrintDocument {
            refusal?.let { throw PrintException(it) }
            return PrintDocument(List(pages) { page })
        }
    }

    private class FakePrinter(var failure: Exception? = null) : SheetPrinter {
        val jobs = mutableListOf<Pair<PrintDocument, String>>()
        override fun printerName() = "EPSON L3360 Series"
        override fun photoDpi() = 360.0
        override fun print(document: PrintDocument, jobName: String) {
            failure?.let { throw it }
            jobs += document to jobName
        }
    }

    private val reader = FakeReader(page = page)
    private val printer = FakePrinter()
    private val library = InMemoryPhotoLibrary()
    private val jobs = StationJobPrinter(reader, printer, library, { today }, { "14.30" })

    @Test
    fun aPhonesSheetPrintsAndIsKeptByDate() {
        val result = jobs.print(sheet, "Photo Dokan 0001 0002", "Galaxy A15")

        assertEquals(JobResult(printed = true, message = "The sheet printed on the PC."), result)
        assertEquals("Photo Dokan 0001 0002", printer.jobs.single().second)
        val (path, kept) = library.sheets.entries.single()
        assertEquals("2026/10/24/Phone sheets/Photo Dokan 0001 0002 14.30.pdf", path)
        assertContentEquals(sheet, kept)
    }

    @Test
    fun anOverflowingOrderPrintsEverySheet() {
        reader.pages = 2
        assertEquals("All 2 sheets printed on the PC.", jobs.print(sheet, "Photo Dokan 0001", "Phone").message)
        assertEquals(2, printer.jobs.single().first.pages.size)
    }

    @Test
    fun aPdfPhotoDokanDidNotMakeIsNotPrinted() {
        reader.refusal = "This PDF wasn't made by Photo Dokan (text), so it can't be printed here at exact size."
        val result = jobs.print(sheet, "Invoice", "Phone")
        assertEquals(JobResult(printed = false, message = reader.refusal!!), result)
        assertTrue(printer.jobs.isEmpty())
        assertTrue(library.sheets.isEmpty(), "nothing kept that wasn't printed")
    }

    @Test
    fun aPrinterProblemGoesBackToThePhone() {
        printer.failure = PrintException("The printer is out of paper.")
        assertEquals(JobResult(printed = false, message = "The printer is out of paper."), jobs.print(sheet, "Photo Dokan 0001", "Phone"))
        printer.failure = IllegalStateException("spooler stopped")
        assertEquals("Printing failed on the PC: spooler stopped", jobs.print(sheet, "Photo Dokan 0001", "Phone").message)
        assertTrue(library.sheets.isEmpty())
    }

    @Test
    fun aSheetIsStillPrintedWhenNoCopyCanBeKept() {
        val full = object : com.tlw.photodokan.core.library.PhotoLibrary by library {
            override fun fileSheet(date: StudioDate, fileName: String, pdf: ByteArray): String = throw IllegalStateException("disk full")
        }
        val result = StationJobPrinter(reader, printer, full, { today }, { "14.30" }).print(sheet, "Photo Dokan 0001", "Phone")
        assertEquals(JobResult(printed = true, message = "The sheet printed on the PC, but a copy couldn't be kept there."), result)
    }

    @Test
    fun theWindowsQueueShowsPhotoDokan() {
        jobs.print(sheet, "Sheet from Galaxy A15", "Galaxy A15")
        assertEquals("Photo Dokan Sheet from Galaxy A15", printer.jobs.single().second)
    }
}

package com.tlw.photodokan.print

import org.apache.pdfbox.Loader
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PdfFilePrinterTest {

    private val folder: File = Files.createTempDirectory("photo-dokan-prints").toFile()

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    @Test
    fun savesEveryPageAsOnePdfNamedByTime() {
        val printer = PdfFilePrinter(File(folder, "prints"), open = false, now = { LocalDateTime.of(2026, 10, 22, 14, 5, 9) })
        val page = CalibrationSheet.page()
        printer.print(PrintDocument(listOf(page, page)), "Photo Dokan test")

        val file = File(folder, "prints/sheet-20261022-140509.pdf")
        assertEquals(file, printer.lastFile)
        Loader.loadPDF(file).use { assertEquals(2, it.numberOfPages) }
    }
}

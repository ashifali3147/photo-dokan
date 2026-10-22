package com.tlw.photodokan.print

import java.awt.Desktop
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * "Prints" by saving a PDF into [folder] and, when [open] is set, opening it. Used where there is
 * no Windows printer, e.g. while developing on a Mac; the PDF is at actual size.
 */
class PdfFilePrinter(
    private val folder: File,
    private val renderer: PdfRenderer = PdfBoxRenderer(),
    private val open: Boolean = true,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) : SheetPrinter {

    /** The last file saved, for tests and messages. */
    var lastFile: File? = null
        private set

    override fun printerName(): String = "PDF file"

    override fun photoDpi(): Double = 300.0

    override fun print(document: PrintDocument, jobName: String) {
        if (!folder.isDirectory && !folder.mkdirs()) throw PrintException("Could not create the folder ${folder.path}.")
        val file = File(folder, "sheet-${now().format(STAMP)}.pdf")
        file.writeBytes(renderer.render(document))
        lastFile = file
        if (open && Desktop.isDesktopSupported()) runCatching { Desktop.getDesktop().open(file) }
    }

    private companion object {
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}

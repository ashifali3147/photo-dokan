package com.tlw.photodokan.ui.print

import com.tlw.photodokan.core.library.PhotoLibrary
import com.tlw.photodokan.core.library.StudioDate
import com.tlw.photodokan.print.PdfSheetReader
import com.tlw.photodokan.print.PrintException
import com.tlw.photodokan.print.SheetPrinter
import com.tlw.photodokan.station.JobResult
import com.tlw.photodokan.station.StationJobs

/**
 * Prints the sheets paired phones send through the Print Station, as soon as they arrive: checks the
 * PDF is one Photo Dokan made, prints it at exact size with this PC's photo settings and size
 * correction, then keeps it in the library under the day it was printed. The phone keeps track of
 * its own sheet, so this PC's queue and sheet don't change.
 *
 * @param time the time of day for the kept copy's name, e.g. "14.30".
 */
class StationJobPrinter(
    private val reader: PdfSheetReader,
    private val printer: SheetPrinter,
    private val library: PhotoLibrary,
    private val today: () -> StudioDate,
    private val time: () -> String,
) : StationJobs {

    override fun print(pdf: ByteArray, name: String, deviceName: String): JobResult {
        val document = try {
            reader.read(pdf)
        } catch (e: PrintException) {
            return JobResult(printed = false, message = e.message ?: "The PC couldn't read this sheet.")
        } catch (e: Exception) {
            return JobResult(printed = false, message = "The PC couldn't read this sheet. Use Share PDF instead.")
        }
        try {
            // Shown in the Windows print queue; the phone's names already start with "Photo Dokan".
            printer.print(document, if (name.startsWith("Photo Dokan")) name else "Photo Dokan $name")
        } catch (e: PrintException) {
            return JobResult(printed = false, message = e.message ?: "Printing failed on the PC.")
        } catch (e: Exception) {
            return JobResult(printed = false, message = "Printing failed on the PC: ${e.message ?: e::class.simpleName}")
        }
        val kept = runCatching { library.fileSheet(today(), "$name ${time()}", pdf) }.isSuccess
        val pages = document.pages.size
        val what = if (pages == 1) "The sheet" else "All $pages sheets"
        return JobResult(
            printed = true,
            message = if (kept) "$what printed on the PC." else "$what printed on the PC, but a copy couldn't be kept there.",
        )
    }
}

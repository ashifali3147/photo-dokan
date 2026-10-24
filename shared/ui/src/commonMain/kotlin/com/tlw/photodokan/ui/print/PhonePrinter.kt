package com.tlw.photodokan.ui.print

import com.tlw.photodokan.print.PrintDocument

/**
 * Prints a sheet through the phone's own print dialog, straight to a Wi-Fi printer the phone can
 * reach (through Mopria or the printer maker's print service). Whether the printer keeps the
 * sheet at actual size is up to its print service, so the PC stays the main way to print.
 */
interface PhonePrinter {
    /**
     * Opens the print dialog for [document]. Called on the main thread; returns once the dialog
     * is open, before anything has printed.
     */
    fun print(document: PrintDocument, jobName: String)
}

package com.tlw.photodokan.ui.print

/**
 * Hands a finished sheet PDF to the operator's other apps (WhatsApp, Files, Bluetooth …), the way
 * a phone gets a sheet to the studio PC when the Print Station can't be reached.
 */
interface SheetSharer {
    /** Called on the main thread with the PDF's bytes and a file name such as "Photo Dokan 0001-0003.pdf". */
    fun share(pdf: ByteArray, fileName: String)
}

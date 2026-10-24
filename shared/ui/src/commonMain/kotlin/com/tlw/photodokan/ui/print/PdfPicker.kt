package com.tlw.photodokan.ui.print

import androidx.compose.runtime.Composable

/** A file the operator chose. [read] loads its bytes; call it off the UI thread. */
class PickedFile(val name: String, val read: () -> ByteArray)

/**
 * Remembers a picker for a sheet PDF received from the phone. Calling the returned function opens
 * it. Null where sheets aren't printed from PDFs (the phone itself).
 */
@Composable
expect fun rememberPdfPicker(onPicked: (PickedFile) -> Unit): (() -> Unit)?

package com.tlw.photodokan.ui.print

import androidx.compose.runtime.Composable

/** The phone shares sheet PDFs; it never prints one it received. */
@Composable
actual fun rememberPdfPicker(onPicked: (PickedFile) -> Unit): (() -> Unit)? = null

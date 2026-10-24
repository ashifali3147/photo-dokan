package com.tlw.photodokan.ui.station

import androidx.compose.runtime.Composable

/** The PC shows the pairing code; it never scans one. */
@Composable
actual fun rememberQrScanner(onScanned: (String) -> Unit): (() -> Unit)? = null

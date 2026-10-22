package com.tlw.photodokan.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tlw.photodokan.ui.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Photo Dokan",
        state = rememberWindowState(width = 1100.dp, height = 820.dp),
    ) {
        App()
    }
}

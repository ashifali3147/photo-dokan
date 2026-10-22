package com.tlw.photodokan.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tlw.photodokan.ui.App
import com.tlw.photodokan.ui.di.initKoin

fun main() {
    initKoin()
    application {
        val windowState = rememberWindowState(width = 1100.dp, height = 820.dp)
        var showRequests by remember { mutableIntStateOf(0) }

        // Closing the window still quits the app. Once the Print Station runs in the background,
        // closing will hide to the tray instead, and Quit here will be the way to stop it.
        Tray(
            icon = AppIcon,
            tooltip = "Photo Dokan",
            onAction = { showRequests++ },
            menu = {
                Item("Open Photo Dokan", onClick = { showRequests++ })
                Separator()
                Item("Quit", onClick = ::exitApplication)
            },
        )

        Window(
            onCloseRequest = ::exitApplication,
            title = "Photo Dokan",
            icon = AppIcon,
            state = windowState,
        ) {
            LaunchedEffect(showRequests) {
                if (showRequests > 0) {
                    windowState.isMinimized = false
                    window.toFront()
                    window.requestFocus()
                }
            }
            App()
        }
    }
}

package com.tlw.photodokan.desktop

import androidx.compose.runtime.DisposableEffect
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
import com.tlw.photodokan.core.library.PhotoLibrary
import com.tlw.photodokan.core.model.PrintQueueStore
import com.tlw.photodokan.library.FilePhotoLibrary
import com.tlw.photodokan.library.FilePrintQueueStore
import com.tlw.photodokan.print.FilePrintProfileStore
import com.tlw.photodokan.print.PdfFilePrinter
import com.tlw.photodokan.print.SheetPrinter
import com.tlw.photodokan.print.WindowsGdiPrinter
import com.tlw.photodokan.ui.App
import com.tlw.photodokan.ui.di.initKoin
import kotlinx.io.files.Path
import org.koin.dsl.module
import java.io.File

// TODO(settings): let the operator pick the library folder, e.g. D:\PhotoDokan\Photos on the studio PC.
private val desktopModule = module {
    single<PhotoLibrary> { FilePhotoLibrary(Path(System.getProperty("user.home"), "Pictures", "Photo Dokan")) }
    single<PrintQueueStore> { FilePrintQueueStore(Path(appDataDir(), "print-queue.json")) }
    // Away from Windows (developing on a Mac) Print saves an actual-size PDF instead.
    single<SheetPrinter> {
        if (WindowsGdiPrinter.isAvailable) {
            WindowsGdiPrinter(FilePrintProfileStore(File(appDataDir().toString(), "photo-print-profile.bin")), owner = { mainWindow })
        } else {
            PdfFilePrinter(File(appDataDir().toString(), "prints"))
        }
    }
}

/** The open app window, so the printer's settings window can open over it. */
@Volatile
private var mainWindow: java.awt.Window? = null

/** The app's own files: %APPDATA%\Photo Dokan on Windows, ~/.photo-dokan when developing elsewhere. */
private fun appDataDir(): Path =
    System.getenv("APPDATA")?.let { Path(it, "Photo Dokan") } ?: Path(System.getProperty("user.home"), ".photo-dokan")

fun main() {
    initKoin(listOf(desktopModule))
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
            DisposableEffect(window) {
                mainWindow = window
                onDispose { mainWindow = null }
            }
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

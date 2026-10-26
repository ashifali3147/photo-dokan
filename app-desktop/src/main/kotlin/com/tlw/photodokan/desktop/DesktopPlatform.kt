package com.tlw.photodokan.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.tlw.photodokan.ui.platform.PhotoFolder
import com.tlw.photodokan.ui.theme.ThemeMode
import java.awt.Desktop
import java.awt.Window
import java.io.File

/** The library folder on this PC, opened in Explorer (or Finder when developing on a Mac). */
internal class DesktopPhotoFolder(private val folder: File) : PhotoFolder {
    override val location: String get() = folder.absolutePath

    override fun open() {
        folder.mkdirs()
        Desktop.getDesktop().open(folder)
    }
}

/**
 * Draws the window's title bar dark or light to match the app, so a dark app doesn't sit under a
 * bright bar. On Windows 10 and 11 [match] asks the desktop window manager and takes effect at
 * once. On macOS, where this is only for development, the appearance can only be chosen before the
 * window opens, so [prepare] sets it from the saved theme and a change applies from the next start.
 */
internal object TitleBar {
    private val os = System.getProperty("os.name").orEmpty().lowercase()

    /** Call before the window opens. */
    fun prepare(mode: ThemeMode) {
        if (!os.contains("mac")) return
        System.setProperty(
            "apple.awt.application.appearance",
            when (mode) {
                ThemeMode.Dark -> "NSAppearanceNameDarkAqua"
                ThemeMode.Light -> "NSAppearanceNameAqua"
                ThemeMode.System -> "system"
            },
        )
    }

    fun match(window: Window, dark: Boolean) {
        if (os.startsWith("windows")) runCatching { windows(window, dark) }
    }

    @Suppress("FunctionName")
    private interface DwmApi : Library {
        fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: IntByReference, size: Int): Int
    }

    private val dwm: DwmApi? by lazy { runCatching { Native.load("dwmapi", DwmApi::class.java) }.getOrNull() }

    private fun windows(window: Window, dark: Boolean) {
        if (!window.isDisplayable) return
        val hwnd = Native.getComponentPointer(window) ?: return
        val value = IntByReference(if (dark) 1 else 0)
        // DWMWA_USE_IMMERSIVE_DARK_MODE is 20 from Windows 10 20H1 on, and was 19 before it.
        val result = dwm?.DwmSetWindowAttribute(hwnd, 20, value, 4) ?: return
        if (result != 0) dwm?.DwmSetWindowAttribute(hwnd, 19, value, 4)
    }
}

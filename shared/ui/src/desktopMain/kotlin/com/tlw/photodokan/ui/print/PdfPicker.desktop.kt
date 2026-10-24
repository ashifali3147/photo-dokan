package com.tlw.photodokan.ui.print

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
actual fun rememberPdfPicker(onPicked: (PickedFile) -> Unit): (() -> Unit)? {
    val callback by rememberUpdatedState(onPicked)
    return remember {
        {
            val dialog = FileDialog(null as Frame?, "Choose the sheet PDF from the phone", FileDialog.LOAD).apply {
                file = "*.pdf" // the filter Windows uses
                setFilenameFilter { _, name -> name.endsWith(".pdf", ignoreCase = true) } // macOS
                isVisible = true // blocks until the operator picks or cancels
            }
            val directory = dialog.directory
            val name = dialog.file
            if (directory != null && name != null) File(directory, name).let { callback(PickedFile(it.name) { it.readBytes() }) }
        }
    }
}

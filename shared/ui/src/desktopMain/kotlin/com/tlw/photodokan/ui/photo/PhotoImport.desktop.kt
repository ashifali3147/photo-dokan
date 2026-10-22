package com.tlw.photodokan.ui.photo

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI

private val PHOTO_EXTENSIONS = setOf("jpg", "jpeg", "png")

private fun File.toPhotoSource() = PhotoSource(name) { readBytes() }

@Composable
actual fun rememberPhotoPicker(onPicked: (PhotoSource) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onPicked)
    return remember {
        {
            val dialog = FileDialog(null as Frame?, "Choose a photo", FileDialog.LOAD).apply {
                file = "*.jpg;*.jpeg;*.png" // the filter Windows uses
                setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in PHOTO_EXTENSIONS } // macOS
                isVisible = true // blocks until the operator picks or cancels
            }
            val directory = dialog.directory
            val name = dialog.file
            if (directory != null && name != null) callback(File(directory, name).toPhotoSource())
        }
    }
}

actual val canDropPhotos: Boolean = true

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.photoDropTarget(onHoverChange: (Boolean) -> Unit, onDrop: (PhotoSource) -> Unit): Modifier = composed {
    val hover by rememberUpdatedState(onHoverChange)
    val drop by rememberUpdatedState(onDrop)
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) = hover(true)
            override fun onExited(event: DragAndDropEvent) = hover(false)
            override fun onEnded(event: DragAndDropEvent) = hover(false)
            override fun onDrop(event: DragAndDropEvent): Boolean {
                hover(false)
                val files = (event.dragData() as? DragData.FilesList)?.readFiles() ?: return false
                val file = files.firstOrNull()?.let { File(URI(it)) } ?: return false
                drop(file.toPhotoSource())
                return true
            }
        }
    }
    dragAndDropTarget(shouldStartDragAndDrop = { true }, target = target)
}

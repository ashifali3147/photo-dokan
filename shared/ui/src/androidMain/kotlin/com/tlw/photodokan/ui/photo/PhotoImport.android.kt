package com.tlw.photodokan.ui.photo

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/** The system Photo Picker: no storage permission needed. */
@Composable
actual fun rememberPhotoPicker(onPicked: (PhotoSource) -> Unit): () -> Unit {
    val resolver = LocalContext.current.contentResolver
    val callback by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "photo"
            callback(PhotoSource(name) { resolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0) })
        }
    }
    return { launcher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
}

actual val canDropPhotos: Boolean = false

actual fun Modifier.photoDropTarget(onHoverChange: (Boolean) -> Unit, onDrop: (PhotoSource) -> Unit): Modifier = this

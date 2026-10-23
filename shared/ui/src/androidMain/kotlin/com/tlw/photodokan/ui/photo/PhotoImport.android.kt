package com.tlw.photodokan.ui.photo

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.io.File

/** The system Photo Picker: no storage permission needed. */
@Composable
actual fun rememberPhotoPicker(onPicked: (PhotoSource) -> Unit): () -> Unit {
    val resolver = LocalContext.current.contentResolver
    val callback by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(PickVisualMedia()) { uri: Uri? ->
        if (uri != null) callback(resolver.photoSource(uri))
    }
    return { launcher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
}

/** The in-app [CameraActivity]; null on a device without a camera. */
@Composable
actual fun rememberCamera(onTaken: (PhotoSource) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onTaken)
    val launcher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        val path = result.data?.getStringExtra(CameraActivity.EXTRA_PATH)
        if (result.resultCode == Activity.RESULT_OK && path != null) {
            val file = File(path)
            callback(PhotoSource(file.name) { file.readBytes() })
        }
    }
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) return null
    return { launcher.launch(Intent(context, CameraActivity::class.java)) }
}

/** A photo behind a content URI, from the Photo Picker or shared by another app. */
fun ContentResolver.photoSource(uri: Uri): PhotoSource {
    val name = query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "photo"
    return PhotoSource(name) { openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0) }
}

actual val canDropPhotos: Boolean = false

actual fun Modifier.photoDropTarget(onHoverChange: (Boolean) -> Unit, onDrop: (PhotoSource) -> Unit): Modifier = this

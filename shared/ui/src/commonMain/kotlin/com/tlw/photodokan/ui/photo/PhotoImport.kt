package com.tlw.photodokan.ui.photo

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A photo the operator chose. [read] loads the original file's bytes; call it off the UI thread. */
class PhotoSource(val name: String, val read: () -> ByteArray)

/** Remembers a platform photo picker. Calling the returned function opens it. */
@Composable
expect fun rememberPhotoPicker(onPicked: (PhotoSource) -> Unit): () -> Unit

/**
 * Remembers the device camera. Calling the returned function opens it; the photo taken arrives
 * in [onTaken], and closing the camera sends nothing. Null where there is no camera to use.
 */
@Composable
expect fun rememberCamera(onTaken: (PhotoSource) -> Unit): (() -> Unit)?

/** True where photos can be dragged onto the app from outside (desktop). */
expect val canDropPhotos: Boolean

/** Accepts a photo file dropped from outside the app. Does nothing where [canDropPhotos] is false. */
expect fun Modifier.photoDropTarget(onHoverChange: (Boolean) -> Unit, onDrop: (PhotoSource) -> Unit): Modifier

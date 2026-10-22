package com.tlw.photodokan.ui.photo

import androidx.compose.ui.graphics.ImageBitmap

/** A reduced, upright copy of a photo for the screen. The size is the upright original's, in pixels. */
class PhotoPreview(val image: ImageBitmap, val originalWidth: Int, val originalHeight: Int)

/**
 * Decodes a reduced copy of a photo, its longer side at most about [maxSide] pixels, turned upright
 * using its EXIF orientation. Returns null when the data is not a photo the platform can read.
 * Blocking: call it off the UI thread. The original bytes are never changed.
 */
expect fun decodePreview(bytes: ByteArray, maxSide: Int = 2000): PhotoPreview?

package com.tlw.photodokan.ui.photo

import androidx.compose.ui.graphics.ImageBitmap

/** A reduced, upright copy of a photo for the screen. The size is the upright original's, in pixels. */
class PhotoPreview(val image: ImageBitmap, val originalWidth: Int, val originalHeight: Int)

/**
 * Decodes a reduced copy of a photo, its longer side at least [maxSide] pixels (and less than twice
 * that) or the whole photo when it is smaller, turned upright using its EXIF orientation. With
 * [scaleDown] it is then scaled smoothly so its longer side is at most [maxSide]: the editor's copy,
 * which is painted over and over and must fit in a phone's memory. Returns null when the data is
 * not a photo the platform can read. Blocking: call it off the UI thread. The original bytes are
 * never changed.
 */
expect fun decodePreview(bytes: ByteArray, maxSide: Int = 2000, scaleDown: Boolean = false): PhotoPreview?

/** The upright photo's width and height in pixels, read from its header without decoding it, or null. */
expect fun photoSize(bytes: ByteArray): Pair<Int, Int>?

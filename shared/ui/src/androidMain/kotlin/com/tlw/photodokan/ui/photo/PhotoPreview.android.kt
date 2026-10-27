package com.tlw.photodokan.ui.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.ui.graphics.asImageBitmap
import com.tlw.photodokan.core.imaging.ExifOrientation

actual fun photoSize(bytes: ByteArray): Pair<Int, Int>? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    return if (ExifOrientation.read(bytes).swapsDimensions) bounds.outHeight to bounds.outWidth else bounds.outWidth to bounds.outHeight
}

actual fun decodePreview(bytes: ByteArray, maxSide: Int, scaleDown: Boolean): PhotoPreview? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val width = bounds.outWidth
    val height = bounds.outHeight
    if (width <= 0 || height <= 0) return null

    // Power-of-two sampling is the cheapest decode; the result is at most about twice maxSide.
    var sample = 1
    while (maxOf(width, height) / (sample * 2) >= maxSide) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return null

    val orientation = ExifOrientation.read(bytes)
    val longer = maxOf(decoded.width, decoded.height)
    val scale = if (scaleDown && longer > maxSide) maxSide.toFloat() / longer else 1f
    val upright = if (orientation == ExifOrientation.NORMAL && scale == 1f) {
        decoded
    } else {
        // Scaled, mirrored and turned in one step, so only one more copy is made.
        val matrix = Matrix().apply {
            postScale(scale, scale)
            if (orientation.mirrored) postScale(-1f, 1f)
            postRotate(orientation.rotationDegrees.toFloat())
        }
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { if (it !== decoded) decoded.recycle() }
    }
    return if (orientation.swapsDimensions) {
        PhotoPreview(upright.asImageBitmap(), height, width)
    } else {
        PhotoPreview(upright.asImageBitmap(), width, height)
    }
}

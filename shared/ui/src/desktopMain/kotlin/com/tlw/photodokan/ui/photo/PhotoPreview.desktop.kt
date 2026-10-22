package com.tlw.photodokan.ui.photo

import androidx.compose.ui.graphics.toComposeImageBitmap
import com.tlw.photodokan.core.imaging.ExifOrientation
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.IOException
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.ceil

actual fun decodePreview(bytes: ByteArray, maxSide: Int): PhotoPreview? {
    val input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)) ?: return null
    input.use {
        val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: return null
        try {
            reader.input = input
            val width = reader.getWidth(0)
            val height = reader.getHeight(0)
            // Skip pixels while decoding, so a 24 MP photo never needs full-size memory for the preview.
            val step = maxOf(1, ceil(maxOf(width, height) / maxSide.toDouble()).toInt())
            val param = reader.defaultReadParam.apply { setSourceSubsampling(step, step, 0, 0) }
            val orientation = ExifOrientation.read(bytes)
            val upright = reader.read(0, param).oriented(orientation)
            return if (orientation.swapsDimensions) {
                PhotoPreview(upright.toComposeImageBitmap(), height, width)
            } else {
                PhotoPreview(upright.toComposeImageBitmap(), width, height)
            }
        } catch (e: IOException) {
            return null
        } finally {
            reader.dispose()
        }
    }
}

/** Mirror first, then rotate clockwise, as [ExifOrientation] describes. */
private fun BufferedImage.oriented(orientation: ExifOrientation): BufferedImage {
    if (orientation == ExifOrientation.NORMAL) return this
    val outWidth = if (orientation.swapsDimensions) height else width
    val outHeight = if (orientation.swapsDimensions) width else height
    val transform = AffineTransform().apply {
        when (orientation.rotationDegrees) {
            90 -> { translate(outWidth.toDouble(), 0.0); rotate(PI / 2) }
            180 -> { translate(outWidth.toDouble(), outHeight.toDouble()); rotate(PI) }
            270 -> { translate(0.0, outHeight.toDouble()); rotate(-PI / 2) }
        }
        if (orientation.mirrored) { translate(width.toDouble(), 0.0); scale(-1.0, 1.0) }
    }
    val type = if (colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
    return BufferedImage(outWidth, outHeight, type).also { out ->
        out.createGraphics().apply { drawImage(this@oriented, transform, null); dispose() }
    }
}

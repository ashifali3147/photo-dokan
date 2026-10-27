package com.tlw.photodokan.ui.photo

import androidx.compose.ui.graphics.toComposeImageBitmap
import com.tlw.photodokan.core.imaging.ExifOrientation
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.IOException
import javax.imageio.ImageIO
import kotlin.math.PI

actual fun photoSize(bytes: ByteArray): Pair<Int, Int>? {
    val input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)) ?: return null
    input.use {
        val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: return null
        return try {
            reader.input = input
            val width = reader.getWidth(0)
            val height = reader.getHeight(0)
            if (ExifOrientation.read(bytes).swapsDimensions) height to width else width to height
        } catch (e: IOException) {
            null
        } finally {
            reader.dispose()
        }
    }
}

actual fun decodePreview(bytes: ByteArray, maxSide: Int, scaleDown: Boolean): PhotoPreview? {
    val input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)) ?: return null
    input.use {
        val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: return null
        try {
            reader.input = input
            val width = reader.getWidth(0)
            val height = reader.getHeight(0)
            // Skip pixels while decoding, so a 24 MP photo never needs full-size memory for the preview.
            // Whole steps that keep the longer side at least maxSide, as the phone's decoder does.
            val step = maxOf(1, maxOf(width, height) / maxSide)
            val param = reader.defaultReadParam.apply { setSourceSubsampling(step, step, 0, 0) }
            val orientation = ExifOrientation.read(bytes)
            val decoded = reader.read(0, param)
            val upright = (if (scaleDown) decoded.scaledDown(maxSide) else decoded).oriented(orientation)
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

/** This image scaled smoothly so its longer side is at most [maxSide]. */
private fun BufferedImage.scaledDown(maxSide: Int): BufferedImage {
    val longer = maxOf(width, height)
    if (longer <= maxSide) return this
    val outWidth = maxOf(1, (width.toLong() * maxSide / longer).toInt())
    val outHeight = maxOf(1, (height.toLong() * maxSide / longer).toInt())
    val type = if (colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
    return BufferedImage(outWidth, outHeight, type).also { out ->
        out.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(this@scaledDown, 0, 0, outWidth, outHeight, null)
            dispose()
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

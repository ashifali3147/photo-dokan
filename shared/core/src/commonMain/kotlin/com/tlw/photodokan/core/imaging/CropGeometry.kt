package com.tlw.photodokan.core.imaging

import com.tlw.photodokan.core.model.CropConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Where a crop lands on the upright photo, in that photo's pixels. The output is the
 * [width] × [height] area around the centre, with the photo turned clockwise by
 * [rotationDegrees] about that centre.
 */
data class CropRect(
    val centerX: Double,
    val centerY: Double,
    val width: Double,
    val height: Double,
    val rotationDegrees: Double,
)

/**
 * Crop maths for the editor. The crop frame always has the photo size's [aspect] (width / height),
 * and is kept inside the photo after rotation, so a print never has blank corners.
 *
 * [CropConfig] is relative to the upright photo: centre in 0..1 of its width and height, and scale
 * 1 for the largest frame that fits, so the same recipe works on the preview and the full photo.
 */
object CropGeometry {
    const val MAX_SCALE = 8.0
    const val MAX_ROTATION = 45.0

    fun rect(imageWidth: Int, imageHeight: Int, aspect: Double, crop: CropConfig): CropRect {
        val (baseWidth, baseHeight) = baseSize(imageWidth, imageHeight, aspect)
        return CropRect(
            centerX = crop.centerX * imageWidth,
            centerY = crop.centerY * imageHeight,
            width = baseWidth / crop.scale,
            height = baseHeight / crop.scale,
            rotationDegrees = crop.rotationDegrees,
        )
    }

    /**
     * The nearest crop that fits: rotation within ±45°, zoom between the smallest frame that fits
     * at this angle and [MAX_SCALE], and the centre moved just enough to keep the frame inside.
     */
    fun clamp(imageWidth: Int, imageHeight: Int, aspect: Double, crop: CropConfig): CropConfig {
        val rotation = crop.rotationDegrees.coerceIn(-MAX_ROTATION, MAX_ROTATION)
        val (baseWidth, baseHeight) = baseSize(imageWidth, imageHeight, aspect)
        val c = abs(cos(rotation * PI / 180))
        val s = abs(sin(rotation * PI / 180))
        // A turned frame needs room for its bounding box; zoom in just enough for that to fit.
        val minScale = max(1.0, max((baseWidth * c + baseHeight * s) / imageWidth, (baseWidth * s + baseHeight * c) / imageHeight))
        val scale = crop.scale.coerceIn(minScale, max(minScale, MAX_SCALE))

        val halfBoxX = (baseWidth * c + baseHeight * s) / scale / 2
        val halfBoxY = (baseWidth * s + baseHeight * c) / scale / 2
        val centerX = (crop.centerX * imageWidth).within(halfBoxX, imageWidth - halfBoxX)
        val centerY = (crop.centerY * imageHeight).within(halfBoxY, imageHeight - halfBoxY)
        return CropConfig(
            centerX = (centerX / imageWidth).coerceIn(0.0, 1.0),
            centerY = (centerY / imageHeight).coerceIn(0.0, 1.0),
            scale = scale,
            rotationDegrees = rotation,
        )
    }

    /**
     * Moves the photo under the frame by ([dx], [dy]), given as fractions of the frame's width and
     * height: dragging right by half the frame moves the photo right by half the frame.
     */
    fun pan(imageWidth: Int, imageHeight: Int, aspect: Double, crop: CropConfig, dx: Double, dy: Double): CropConfig {
        val r = rect(imageWidth, imageHeight, aspect, crop)
        val ox = -dx * r.width
        val oy = -dy * r.height
        // Frame axes are the photo's axes turned by the rotation; turn the move back into photo axes.
        val a = -crop.rotationDegrees * PI / 180
        val moveX = ox * cos(a) - oy * sin(a)
        val moveY = ox * sin(a) + oy * cos(a)
        val moved = crop.copy(centerX = ((r.centerX + moveX) / imageWidth).coerceIn(0.0, 1.0), centerY = ((r.centerY + moveY) / imageHeight).coerceIn(0.0, 1.0))
        return clamp(imageWidth, imageHeight, aspect, moved)
    }

    fun zoom(imageWidth: Int, imageHeight: Int, aspect: Double, crop: CropConfig, factor: Double): CropConfig =
        clamp(imageWidth, imageHeight, aspect, crop.copy(scale = (crop.scale * factor).coerceIn(1.0, MAX_SCALE)))

    fun rotate(imageWidth: Int, imageHeight: Int, aspect: Double, crop: CropConfig, degrees: Double): CropConfig =
        clamp(imageWidth, imageHeight, aspect, crop.copy(rotationDegrees = degrees.coerceIn(-MAX_ROTATION, MAX_ROTATION)))

    /** Like coerceIn, but when rounding leaves no room at all it settles in the middle instead of throwing. */
    private fun Double.within(low: Double, high: Double) = if (low > high) (low + high) / 2 else coerceIn(low, high)

    /** The largest frame of [aspect] that fits the photo unrotated: crop scale 1. */
    private fun baseSize(imageWidth: Int, imageHeight: Int, aspect: Double): Pair<Double, Double> {
        require(imageWidth > 0 && imageHeight > 0) { "Image must have a size, was $imageWidth × $imageHeight" }
        require(aspect > 0) { "Aspect must be positive, was $aspect" }
        return if (imageWidth.toDouble() / imageHeight > aspect) {
            imageHeight * aspect to imageHeight.toDouble()
        } else {
            imageWidth.toDouble() to imageWidth / aspect
        }
    }
}

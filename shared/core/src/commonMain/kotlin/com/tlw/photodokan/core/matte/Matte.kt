package com.tlw.photodokan.core.matte

import kotlin.math.floor

/**
 * How much of each pixel of a photo is the person rather than the background: 0 is background,
 * 255 the person, in between at the edges of hair. Row by row, at any resolution: it covers the
 * whole upright photo, so a small matte serves the preview and the full-size print alike.
 */
class Matte(val width: Int, val height: Int, val alpha: ByteArray) {
    init {
        require(width > 0 && height > 0) { "A matte needs a size, was $width × $height" }
        require(alpha.size == width * height) { "A $width × $height matte needs ${width * height} values, had ${alpha.size}" }
    }

    /** The value at pixel ([x], [y]), 0–255. */
    operator fun get(x: Int, y: Int): Int = alpha[y * width + x].toInt() and 0xff

    /**
     * How much of the photo's point at ([fx], [fy]), as fractions of its width and height, is the
     * person, 0 to 1: blended from the four nearest matte pixels, so edges stay smooth when the
     * matte is smaller than the photo.
     */
    fun at(fx: Double, fy: Double): Double {
        val x = (fx * width - 0.5).coerceIn(0.0, width - 1.0)
        val y = (fy * height - 0.5).coerceIn(0.0, height - 1.0)
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val x1 = minOf(x0 + 1, width - 1)
        val y1 = minOf(y0 + 1, height - 1)
        val tx = x - x0
        val ty = y - y0
        val top = this[x0, y0] * (1 - tx) + this[x1, y0] * tx
        val bottom = this[x0, y1] * (1 - tx) + this[x1, y1] * tx
        return (top * (1 - ty) + bottom * ty) / 255
    }

    override fun equals(other: Any?): Boolean =
        other is Matte && width == other.width && height == other.height && alpha.contentEquals(other.alpha)

    override fun hashCode(): Int = (width * 31 + height) * 31 + alpha.contentHashCode()

    override fun toString(): String = "Matte($width × $height)"
}

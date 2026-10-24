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

    /**
     * The matte scaled to a [width] × [height] photo, one value 0–255 per photo pixel: the same
     * values as [at] gives at each pixel's centre, worked out a row at a time so that a full-size
     * photo takes a fraction of a second.
     */
    fun resized(width: Int, height: Int): ByteArray {
        require(width > 0 && height > 0) { "Need a size, was $width × $height" }
        val out = ByteArray(width * height)
        val columns = Lerp(width, this.width)
        val rows = Lerp(height, this.height)
        val line = FloatArray(this.width)
        for (y in 0 until height) {
            val top = rows.low[y] * this.width
            val bottom = rows.high[y] * this.width
            val ty = rows.t[y]
            for (x in 0 until this.width) {
                line[x] = (alpha[top + x].toInt() and 0xff) * (1 - ty) + (alpha[bottom + x].toInt() and 0xff) * ty
            }
            val row = y * width
            for (x in 0 until width) {
                val tx = columns.t[x]
                out[row + x] = (line[columns.low[x]] * (1 - tx) + line[columns.high[x]] * tx + 0.5f).toInt().toByte()
            }
        }
        return out
    }

    /** For each of [n] pixel centres, the two of [size] matte pixels around it and how far between. */
    private class Lerp(n: Int, size: Int) {
        val low = IntArray(n)
        val high = IntArray(n)
        val t = FloatArray(n)

        init {
            for (i in 0 until n) {
                val p = ((i + 0.5) / n * size - 0.5).coerceIn(0.0, size - 1.0)
                low[i] = floor(p).toInt()
                high[i] = minOf(low[i] + 1, size - 1)
                t[i] = (p - low[i]).toFloat()
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        other is Matte && width == other.width && height == other.height && alpha.contentEquals(other.alpha)

    override fun hashCode(): Int = (width * 31 + height) * 31 + alpha.contentHashCode()

    override fun toString(): String = "Matte($width × $height)"
}

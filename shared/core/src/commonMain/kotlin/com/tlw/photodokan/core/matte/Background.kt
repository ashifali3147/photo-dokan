package com.tlw.photodokan.core.matte

import com.tlw.photodokan.core.model.BackgroundConfig
import com.tlw.photodokan.core.model.BackgroundMode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Changes the background of a photo behind the person in its [Matte]: blurred softly (the studio's
 * backdrop kept, its creases and seams gone) or replaced by a solid colour. The same maths for
 * the editor, final.jpg and print, at whatever size the photo is decoded.
 */
object Background {
    /** The blur radius at full strength, as a share of the photo's height. */
    const val MAX_BLUR = 0.04

    /**
     * [argb], a [width] × [height] photo row by row, with its background changed as [config] says.
     * KEEP gives the photo back unchanged.
     */
    fun apply(argb: IntArray, width: Int, height: Int, matte: Matte, config: BackgroundConfig): IntArray {
        require(width > 0 && height > 0 && argb.size == width * height) { "Need $width × $height pixels, had ${argb.size}" }
        if (config.mode == BackgroundMode.KEEP) return argb.copyOf()
        val radius = config.blurStrength * MAX_BLUR * height
        if (config.mode == BackgroundMode.BLUR && radius < 0.5) return argb.copyOf()

        val alpha = matte.resized(width, height)
        val out = IntArray(width * height)
        val blurred = if (config.mode == BackgroundMode.BLUR) BlurredBackground(argb, width, height, alpha, radius) else null
        val colour = config.replaceColorArgb.toInt()
        val back = IntArray(width)
        for (y in 0 until height) {
            val row = y * width
            // Rows that are all person need no background.
            var any = false
            for (x in 0 until width) if (alpha[row + x] != PERSON) { any = true; break }
            if (!any) {
                for (x in 0 until width) out[row + x] = argb[row + x] or OPAQUE
                continue
            }
            if (blurred != null) blurred.row(y, back) else back.fill(colour)
            for (x in 0 until width) {
                val a = alpha[row + x].toInt() and 0xff
                out[row + x] = when (a) {
                    255 -> argb[row + x] or OPAQUE
                    0 -> back[x] or OPAQUE
                    else -> mix(argb[row + x], back[x], a)
                }
            }
        }
        return out
    }

    private const val PERSON: Byte = -1 // 255
    private const val OPAQUE = 0xff shl 24

    /** [person] over [back], [a] of 255 the person. */
    private fun mix(person: Int, back: Int, a: Int): Int {
        val b = 255 - a
        fun channel(shift: Int) = ((((person shr shift) and 0xff) * a + ((back shr shift) and 0xff) * b + 127) / 255)
        return OPAQUE or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /**
     * The background blurred without the person: each point is the average of the background
     * around it, weighted by how much of each pixel is background, so the person's colours never
     * bleed into it. A blur is smooth, so it is worked out on a copy a few times smaller and read
     * back a row at a time, blended between its pixels.
     */
    private class BlurredBackground(argb: IntArray, private val width: Int, height: Int, alpha: ByteArray, radius: Double) {
        private val step = max(1, (radius / 4).toInt())
        private val w = (width + step - 1) / step
        private val h = (height + step - 1) / step
        private val r = FloatArray(w * h)
        private val g = FloatArray(w * h)
        private val b = FloatArray(w * h)
        private val weight = FloatArray(w * h)

        // Where each photo column falls between the small copy's columns.
        private val left = IntArray(width)
        private val right = IntArray(width)
        private val across = FloatArray(width)

        // One row of the small copy, blended between two of its rows.
        private val lineR = FloatArray(w)
        private val lineG = FloatArray(w)
        private val lineB = FloatArray(w)
        private val lineWeight = FloatArray(w)

        init {
            // Sum each block of the photo, weighted by how much is background.
            for (y in 0 until height) {
                val small = (y / step) * w
                for (x in 0 until width) {
                    val k = 255 - (alpha[y * width + x].toInt() and 0xff)
                    if (k == 0) continue
                    val c = argb[y * width + x]
                    val j = small + x / step
                    r[j] += k * ((c shr 16) and 0xff).toFloat()
                    g[j] += k * ((c shr 8) and 0xff).toFloat()
                    b[j] += k * (c and 0xff).toFloat()
                    weight[j] += k.toFloat()
                }
            }
            // Three box blurs come close to a smooth (Gaussian) blur.
            val boxRadius = max(1, (radius / step / 2).roundToInt())
            for (plane in listOf(r, g, b, weight)) repeat(3) { boxBlur(plane, boxRadius) }
            for (x in 0 until width) {
                val p = ((x + 0.5) / step - 0.5).coerceIn(0.0, w - 1.0)
                left[x] = p.toInt()
                right[x] = min(left[x] + 1, w - 1)
                across[x] = (p - left[x]).toFloat()
            }
        }

        /** The blurred background along photo row [y], into [out]. */
        fun row(y: Int, out: IntArray) {
            val p = ((y + 0.5) / step - 0.5).coerceIn(0.0, h - 1.0)
            val top = p.toInt() * w
            val bottom = min(p.toInt() + 1, h - 1) * w
            val t = (p - p.toInt()).toFloat()
            for (x in 0 until w) {
                lineR[x] = r[top + x] * (1 - t) + r[bottom + x] * t
                lineG[x] = g[top + x] * (1 - t) + g[bottom + x] * t
                lineB[x] = b[top + x] * (1 - t) + b[bottom + x] * t
                lineWeight[x] = weight[top + x] * (1 - t) + weight[bottom + x] * t
            }
            for (x in 0 until width) {
                val l = left[x]
                val rr = right[x]
                val tx = across[x]
                val k = lineWeight[l] * (1 - tx) + lineWeight[rr] * tx
                // Deep inside the person there is no background nearby; the person covers it anyway.
                if (k < 1e-2f) {
                    out[x] = OPAQUE
                    continue
                }
                fun channel(line: FloatArray) = ((line[l] * (1 - tx) + line[rr] * tx) / k + 0.5f).toInt().coerceIn(0, 255)
                out[x] = OPAQUE or (channel(lineR) shl 16) or (channel(lineG) shl 8) or channel(lineB)
            }
        }

        /** Averages each value with its neighbours within [radius], across then down; edges repeat. */
        private fun boxBlur(p: FloatArray, radius: Int) {
            val line = FloatArray(max(w, h))
            for (y in 0 until h) {
                for (x in 0 until w) line[x] = p[y * w + x]
                blurLine(line, w, radius) { x, v -> p[y * w + x] = v }
            }
            for (x in 0 until w) {
                for (y in 0 until h) line[y] = p[y * w + x]
                blurLine(line, h, radius) { y, v -> p[y * w + x] = v }
            }
        }

        private inline fun blurLine(line: FloatArray, n: Int, radius: Int, put: (Int, Float) -> Unit) {
            val span = 2 * radius + 1
            var sum = 0f
            for (i in -radius..radius) sum += line[i.coerceIn(0, n - 1)]
            for (i in 0 until n) {
                put(i, sum / span)
                sum += line[(i + radius + 1).coerceAtMost(n - 1)] - line[(i - radius).coerceAtLeast(0)]
            }
        }
    }
}

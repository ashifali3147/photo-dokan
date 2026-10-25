package com.tlw.photodokan.core.matte

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The colour of the background around each point of a photo with the person taken out, or with
 * [person] the colour of the person around it with the background taken out: the average within
 * about [radius] pixels, weighted by how much of each pixel is that side, so the other side's
 * colours never bleed into it.
 *
 * A blur is smooth, so it is worked out on a copy a few times smaller and read back a row at a
 * time, blended between its pixels.
 *
 * @param alpha how much of each pixel is the person, 0–255, as [MatteEdges.fit] gives it.
 */
internal class NearbyColour(argb: IntArray, private val width: Int, height: Int, alpha: ByteArray, radius: Double, person: Boolean = false) {
    private val step = max(1, (radius / 4).toInt())
    private val w = (width + step - 1) / step
    private val h = (height + step - 1) / step
    private val r = FloatArray(w * h)
    private val g = FloatArray(w * h)
    private val b = FloatArray(w * h)
    private val weight = FloatArray(w * h)
    private val columns = GridLerp(width, step, w)
    private val rows = GridLerp(height, step, h)

    init {
        // Sum each block of the photo, weighted by how much is the side wanted.
        for (y in 0 until height) {
            val small = (y / step) * w
            for (x in 0 until width) {
                val a = alpha[y * width + x].toInt() and 0xff
                val k = if (person) a else 255 - a
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
        val box = BoxMean(w, h, max(1, (radius / step / 2).roundToInt()))
        for (plane in listOf(r, g, b, weight)) repeat(3) { box.smooth(plane) }
    }

    /** Reads the colour a row at a time. Each has its own room to work, so threads can read at once. */
    inner class Rows {
        // One row of the small copy, blended between two of its rows.
        private val lineR = FloatArray(w)
        private val lineG = FloatArray(w)
        private val lineB = FloatArray(w)
        private val lineWeight = FloatArray(w)

        /**
         * The colour along photo row [y], into [out]; where none of that side is nearby (such as
         * the background deep inside the person, who covers it anyway) the pixel is [UNKNOWN].
         */
        fun read(y: Int, out: IntArray) {
            val top = rows.low[y] * w
            val bottom = rows.high[y] * w
            val t = rows.t[y]
            for (x in 0 until w) {
                lineR[x] = r[top + x] * (1 - t) + r[bottom + x] * t
                lineG[x] = g[top + x] * (1 - t) + g[bottom + x] * t
                lineB[x] = b[top + x] * (1 - t) + b[bottom + x] * t
                lineWeight[x] = weight[top + x] * (1 - t) + weight[bottom + x] * t
            }
            for (x in 0 until width) {
                val l = columns.low[x]
                val hi = columns.high[x]
                val tx = columns.t[x]
                val k = lineWeight[l] * (1 - tx) + lineWeight[hi] * tx
                if (k < 1e-2f) {
                    out[x] = UNKNOWN
                    continue
                }
                fun channel(line: FloatArray) = ((line[l] * (1 - tx) + line[hi] * tx) / k + 0.5f).toInt().coerceIn(0, 255)
                out[x] = OPAQUE or (channel(lineR) shl 16) or (channel(lineG) shl 8) or channel(lineB)
            }
        }
    }

    companion object {
        /** None of that side nearby: fully transparent, which a colour from the photo never is. */
        const val UNKNOWN = 0
        private const val OPAQUE = 0xff shl 24
    }
}

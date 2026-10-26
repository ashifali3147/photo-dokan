package com.tlw.photodokan.core.matte

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Takes the old backdrop's light off the person near their outline. A coloured wall lights the
 * hair and the edges of the face (an orange wall leaves an orange rim), and once the wall is
 * replaced by a plain colour that rim shows. Near the edge, a pixel with more of the backdrop's
 * colour than the person has further in is made greyer until it has no more: hair, nearly neutral
 * further in, loses nearly all of it; skin, itself orange-ish, keeps its own colour and loses only
 * the glow. Brightness never changes, and a neutral backdrop changes nothing.
 */
internal object Despill {
    /** A backdrop with less colour than this (0–1) lights nothing noticeably. */
    private const val NEUTRAL = 0.03f
    private const val COLOURED = 0.1f

    /**
     * [front], the person's colour in a pixel, with the [backdrop]'s light taken off by [near]
     * (0 deep inside the person, 1 at the edge), measured against the person's colour [inside].
     */
    fun clean(front: Int, backdrop: Int, inside: Int, near: Float): Int {
        if (near <= 0f) return front
        // The backdrop's colour, as a direction away from grey.
        val br = red(backdrop)
        val bg = green(backdrop)
        val bb = blue(backdrop)
        val by = luma(br, bg, bb)
        var ur = br - by
        var ug = bg - by
        var ub = bb - by
        val size = sqrt(ur * ur + ug * ug + ub * ub)
        if (size < NEUTRAL) return front
        ur /= size
        ug /= size
        ub /= size
        val strength = near * ((size - NEUTRAL) / (COLOURED - NEUTRAL)).coerceIn(0f, 1f)

        val r = red(front)
        val g = green(front)
        val b = blue(front)
        val y = luma(r, g, b)
        fun along(cr: Float, cg: Float, cb: Float, cy: Float) = (cr - cy) * ur + (cg - cy) * ug + (cb - cy) * ub
        // As much of that colour as the person has further in, for their brightness: a shadowed ear
        // keeps its skin's warmth, while hair, near-neutral further in, has hardly any to keep.
        val ir = red(inside)
        val ig = green(inside)
        val ib = blue(inside)
        val iy = luma(ir, ig, ib)
        val own = max(0f, along(ir, ig, ib, iy))
        val allowed = if (iy <= 0f) 0f else own * min(1f, y / iy)
        val spill = along(r, g, b, y)
        if (spill <= allowed) return front
        // Less colour, not a different one: towards grey until only the allowed share of the
        // backdrop's colour is left. Taking the backdrop's colour out directly would leave dark
        // red-brown blue.
        val k = strength * (1 - allowed / spill)
        return pack(front, r + k * (y - r), g + k * (y - g), b + k * (y - b))
    }

    private fun red(c: Int) = ((c shr 16) and 0xff) / 255f
    private fun green(c: Int) = ((c shr 8) and 0xff) / 255f
    private fun blue(c: Int) = (c and 0xff) / 255f
    private fun luma(r: Float, g: Float, b: Float) = 0.2126f * r + 0.7152f * g + 0.0722f * b

    private fun pack(like: Int, r: Float, g: Float, b: Float): Int {
        fun channel(v: Float) = (v * 255 + 0.5f).toInt().coerceIn(0, 255)
        return (like and (0xff shl 24)) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }
}

/**
 * How near each pixel of the person is to their outline, 0 (deep inside) to 1 (at the edge), from
 * how much background there is within about [radius] pixels; worked out on a grid a few times
 * smaller and read back a row at a time.
 */
internal class EdgeNearness(alpha: ByteArray, private val width: Int, height: Int, radius: Double) {
    private val step = max(1, (radius / 4).toInt())
    private val w = (width + step - 1) / step
    private val h = (height + step - 1) / step
    private val grid = FloatArray(w * h)
    private val columns = GridLerp(width, step, w)
    private val rows = GridLerp(height, step, h)

    init {
        for (y in 0 until height) for (x in 0 until width) grid[(y / step) * w + x / step] += 1 - (alpha[y * width + x].toInt() and 0xff) / 255f
        for (j in grid.indices) grid[j] /= (min(step, width - (j % w) * step) * min(step, height - (j / w) * step)).toFloat()
        BoxMean(w, h, max(1, (radius / step).roundToInt())).let { box -> repeat(2) { box.smooth(grid) } }
    }

    /** Along photo row [y], into [out]. */
    fun read(y: Int, out: FloatArray) {
        val top = rows.low[y] * w
        val bottom = rows.high[y] * w
        val t = rows.t[y]
        for (x in 0 until width) {
            val l = columns.low[x]
            val hi = columns.high[x]
            val tx = columns.t[x]
            val background = (grid[top + l] * (1 - tx) + grid[top + hi] * tx) * (1 - t) + (grid[bottom + l] * (1 - tx) + grid[bottom + hi] * tx) * t
            // A quarter background nearby is right at the edge; none is deep inside.
            out[x] = (background * 4).coerceIn(0f, 1f)
        }
    }
}

package com.tlw.photodokan.core.imaging

import com.tlw.photodokan.core.face.Face
import com.tlw.photodokan.core.matte.BoxMean
import com.tlw.photodokan.core.matte.GridLerp
import com.tlw.photodokan.core.model.Portrait
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The studio look: how a studio edits a portrait before printing it, bright, clean and crisp.
 * Classical steps only; the face itself is never changed:
 *
 * 1. A tone curve set from the face lifts the midtones until the skin is well lit, while
 *    leaving the shadows alone, so hair, beard and dark clothes stay deep.
 * 2. Skin loses some of its colour cast and the backdrop's bounce (an orange wall makes faces
 *    orange); near-black hair and beard lose theirs completely.
 * 3. Clarity brings out the face's form, and sharpening the fine detail a print softens.
 *
 * Every step scales with the strength, 0 to 1, so the operator's Enhance slider blends it in.
 */
object StudioLook {
    /** How bright well-lit skin is, in the middle of the face, 0 to 1. */
    const val SKIN_TARGET = 0.56

    /** Below this brightness the curve changes nothing: shadows stay as shot. */
    private const val SHADOWS = 0.08

    private const val SKIN_CLEAN = 0.25f

    /** At full fairness, skin goes this share of the way to white, and this share of the way to grey. */
    private const val FAIR_LIFT = 0.35f
    private const val FAIR_PALE = 0.2f

    /** Skin this bright or more is left as it is by default; skin this dark or less gets [FAIR_MOST]. */
    private const val FAIR_FROM = 0.62
    private const val FAIR_FULL = 0.35
    private const val FAIR_MOST = 0.7
    private const val DARK_CLEAN = 0.7f
    private const val CLARITY = 0.25f
    private const val SHARPEN = 0.6f

    /**
     * What the look works from, measured on the [width] × [height] photo [argb] with [face] on
     * it: how bright the face's skin is and how big the face is. Null when the skin can't be seen.
     */
    fun measure(argb: IntArray, width: Int, height: Int, face: Face): Portrait? {
        val skin = AutoLight.measure(argb, width, height, face) ?: return null
        return Portrait((skin.medianLuma / 255).coerceIn(0.0, 1.0), (face.height / height).coerceIn(0.01, 1.0))
    }

    /**
     * [argb], a [width] × [height] photo, with the studio look at [strength] and its skin made
     * [fairness] fairer, each 0 to 1; each pixel keeps its alpha. [person] says how much of each
     * pixel is the person, 0–255, when known, so only their skin is changed. Without a [portrait]
     * there is no face to go by, so the curve and fair skin are left out.
     */
    fun apply(
        argb: IntArray,
        width: Int,
        height: Int,
        strength: Double,
        portrait: Portrait?,
        person: ByteArray? = null,
        fairness: Double = 0.0,
    ): IntArray {
        require(argb.size == width * height) { "Need $width × $height pixels, had ${argb.size}" }
        require(person == null || person.size == argb.size) { "The person needs a value per pixel" }
        val s = strength.coerceIn(0.0, 1.0).toFloat()
        val fair = if (portrait == null) 0f else fairness.coerceIn(0.0, 1.0).toFloat()
        if (s <= 0 && fair <= 0) return argb.copyOf()
        val curve = portrait?.takeIf { s > 0 }?.let { curve(it.skinLevel, it.skinLevel + s * (max(SKIN_TARGET, it.skinLevel) - it.skinLevel)) }

        // Light first, and how much of each pixel is skin.
        val toned = IntArray(argb.size)
        val skin = ByteArray(argb.size)
        // Fair skin goes by a smooth map of the skin, so it changes evenly across the face: no
        // speckles where the skin's texture dips in and out of skin's colours. Anything much
        // darker than the face's skin (beard, brows, hair, deep shadow) is left out of it.
        val fairMap = if (fair > 0) SmoothMap(width, height) else null
        val level = portrait?.let { curve?.at(it.skinLevel.toFloat()) ?: it.skinLevel.toFloat() } ?: 0f
        for (i in argb.indices) {
            val c = argb[i]
            var r = ((c shr 16) and 0xff) / 255f
            var g = ((c shr 8) and 0xff) / 255f
            var b = (c and 0xff) / 255f
            if (curve != null) {
                // Half on brightness, which keeps colour, half on each channel, which pales it a little.
                val y = luma(r, g, b).coerceAtLeast(1e-4f)
                val k = curve.at(y) / y
                r = 0.5f * min(r * k, 1f) + 0.5f * curve.at(r)
                g = 0.5f * min(g * k, 1f) + 0.5f * curve.at(g)
                b = 0.5f * min(b * k, 1f) + 0.5f * curve.at(b)
            }
            toned[i] = pack(c, r, g, b)
            val isSkin = skinShare(r, g, b) * (person?.let { (it[i].toInt() and 0xff) / 255f } ?: 1f)
            skin[i] = (isSkin * 255 + 0.5f).toInt().toByte()
            fairMap?.add(i, isSkin * ramp(luma(r, g, b), 0.55f * level, 0.8f * level))
        }
        fairMap?.smooth()
        val row = FloatArray(width)
        for (y in 0 until height) {
            fairMap?.row(y, row)
            for (x in 0 until width) {
                val i = y * width + x
                val c = toned[i]
                var r = ((c shr 16) and 0xff) / 255f
                var g = ((c shr 8) and 0xff) / 255f
                var b = (c and 0xff) / 255f
                if (fairMap != null) {
                    val k = fair * row[x]
                    if (k > 0) {
                        // Lighter, and a little paler, as fair skin is.
                        val l = luma(r, g, b).coerceAtLeast(1e-4f)
                        val lit = l + FAIR_LIFT * k * (1 - l)
                        val m = lit / l
                        r = min(r * m, 1f)
                        g = min(g * m, 1f)
                        b = min(b * m, 1f)
                        val pale = FAIR_PALE * k
                        r += pale * (lit - r)
                        g += pale * (lit - g)
                        b += pale * (lit - b)
                    }
                }
                if (s > 0) {
                    val l = luma(r, g, b)
                    val isSkin = (skin[i].toInt() and 0xff) / 255f
                    val clean = s * (SKIN_CLEAN * isSkin + DARK_CLEAN * darkCast(r, g, b, l))
                    r += clean * (l - r)
                    g += clean * (l - g)
                    b += clean * (l - b)
                }
                toned[i] = pack(c, r, g, b)
            }
        }
        return if (s > 0) detail(toned, width, height, s, portrait?.faceHeight, skin) else toned
    }

    /**
     * How fair to make skin of [skinLevel] (how bright the face's skin was measured) by default:
     * darker skin a good deal, as customers ask; skin already fair hardly at all.
     */
    fun fairnessFor(skinLevel: Double): Double = ((FAIR_FROM - skinLevel) / (FAIR_FROM - FAIR_FULL)).coerceIn(0.0, 1.0) * FAIR_MOST

    /**
     * A value per pixel of a [width] × [height] photo, smoothed: each pixel [add]ed to a grid a few
     * times smaller, [smooth]ed with its neighbours, then read back a row at a time.
     */
    private class SmoothMap(private val width: Int, private val height: Int) {
        private val step = max(1, height / 300)
        private val w = (width + step - 1) / step
        private val h = (height + step - 1) / step
        private val grid = FloatArray(w * h)
        private val columns = GridLerp(width, step, w)
        private val rows = GridLerp(height, step, h)

        /** Adds pixel [i]'s [value]. */
        fun add(i: Int, value: Float) {
            grid[((i / width) / step) * w + (i % width) / step] += value
        }

        /** Once every pixel is added: each grid point their average, then averaged with its neighbours. */
        fun smooth() {
            for (j in grid.indices) grid[j] /= (min(step, width - (j % w) * step) * min(step, height - (j / w) * step)).toFloat()
            BoxMean(w, h, 1).let { box -> repeat(2) { box.smooth(grid) } }
        }

        fun row(y: Int, out: FloatArray) {
            val top = rows.low[y] * w
            val bottom = rows.high[y] * w
            val t = rows.t[y]
            for (x in 0 until width) {
                val l = columns.low[x]
                val hi = columns.high[x]
                val tx = columns.t[x]
                val upper = grid[top + l] * (1 - tx) + grid[top + hi] * tx
                val lower = grid[bottom + l] * (1 - tx) + grid[bottom + hi] * tx
                out[x] = upper * (1 - t) + lower * t
            }
        }
    }

    /**
     * Clarity and sharpening on brightness alone: each pixel moved away from the average around
     * it, widely (the face's form) and closely (its detail). Skin gets less of the close kind, so
     * its texture isn't coarsened.
     */
    private fun detail(argb: IntArray, width: Int, height: Int, s: Float, faceHeight: Double?, skin: ByteArray): IntArray {
        // The wide average, on a copy a few times smaller.
        val radius = (faceHeight ?: DEFAULT_FACE) * height / 12
        val step = max(1, (radius / 4).toInt())
        val w = (width + step - 1) / step
        val h = (height + step - 1) / step
        val wide = FloatArray(w * h)
        for (y in 0 until height) for (x in 0 until width) wide[(y / step) * w + x / step] += luma(argb[y * width + x])
        for (j in wide.indices) {
            val bx = j % w
            val by = j / w
            wide[j] /= (min(step, width - bx * step) * min(step, height - by * step)).toFloat()
        }
        val box = BoxMean(w, h, max(1, (radius / step / 2).roundToInt()))
        repeat(3) { box.smooth(wide) }
        val columns = GridLerp(width, step, w)
        val rows = GridLerp(height, step, h)

        // Rows of brightness above, at and below the one being worked on, for the close average.
        var above = FloatArray(width)
        var here = FloatArray(width)
        var below = FloatArray(width)
        fun lumaRow(y: Int, into: FloatArray) {
            val row = y.coerceIn(0, height - 1) * width
            for (x in 0 until width) into[x] = luma(argb[row + x])
        }
        lumaRow(-1, above)
        lumaRow(0, here)
        val out = IntArray(argb.size)
        val line = FloatArray(w)
        for (y in 0 until height) {
            lumaRow(y + 1, below)
            val top = rows.low[y] * w
            val bottom = rows.high[y] * w
            val t = rows.t[y]
            for (x in 0 until w) line[x] = wide[top + x] * (1 - t) + wide[bottom + x] * t
            for (x in 0 until width) {
                val l = here[x]
                val far = line[columns.low[x]] * (1 - columns.t[x]) + line[columns.high[x]] * columns.t[x]
                val left = here[max(0, x - 1)]
                val right = here[min(width - 1, x + 1)]
                val near = (above[x] + below[x] + left + right + 4 * l) / 8
                val i = y * width + x
                val skinShare = (skin[i].toInt() and 0xff) / 255f
                val d = s * (CLARITY * (l - far) + SHARPEN * (1 - 0.6f * skinShare) * (l - near) * 2)
                val c = argb[i]
                out[i] = pack(c, ((c shr 16) and 0xff) / 255f + d, ((c shr 8) and 0xff) / 255f + d, (c and 0xff) / 255f + d)
            }
            val spare = above
            above = here
            here = below
            below = spare
        }
        return out
    }

    /** A face's height in an unknown portrait, as a share of the photo's height. */
    private const val DEFAULT_FACE = 0.3

    /**
     * How much a colour looks like skin, 0 to 1: red over green over blue, in the proportions
     * skin of any shade has, fading out towards lips, hair and anything not skin.
     */
    internal fun skinShare(r: Float, g: Float, b: Float): Float {
        if (g <= 0.02f || luma(r, g, b) < 0.06f) return 0f
        val rg = r / g
        val bg = b / g
        return ramp(rg, 1.04f, 1.12f) * (1 - ramp(rg, 1.9f, 2.4f)) * ramp(bg, 0.3f, 0.42f) * (1 - ramp(bg, 0.98f, 1.08f))
    }

    /**
     * How much a colour is near-black hair or beard tinted by the light: dark and warm, but not
     * strongly coloured like maroon clothes.
     */
    internal fun darkCast(r: Float, g: Float, b: Float, y: Float): Float {
        if (r < b) return 0f // navy, dark blue: clothes, not a cast
        val dark = ((0.3f - y) / 0.2f).coerceIn(0f, 1f)
        val top = max(r, max(g, b))
        val colour = if (top <= 0f) 0f else (top - min(r, min(g, b))) / top
        return dark * ((0.8f - colour) / 0.25f).coerceIn(0f, 1f)
    }

    /** 0 below [low], 1 above [high], smooth between. */
    private fun ramp(v: Float, low: Float, high: Float): Float {
        val t = ((v - low) / (high - low)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    private fun luma(r: Float, g: Float, b: Float) = 0.2126f * r + 0.7152f * g + 0.0722f * b
    private fun luma(c: Int) = luma(((c shr 16) and 0xff) / 255f, ((c shr 8) and 0xff) / 255f, (c and 0xff) / 255f)

    /** [r], [g] and [b] with the alpha of [like], so a cut-out person stays cut out. */
    private fun pack(like: Int, r: Float, g: Float, b: Float): Int {
        fun channel(v: Float) = (v * 255 + 0.5f).toInt().coerceIn(0, 255)
        return (like and (0xff shl 24)) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    /**
     * The tone curve that takes [from] to [to] and leaves the shadows, below [SHADOWS], and pure
     * white as they are: a smooth, always-rising curve (Fritsch–Carlson), looked up from a table.
     */
    internal fun curve(from: Double, to: Double): Curve {
        val f = from.coerceIn(SHADOWS + 0.02, 0.98)
        val xs = doubleArrayOf(0.0, SHADOWS, f, 1.0)
        val ys = doubleArrayOf(0.0, SHADOWS, to.coerceIn(f, 0.99), 1.0)
        val slopes = DoubleArray(3) { (ys[it + 1] - ys[it]) / (xs[it + 1] - xs[it]) }
        // Straight through the shadows: slope 1 at both of their ends leaves them exactly as shot.
        val tangents = DoubleArray(4) { k ->
            when (k) {
                0, 1 -> 1.0
                3 -> slopes[2]
                else -> {
                    val a = slopes[k - 1]
                    val b = slopes[k]
                    if (a * b <= 0) 0.0 else 3 * (a + b) / ((2 * b + a) / a + (b + 2 * a) / b)
                }
            }
        }
        return Curve(FloatArray(Curve.SIZE) { i ->
            val x = i / (Curve.SIZE - 1.0)
            val k = when {
                x < xs[1] -> 0
                x < xs[2] -> 1
                else -> 2
            }
            val span = xs[k + 1] - xs[k]
            val t = (x - xs[k]) / span
            val t2 = t * t
            val t3 = t2 * t
            ((2 * t3 - 3 * t2 + 1) * ys[k] + (t3 - 2 * t2 + t) * span * tangents[k] + (-2 * t3 + 3 * t2) * ys[k + 1] + (t3 - t2) * span * tangents[k + 1]).toFloat()
        })
    }

    /** A tone curve from 0–1 to 0–1, as a table. */
    internal class Curve(private val table: FloatArray) {
        fun at(v: Float): Float = table[(v.coerceIn(0f, 1f) * (SIZE - 1) + 0.5f).toInt()]

        companion object {
            const val SIZE = 1024
        }
    }
}

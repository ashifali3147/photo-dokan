package com.tlw.photodokan.core.matte

import kotlin.math.max
import kotlin.math.min

/**
 * Fits a matte's edges to the photo. The matte is worked out on a small copy of the photo, so
 * scaled up to a full-size photo its edges are a soft blur a dozen pixels wide, and the old backdrop
 * shows through it around the hair. A guided filter (He, Sun and Tang) lets the photo itself say
 * where the edge is: near an edge, the matte is matched to the photo's colours there, so it follows
 * strands of hair and the outline of the shoulders at the photo's full sharpness. Away from an
 * edge, where the matte is all person or all background, it is left as it is.
 *
 * The matching is worked out on a grid about the size of the matte and read back a row at a time,
 * so a full-size photo needs little memory.
 */
internal object MatteEdges {
    /** How far around each point the photo's colours are compared, in grid points. */
    private const val RADIUS = 2

    /**
     * How closely the matte follows the photo's colours: smaller follows finer detail, and noise
     * with it. Colours run 0–1, so this is about a 3% difference in colour.
     */
    private const val SMOOTHING = 1e-3f

    /**
     * How sure the matte must be to be left alone: fitted in full from 15% to 85% person, and
     * less and less towards all person or all background.
     */
    private const val UNSURE = 8f

    /** One value 0–255 per pixel of the [width] × [height] photo [argb]: how much of it is the person. */
    fun fit(argb: IntArray, width: Int, height: Int, matte: Matte): ByteArray {
        val step = max(1, min(width / matte.width, height / matte.height))
        val w = (width + step - 1) / step
        val h = (height + step - 1) / step
        val n = w * h

        // The photo on the grid, each point the average of its block; and the matte at each point.
        val r = FloatArray(n)
        val g = FloatArray(n)
        val b = FloatArray(n)
        for (y in 0 until height) {
            val row = (y / step) * w
            for (x in 0 until width) {
                val c = argb[y * width + x]
                val j = row + x / step
                r[j] += ((c shr 16) and 0xff) / 255f
                g[j] += ((c shr 8) and 0xff) / 255f
                b[j] += (c and 0xff) / 255f
            }
        }
        val p = FloatArray(n)
        for (j in 0 until n) {
            val x = j % w
            val y = j / w
            val pixels = (min(step, width - x * step) * min(step, height - y * step)).toFloat()
            r[j] /= pixels
            g[j] /= pixels
            b[j] /= pixels
            p[j] = matte.at(min((x + 0.5) * step, width.toDouble()) / width, min((y + 0.5) * step, height.toDouble()) / height).toFloat()
        }

        // Local averages of the colours, the matte, and their products, each worked out in place.
        val rp = FloatArray(n) { r[it] * p[it] }
        val gp = FloatArray(n) { g[it] * p[it] }
        val bp = FloatArray(n) { b[it] * p[it] }
        val rr = FloatArray(n) { r[it] * r[it] }
        val rg = FloatArray(n) { r[it] * g[it] }
        val rb = FloatArray(n) { r[it] * b[it] }
        val gg = FloatArray(n) { g[it] * g[it] }
        val gb = FloatArray(n) { g[it] * b[it] }
        val bb = FloatArray(n) { b[it] * b[it] }
        val mp = p.copyOf()
        val box = BoxMean(w, h, RADIUS)
        for (plane in arrayOf(r, g, b, mp, rp, gp, bp, rr, rg, rb, gg, gb, bb)) box.smooth(plane)

        // Around each point, the matte as a straight-line mix of the colours: a·colour + offset,
        // written over the averages of colour × matte (a) and of the matte (offset).
        val ar = rp
        val ag = gp
        val ab = bp
        val offset = mp
        for (j in 0 until n) {
            val cr = rp[j] - r[j] * mp[j]
            val cg = gp[j] - g[j] * mp[j]
            val cb = bp[j] - b[j] * mp[j]
            if (cr == 0f && cg == 0f && cb == 0f) {
                ar[j] = 0f // all person or all background: nothing to match, the offset is the matte
                ag[j] = 0f
                ab[j] = 0f
                continue
            }
            // The colours' spread around here, and its inverse (a symmetric 3 × 3 matrix).
            val srr = rr[j] - r[j] * r[j] + SMOOTHING
            val srg = rg[j] - r[j] * g[j]
            val srb = rb[j] - r[j] * b[j]
            val sgg = gg[j] - g[j] * g[j] + SMOOTHING
            val sgb = gb[j] - g[j] * b[j]
            val sbb = bb[j] - b[j] * b[j] + SMOOTHING
            val irr = sgg * sbb - sgb * sgb
            val irg = srb * sgb - srg * sbb
            val irb = srg * sgb - srb * sgg
            val igg = srr * sbb - srb * srb
            val igb = srb * srg - srr * sgb
            val ibb = srr * sgg - srg * srg
            val det = srr * irr + srg * irg + srb * irb
            ar[j] = (irr * cr + irg * cg + irb * cb) / det
            ag[j] = (irg * cr + igg * cg + igb * cb) / det
            ab[j] = (irb * cr + igb * cg + ibb * cb) / det
            offset[j] = mp[j] - ar[j] * r[j] - ag[j] * g[j] - ab[j] * b[j]
        }
        for (plane in arrayOf(ar, ag, ab, offset)) box.smooth(plane)

        // Back to the full photo: each pixel's own colour through the mix around it.
        val out = ByteArray(width * height)
        val columns = GridLerp(width, step, w)
        val rows = GridLerp(height, step, h)
        val lr = FloatArray(w)
        val lg = FloatArray(w)
        val lb = FloatArray(w)
        val lo = FloatArray(w)
        val lp = FloatArray(w)
        for (y in 0 until height) {
            val top = rows.low[y] * w
            val bottom = rows.high[y] * w
            val t = rows.t[y]
            for (x in 0 until w) {
                lr[x] = ar[top + x] * (1 - t) + ar[bottom + x] * t
                lg[x] = ag[top + x] * (1 - t) + ag[bottom + x] * t
                lb[x] = ab[top + x] * (1 - t) + ab[bottom + x] * t
                lo[x] = offset[top + x] * (1 - t) + offset[bottom + x] * t
                lp[x] = p[top + x] * (1 - t) + p[bottom + x] * t
            }
            val row = y * width
            for (x in 0 until width) {
                val l = columns.low[x]
                val hi = columns.high[x]
                val tx = columns.t[x]
                val c = argb[row + x]
                val q = (lr[l] * (1 - tx) + lr[hi] * tx) * (((c shr 16) and 0xff) / 255f) +
                    (lg[l] * (1 - tx) + lg[hi] * tx) * (((c shr 8) and 0xff) / 255f) +
                    (lb[l] * (1 - tx) + lb[hi] * tx) * ((c and 0xff) / 255f) +
                    (lo[l] * (1 - tx) + lo[hi] * tx)
                // Only where the matte isn't sure: elsewhere the photo's own patterns mustn't move it.
                val before = lp[l] * (1 - tx) + lp[hi] * tx
                val unsure = min(1f, UNSURE * before * (1 - before))
                out[row + x] = ((before + unsure * (q - before)) * 255 + 0.5f).toInt().coerceIn(0, 255).toByte()
            }
        }
        return out
    }
}

/** For each of [n] photo pixels, the two of [size] grid points (each [step] pixels) around it and how far between. */
internal class GridLerp(n: Int, step: Int, size: Int) {
    val low = IntArray(n)
    val high = IntArray(n)
    val t = FloatArray(n)

    init {
        for (i in 0 until n) {
            val p = ((i + 0.5) / step - 0.5).coerceIn(0.0, size - 1.0)
            low[i] = p.toInt()
            high[i] = min(low[i] + 1, size - 1)
            t[i] = (p - low[i]).toFloat()
        }
    }
}

/** Replaces each value of a [w] × [h] grid by the average of it and its neighbours within [radius]; edges repeat. */
internal class BoxMean(private val w: Int, private val h: Int, private val radius: Int) {
    private val line = FloatArray(max(w, h))

    fun smooth(values: FloatArray) {
        for (y in 0 until h) {
            for (x in 0 until w) line[x] = values[y * w + x]
            mean(w) { x, v -> values[y * w + x] = v }
        }
        for (x in 0 until w) {
            for (y in 0 until h) line[y] = values[y * w + x]
            mean(h) { y, v -> values[y * w + x] = v }
        }
    }

    private inline fun mean(n: Int, put: (Int, Float) -> Unit) {
        val span = 2 * radius + 1
        var sum = 0f
        for (i in -radius..radius) sum += line[i.coerceIn(0, n - 1)]
        for (i in 0 until n) {
            put(i, sum / span)
            sum += line[(i + radius + 1).coerceAtMost(n - 1)] - line[(i - radius).coerceAtLeast(0)]
        }
    }
}

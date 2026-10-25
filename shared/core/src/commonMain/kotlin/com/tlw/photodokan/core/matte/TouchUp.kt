package com.tlw.photodokan.core.matte

import com.tlw.photodokan.core.model.BrushMode
import com.tlw.photodokan.core.model.MaskStroke
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * This matte with the operator's touch-up [strokes] painted on, in order: KEEP makes what the
 * brush covers the person, ERASE makes it background. The brush has a soft rim, so painted edges
 * blend in and are then fitted to the photo with the rest of the matte's edges ([MatteEdges]).
 *
 * @param aspect the photo's width over its height, so a round brush stays round on a matte whose
 *   shape is a little different from the photo's.
 */
fun Matte.touchedUp(strokes: List<MaskStroke>, aspect: Double): Matte {
    if (strokes.isEmpty()) return this
    require(aspect > 0) { "Need the photo's shape, was $aspect" }
    val out = alpha.copyOf()
    for (stroke in strokes) paint(out, stroke, aspect)
    return Matte(width, height, out)
}

/** How much of the brush's radius is its soft rim. */
private const val SOFT_RIM = 0.3

/**
 * Paints one [stroke] into [alpha]. Distances are measured in photo heights: x as a fraction of
 * the width times [aspect], y as a fraction of the height.
 */
private fun Matte.paint(alpha: ByteArray, stroke: MaskStroke, aspect: Double) {
    val r = stroke.radius
    val xs = DoubleArray(stroke.points.size) { stroke.points[it].x * aspect }
    val ys = DoubleArray(stroke.points.size) { stroke.points[it].y }
    // Only the matte pixels the brush can reach.
    val left = max(0, floor(((xs.min() - r) / aspect) * width - 0.5).toInt())
    val right = min(width - 1, ceil(((xs.max() + r) / aspect) * width - 0.5).toInt())
    val top = max(0, floor((ys.min() - r) * height - 0.5).toInt())
    val bottom = min(height - 1, ceil((ys.max() + r) * height - 0.5).toInt())
    for (j in top..bottom) {
        val py = (j + 0.5) / height
        for (i in left..right) {
            val px = (i + 0.5) / width * aspect
            var nearest = Double.MAX_VALUE
            for (k in xs.indices) {
                val d = if (k == 0) distance(px, py, xs[0], ys[0], xs[0], ys[0]) else distance(px, py, xs[k - 1], ys[k - 1], xs[k], ys[k])
                if (d < nearest) nearest = d
            }
            if (nearest >= r) continue
            // Full strength inside, fading to nothing across the rim.
            val cover = ((r - nearest) / (SOFT_RIM * r)).coerceAtMost(1.0)
            val k = (cover * 255 + 0.5).toInt()
            val index = j * width + i
            val now = alpha[index].toInt() and 0xff
            alpha[index] = when (stroke.mode) {
                BrushMode.KEEP -> max(now, k)
                BrushMode.ERASE -> min(now, 255 - k)
            }.toByte()
        }
    }
}

/** How far ([px], [py]) is from the segment ([ax], [ay])–([bx], [by]). */
private fun distance(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
    val dx = bx - ax
    val dy = by - ay
    val length = dx * dx + dy * dy
    val t = if (length == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / length).coerceIn(0.0, 1.0)
    val x = ax + t * dx - px
    val y = ay + t * dy - py
    return sqrt(x * x + y * y)
}

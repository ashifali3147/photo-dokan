package com.tlw.photodokan.vision

import kotlin.math.max
import kotlin.math.min

/**
 * Resizes a [width] × [height] ARGB photo to [outW] × [outH] for a model, handing each output
 * pixel's index and colour (0–255) to [put]. When shrinking, each output pixel is the average of
 * the photo pixels it covers, so small details don't flicker in or out; when enlarging, the nearest.
 */
internal inline fun shrink(argb: IntArray, width: Int, height: Int, outW: Int, outH: Int, put: (i: Int, r: Float, g: Float, b: Float) -> Unit) {
    require(width > 0 && height > 0 && argb.size >= width * height) { "Need $width × $height pixels" }
    val sx = width.toDouble() / outW
    val sy = height.toDouble() / outH
    for (ty in 0 until outH) {
        val y0 = min(height - 1, (ty * sy).toInt())
        val y1 = max(y0 + 1, min(height, ((ty + 1) * sy).toInt()))
        for (tx in 0 until outW) {
            val x0 = min(width - 1, (tx * sx).toInt())
            val x1 = max(x0 + 1, min(width, ((tx + 1) * sx).toInt()))
            var r = 0L
            var g = 0L
            var b = 0L
            for (y in y0 until y1) {
                val row = y * width
                for (x in x0 until x1) {
                    val c = argb[row + x]
                    r += (c shr 16) and 0xff
                    g += (c shr 8) and 0xff
                    b += c and 0xff
                }
            }
            val n = ((y1 - y0) * (x1 - x0)).toFloat()
            put(ty * outW + tx, r / n, g / n, b / n)
        }
    }
}

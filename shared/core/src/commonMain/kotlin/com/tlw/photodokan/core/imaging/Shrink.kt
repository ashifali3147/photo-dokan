package com.tlw.photodokan.core.imaging

/**
 * A smaller copy of a photo for quick work, e.g. showing a slider's effect while it is dragged:
 * each block of [factor] × [factor] pixels becomes one, the average of its colours. Pixels past
 * the last whole block on the right or bottom are left out.
 */
object Shrink {
    /** The block size that brings the longer side of [width] × [height] to [longSide] or just under. */
    fun factorFor(width: Int, height: Int, longSide: Int): Int {
        require(longSide > 0) { "Need a size, was $longSide" }
        val longest = maxOf(width, height)
        return maxOf(1, (longest + longSide - 1) / longSide)
    }

    /** The [width] × [height] ARGB photo, row by row, shrunk [factor] times: see [Shrink]. */
    fun box(argb: IntArray, width: Int, height: Int, factor: Int): Shrunk {
        require(width > 0 && height > 0 && argb.size == width * height) { "Need $width × $height pixels, had ${argb.size}" }
        require(factor in 1..minOf(width, height)) { "Can't shrink $width × $height by $factor" }
        if (factor == 1) return Shrunk(argb.copyOf(), width, height)
        val w = width / factor
        val h = height / factor
        val out = IntArray(w * h)
        val n = factor * factor
        for (y in 0 until h) {
            for (x in 0 until w) {
                var a = 0
                var r = 0
                var g = 0
                var b = 0
                for (dy in 0 until factor) {
                    val row = (y * factor + dy) * width + x * factor
                    for (dx in 0 until factor) {
                        val p = argb[row + dx]
                        a += p ushr 24
                        r += (p shr 16) and 0xff
                        g += (p shr 8) and 0xff
                        b += p and 0xff
                    }
                }
                out[y * w + x] = ((a + n / 2) / n shl 24) or ((r + n / 2) / n shl 16) or ((g + n / 2) / n shl 8) or ((b + n / 2) / n)
            }
        }
        return Shrunk(out, w, h)
    }
}

/** A photo's pixels, row by row, and its size. */
class Shrunk(val argb: IntArray, val width: Int, val height: Int)

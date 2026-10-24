package com.tlw.photodokan.vision

import com.tlw.photodokan.core.matte.Matte
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The MODNet portrait matting model's input and output: an RGB image scaled to -1…1 with its
 * shorter side about [SHORT_SIDE] and both sides a multiple of 32, and a matte the same size.
 */
object ModNet {
    const val SHORT_SIDE = 512
    private const val MULTIPLE = 32

    /** The model's input: the photo resized to [width] × [height], red, green and blue planes. */
    class Input(val pixels: FloatArray, val width: Int, val height: Int)

    /** The size a [width] × [height] photo is resized to for the model. */
    fun inputSize(width: Int, height: Int): Pair<Int, Int> {
        val scale = SHORT_SIDE.toDouble() / minOf(width, height)
        fun side(n: Int) = max(MULTIPLE, (n * scale).toInt() / MULTIPLE * MULTIPLE)
        return side(width) to side(height)
    }

    fun input(argb: IntArray, width: Int, height: Int): Input {
        val (w, h) = inputSize(width, height)
        val plane = w * h
        val pixels = FloatArray(3 * plane)
        shrink(argb, width, height, w, h) { i, r, g, b ->
            pixels[i] = r / 127.5f - 1
            pixels[plane + i] = g / 127.5f - 1
            pixels[2 * plane + i] = b / 127.5f - 1
        }
        return Input(pixels, w, h)
    }

    /** The model's output for a [width] × [height] input, 0 to 1 per pixel, as a matte. */
    fun matte(output: FloatArray, width: Int, height: Int): Matte =
        Matte(width, height, ByteArray(width * height) { i -> (output[i].coerceIn(0f, 1f) * 255).roundToInt().toByte() })
}

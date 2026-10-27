package com.tlw.photodokan.core.imaging

import com.tlw.photodokan.core.model.EditRecipe
import kotlin.math.pow

/**
 * The recipe's light and colour adjustments as one 4 × 5 colour matrix (row-major, offsets in
 * 0..255), the layout Compose and Android use. Drawing through it is instant at any photo size,
 * and the same matrix is used for the screen, final.jpg and printing.
 *
 * Gentle studio corrections, applied in order: warmth and tint, exposure, colour, contrast. Black
 * and white takes the colour out instead of the colour slider; the plain background and the border,
 * which the sliders leave as chosen, are turned to their own grey by [grey].
 */
object ColorAdjustment {
    /** ±1 stop of light at the slider ends. */
    const val EXPOSURE_STOPS = 1.0
    /** Contrast ×0.5 … ×1.5 around mid-grey. */
    private const val CONTRAST_RANGE = 0.5
    /** Red up and blue down by up to 12 %. */
    const val WARMTH_RANGE = 0.12
    /** Green down and red and blue up (pinker), or the other way, by up to 8 % each. */
    const val TINT_RANGE = 0.08
    private const val MID_GREY = 128.0

    // Rec. 709 luma: how bright each channel looks to the eye.
    private const val LUMA_R = 0.2126
    private const val LUMA_G = 0.7152
    private const val LUMA_B = 0.0722

    val IDENTITY: FloatArray = Affine.identity().toColorMatrix()

    fun isNeutral(recipe: EditRecipe): Boolean =
        recipe.exposure == 0.0 && recipe.contrast == 0.0 && recipe.warmth == 0.0 && recipe.tint == 0.0 && recipe.saturation == 0.0 && !recipe.blackAndWhite

    fun matrix(recipe: EditRecipe): FloatArray {
        val balance = gains(recipe.warmth, recipe.tint)
        val warmth = Affine.scale(balance[0], balance[1], balance[2])
        val gain = 2.0.pow(EXPOSURE_STOPS * recipe.exposure)
        val exposure = Affine.scale(gain, gain, gain)
        val saturation = saturation(if (recipe.blackAndWhite) 0.0 else 1 + recipe.saturation)
        val k = 1 + CONTRAST_RANGE * recipe.contrast
        val contrast = Affine.scale(k, k, k).withOffset(MID_GREY * (1 - k))
        return (contrast * saturation * exposure * warmth).toColorMatrix()
    }

    /**
     * The red, green and blue gains of [warmth] and [tint]. Tint keeps a grey's brightness, so
     * moving it changes only the colour.
     */
    fun gains(warmth: Double, tint: Double): DoubleArray {
        val pink = 1 + TINT_RANGE * tint
        val green = 1 - TINT_RANGE * tint
        val keep = 1 / ((LUMA_R + LUMA_B) * pink + LUMA_G * green)
        return doubleArrayOf((1 + WARMTH_RANGE * warmth) * pink * keep, green * keep, (1 - WARMTH_RANGE * warmth) * pink * keep)
    }

    /** The opaque ARGB [argb] as the grey of the same brightness, as black and white shows it. */
    fun grey(argb: Long): Long {
        val y = (LUMA_R * ((argb shr 16) and 0xff) + LUMA_G * ((argb shr 8) and 0xff) + LUMA_B * (argb and 0xff) + 0.5).toLong().coerceIn(0, 255)
        return 0xFF000000 or (y shl 16) or (y shl 8) or y
    }

    /**
     * One pixel's 0..255 [rgb] through a [matrix] from [matrix], clamped as a renderer does. The
     * reference the platform renderers are checked against.
     */
    fun applyTo(matrix: FloatArray, rgb: DoubleArray): DoubleArray = DoubleArray(3) { row ->
        (matrix[row * 5] * rgb[0] + matrix[row * 5 + 1] * rgb[1] + matrix[row * 5 + 2] * rgb[2] + matrix[row * 5 + 4]).coerceIn(0.0, 255.0)
    }

    /** Mixes each channel with the pixel's grey: 0 is black and white, 1 unchanged, 2 doubled. */
    private fun saturation(s: Double): Affine {
        val r = (1 - s) * LUMA_R
        val g = (1 - s) * LUMA_G
        val b = (1 - s) * LUMA_B
        return Affine(
            doubleArrayOf(
                r + s, g, b,
                r, g + s, b,
                r, g, b + s,
            ),
            doubleArrayOf(0.0, 0.0, 0.0),
        )
    }

    /** out = m · rgb + offset, on 0..255 values; alpha passes through. */
    private class Affine(val m: DoubleArray, val offset: DoubleArray) {
        operator fun times(other: Affine): Affine {
            val product = DoubleArray(9) { i -> (0 until 3).sumOf { k -> m[i / 3 * 3 + k] * other.m[k * 3 + i % 3] } }
            val shifted = DoubleArray(3) { row -> (0 until 3).sumOf { k -> m[row * 3 + k] * other.offset[k] } + offset[row] }
            return Affine(product, shifted)
        }

        fun withOffset(value: Double) = Affine(m, doubleArrayOf(value, value, value))

        fun toColorMatrix(): FloatArray = floatArrayOf(
            m[0].toFloat(), m[1].toFloat(), m[2].toFloat(), 0f, offset[0].toFloat(),
            m[3].toFloat(), m[4].toFloat(), m[5].toFloat(), 0f, offset[1].toFloat(),
            m[6].toFloat(), m[7].toFloat(), m[8].toFloat(), 0f, offset[2].toFloat(),
            0f, 0f, 0f, 1f, 0f,
        )

        companion object {
            fun identity() = scale(1.0, 1.0, 1.0)
            fun scale(r: Double, g: Double, b: Double) =
                Affine(doubleArrayOf(r, 0.0, 0.0, 0.0, g, 0.0, 0.0, 0.0, b), doubleArrayOf(0.0, 0.0, 0.0))
        }
    }
}

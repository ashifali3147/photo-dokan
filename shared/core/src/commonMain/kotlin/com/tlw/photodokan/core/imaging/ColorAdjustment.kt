package com.tlw.photodokan.core.imaging

import com.tlw.photodokan.core.model.EditRecipe
import kotlin.math.pow

/**
 * The recipe's light and colour adjustments as one 4 × 5 colour matrix (row-major, offsets in
 * 0..255), the layout Compose and Android use. Drawing through it is instant at any photo size,
 * and the same matrix is used for the screen, final.jpg and printing.
 *
 * Gentle studio corrections, applied in order: warmth, exposure, colour, contrast.
 */
object ColorAdjustment {
    /** ±1 stop of light at the slider ends. */
    private const val EXPOSURE_STOPS = 1.0
    /** Contrast ×0.5 … ×1.5 around mid-grey. */
    private const val CONTRAST_RANGE = 0.5
    /** Red up and blue down by up to 12 %. */
    private const val WARMTH_RANGE = 0.12
    private const val MID_GREY = 128.0

    // Rec. 709 luma: how bright each channel looks to the eye.
    private const val LUMA_R = 0.2126
    private const val LUMA_G = 0.7152
    private const val LUMA_B = 0.0722

    val IDENTITY: FloatArray = Affine.identity().toColorMatrix()

    fun isNeutral(recipe: EditRecipe): Boolean =
        recipe.exposure == 0.0 && recipe.contrast == 0.0 && recipe.warmth == 0.0 && recipe.saturation == 0.0

    fun matrix(recipe: EditRecipe): FloatArray {
        val warmth = Affine.scale(1 + WARMTH_RANGE * recipe.warmth, 1.0, 1 - WARMTH_RANGE * recipe.warmth)
        val gain = 2.0.pow(EXPOSURE_STOPS * recipe.exposure)
        val exposure = Affine.scale(gain, gain, gain)
        val saturation = saturation(1 + recipe.saturation)
        val k = 1 + CONTRAST_RANGE * recipe.contrast
        val contrast = Affine.scale(k, k, k).withOffset(MID_GREY * (1 - k))
        return (contrast * saturation * exposure * warmth).toColorMatrix()
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

package com.tlw.photodokan.core.imaging

import com.tlw.photodokan.core.face.Face
import com.tlw.photodokan.core.model.EditRecipe
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Suggests the light and colour sliders for a portrait from the skin of its face: white balance
 * that brings the skin back to a natural skin colour, and brightness that lights the face well.
 *
 * The colour is judged on the skin alone. A grey-world balance would see the studio's blue
 * backdrop and push the whole photo orange. Skin of every shade has nearly the same balance of
 * green against red and blue, so a green or pink cast shows plainly and is taken out. Fair and
 * dark skin differ in warmth the way warm and cool light do, though, so warmth is changed only
 * when the skin is warmer or cooler than any skin's own colour, and then only back to the edge of
 * that range. Only the face's highlights set the brightness, so dark skin stays dark.
 *
 * Colours are measured as w = (ln(r/g) − ln(b/g)) / 2, warm against cool, and t = (ln(r/g) +
 * ln(b/g)) / 2, pink against green.
 */
object AutoLight {
    /** Skin's own warmth, w: from the coolest to the warmest skin under neutral light. */
    private const val WARM_LOW = 0.15
    private const val WARM_HIGH = 0.45

    /** Skin's own pink against green, t, under neutral light. */
    private const val TINT_LOW = 0.03
    private const val TINT_HIGH = 0.07

    /** Where the bright part of the face should be: the 90th centile of its brightness, 0–255. */
    private const val HIGHLIGHT = 182.0

    /** How far off the face's light must be, in stops, before it is changed. */
    private const val SMALL_STOPS = 0.15

    /** What [suggest] would change: slider values, each −1..1. */
    data class Suggestion(val exposure: Double, val warmth: Double, val tint: Double) {
        /** [recipe] with these light and colour values; contrast and colour are left as they are. */
        fun applyTo(recipe: EditRecipe) = recipe.copy(exposure = exposure, warmth = warmth, tint = tint)
    }

    /**
     * The skin of a face, measured: how bright its middle and highlights are, and its median hue
     * as ln(red / green) and ln(blue / green), or null when glare and shadow hide it.
     */
    data class Skin(val medianLuma: Double, val highLuma: Double, val redGreen: Double?, val blueGreen: Double?)

    /**
     * Light and colour for the [width] × [height] photo [argb] with [face] on it, or null when
     * too little of the face's skin can be seen to judge (a face too small, or mostly hidden).
     */
    fun suggest(argb: IntArray, width: Int, height: Int, face: Face): Suggestion? {
        val skin = measure(argb, width, height, face) ?: return null
        var warmth = 0.0
        var tint = 0.0
        var red = 1.0
        var blue = 1.0
        if (skin.redGreen != null && skin.blueGreen != null) {
            val dw = intoRange((skin.redGreen - skin.blueGreen) / 2, WARM_LOW, WARM_HIGH)
            val dt = intoRange((skin.redGreen + skin.blueGreen) / 2, TINT_LOW, TINT_HIGH)
            sliders(dw + dt, dt - dw).let { warmth = it.first; tint = it.second }
            red = exp(skin.redGreen)
            blue = exp(skin.blueGreen)
        }

        // The face's highlights after the colour change, from the gains the sliders give.
        val gains = ColorAdjustment.gains(warmth, tint)
        val luma = skin.highLuma * (LUMA_R * gains[0] * red + LUMA_G * gains[1] + LUMA_B * gains[2] * blue) / (LUMA_R * red + LUMA_G + LUMA_B * blue)
        val stops = ln(HIGHLIGHT / max(luma, 1.0)) / ln(2.0)
        val exposure = if (abs(stops) < SMALL_STOPS) 0.0 else (stops / ColorAdjustment.EXPOSURE_STOPS).coerceIn(-1.0, 1.0)
        return Suggestion(round(exposure), round(warmth), round(tint))
    }

    /** How far [value] must move to be within [low]..[high]. */
    private fun intoRange(value: Double, low: Double, high: Double) = when {
        value < low -> low - value
        value > high -> high - value
        else -> 0.0
    }

    /**
     * The skin between the eyes and the mouth: cheeks and nose, away from hair, eyebrows and beard.
     * Shadows, glare and anything far from skin's colours are left out.
     */
    fun measure(argb: IntArray, width: Int, height: Int, face: Face): Skin? {
        val eyeY = (face.eyeLeft.y + face.eyeRight.y) / 2
        val mouthY = (face.mouthLeft.y + face.mouthRight.y) / 2
        val drop = mouthY - eyeY
        if (drop <= 0) return null
        val left = max(0, (min(face.eyeLeft.x, face.mouthLeft.x)).roundToInt())
        val right = min(width - 1, (max(face.eyeRight.x, face.mouthRight.x)).roundToInt())
        val top = max(0, (eyeY + 0.25 * drop).roundToInt())
        val bottom = min(height - 1, (mouthY - 0.15 * drop).roundToInt())
        if (right - left < 4 || bottom - top < 4) return null

        val reds = ArrayList<Double>()
        val blues = ArrayList<Double>()
        val lumas = ArrayList<Double>()
        val glare = ArrayList<Double>()
        for (y in top..bottom) for (x in left..right) {
            val c = argb[y * width + x]
            val r = (c shr 16) and 0xff
            val g = (c shr 8) and 0xff
            val b = c and 0xff
            // Glare counts towards the face's brightness, but neither it nor deep shadow shows the
            // skin's colour.
            val luma = LUMA_R * r + LUMA_G * g + LUMA_B * b
            if (max(r, max(g, b)) > 250) {
                glare += luma
                continue
            }
            if (min(r, min(g, b)) < 8) continue
            val u = ln(r.toDouble() / g)
            val v = ln(b.toDouble() / g)
            // Lips, eyes, green or blue things: far outside any skin, however cast.
            if (u !in -0.2..0.9 || v !in -0.9..0.3) continue
            reds += u
            blues += v
            lumas += luma
        }
        val sorted = (lumas + glare).sorted()
        if (sorted.size < MIN_PIXELS) return null
        if (reds.size < MIN_PIXELS) return Skin(sorted[sorted.size / 2], sorted[sorted.size * 9 / 10], null, null)
        // The colour from the lit skin only: shadows beside the nose and under the cheekbones take
        // colour from what is around them.
        val lit = lumas.sorted()[lumas.size / 4]
        val litReds = reds.filterIndexed { i, _ -> lumas[i] >= lit }.sorted()
        val litBlues = blues.filterIndexed { i, _ -> lumas[i] >= lit }.sorted()
        return Skin(sorted[sorted.size / 2], sorted[sorted.size * 9 / 10], litReds[litReds.size / 2], litBlues[litBlues.size / 2])
    }

    /** Warmth and tint whose gains change red/green and blue/green by e^[du] and e^[dv]. */
    internal fun sliders(du: Double, dv: Double): Pair<Double, Double> {
        val redGreen = exp(du)
        val blueGreen = exp(dv)
        // Warmth sets red against blue; tint sets the two of them against green.
        val warmth = ((redGreen - blueGreen) / (redGreen + blueGreen)) / ColorAdjustment.WARMTH_RANGE
        val both = (redGreen + blueGreen) / 2 // (1 + k·t) / (1 − k·t)
        val tint = ((both - 1) / (both + 1)) / ColorAdjustment.TINT_RANGE
        return warmth.coerceIn(-1.0, 1.0) to tint.coerceIn(-1.0, 1.0)
    }

    /** Slider values in whole steps of the 0–100 scale the operator sees. */
    private fun round(value: Double) = (value * 100).roundToInt() / 100.0

    private const val MIN_PIXELS = 200
    private const val LUMA_R = 0.2126
    private const val LUMA_G = 0.7152
    private const val LUMA_B = 0.0722
}

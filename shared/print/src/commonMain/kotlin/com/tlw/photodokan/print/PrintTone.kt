package com.tlw.photodokan.print

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Makes up for a printer that prints darker and flatter than the screen. Photos are brightened
 * by [brightness] steps and given more colour by [colour] steps only as they go to the printer,
 * so the screen and final.jpg stay true.
 *
 * Brightness lifts the midtones and leaves black and white where they are, so nothing burns out.
 * Colour mixes each pixel away from its grey, keeping its brightness.
 */
data class PrintTone(val brightness: Int = 0, val colour: Int = 0) {
    init {
        require(brightness in 0..MAX_BRIGHTNESS) { "Brightness out of range: $brightness" }
        require(colour in 0..MAX_COLOUR) { "Colour out of range: $colour" }
    }

    val isNone: Boolean get() = this == NONE

    /** Each channel's 0..255 value after the brightness curve. */
    private val curve: IntArray by lazy {
        val gamma = 1 + GAMMA_PER_STEP * brightness
        IntArray(256) { v -> (255 * (v / 255.0).pow(1 / gamma)).roundToInt() }
    }

    /** One 0xAARRGGBB pixel through this tone; alpha passes through. */
    fun apply(argb: Int): Int {
        val r = curve[(argb shr 16) and 0xFF].toDouble()
        val g = curve[(argb shr 8) and 0xFF].toDouble()
        val b = curve[argb and 0xFF].toDouble()
        val s = 1 + COLOUR_PER_STEP * colour
        val grey = LUMA_R * r + LUMA_G * g + LUMA_B * b
        fun mix(c: Double) = (grey + (c - grey) * s).roundToInt().coerceIn(0, 255)
        return (argb and ALPHA) or (mix(r) shl 16) or (mix(g) shl 8) or mix(b)
    }

    /** Every pixel of [pixels] through this tone, in place. */
    fun applyTo(pixels: IntArray) {
        if (isNone) return
        for (i in pixels.indices) pixels[i] = apply(pixels[i])
    }

    /** For the operator, e.g. "brighter +2, more colour +1". */
    fun describe(): String = when {
        isNone -> "No change"
        colour == 0 -> "Brighter +$brightness"
        brightness == 0 -> "More colour +$colour"
        else -> "Brighter +$brightness, more colour +$colour"
    }

    companion object {
        const val MAX_BRIGHTNESS = 5
        const val MAX_COLOUR = 3

        /** At the top step mid-grey 128 prints as about 153: an inkjet on photo paper rarely needs more. */
        private const val GAMMA_PER_STEP = 0.07
        private const val COLOUR_PER_STEP = 0.08

        // Rec. 709 luma, as in the editor's colour adjustments.
        private const val LUMA_R = 0.2126
        private const val LUMA_G = 0.7152
        private const val LUMA_B = 0.0722
        private const val ALPHA = 0xFF shl 24

        val NONE = PrintTone()
    }
}

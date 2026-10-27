package com.tlw.photodokan.core.imaging

import com.tlw.photodokan.core.model.EditRecipe
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ColorAdjustmentTest {

    private val neutral = EditRecipe(presetId = "studio-slot")

    /** Applies a colour matrix to one pixel, clamped like a real renderer. */
    private fun FloatArray.apply(r: Double, g: Double, b: Double): List<Double> = (0 until 3).map { row ->
        (this[row * 5] * r + this[row * 5 + 1] * g + this[row * 5 + 2] * b + this[row * 5 + 4]).coerceIn(0.0, 255.0)
    }

    private fun close(expected: Double, actual: Double, tolerance: Double = 0.01) =
        assertTrue(abs(expected - actual) < tolerance, "expected $expected, was $actual")

    @Test
    fun noAdjustmentLeavesColoursAlone() {
        assertContentEquals(ColorAdjustment.IDENTITY, ColorAdjustment.matrix(neutral))
        assertTrue(ColorAdjustment.isNeutral(neutral))
    }

    @Test
    fun exposureIsOneStopAtTheEnds() {
        val brighter = ColorAdjustment.matrix(neutral.copy(exposure = 1.0)).apply(50.0, 60.0, 70.0)
        close(100.0, brighter[0]); close(120.0, brighter[1]); close(140.0, brighter[2])
        val darker = ColorAdjustment.matrix(neutral.copy(exposure = -1.0)).apply(50.0, 60.0, 70.0)
        close(25.0, darker[0]); close(35.0, darker[2])
    }

    @Test
    fun contrastPivotsOnMidGrey() {
        for (c in listOf(-1.0, -0.3, 0.5, 1.0)) {
            val m = ColorAdjustment.matrix(neutral.copy(contrast = c))
            m.apply(128.0, 128.0, 128.0).forEach { close(128.0, it) }
        }
        val more = ColorAdjustment.matrix(neutral.copy(contrast = 1.0))
        close(128.0 - 1.5 * 28, more.apply(100.0, 100.0, 100.0)[0])
    }

    @Test
    fun noColourMakesEveryPixelGrey() {
        val grey = ColorAdjustment.matrix(neutral.copy(saturation = -1.0))
        for ((r, g, b) in listOf(Triple(200.0, 30.0, 40.0), Triple(10.0, 220.0, 90.0), Triple(255.0, 255.0, 0.0))) {
            val out = grey.apply(r, g, b)
            close(out[0], out[1]); close(out[1], out[2])
        }
    }

    @Test
    fun blackAndWhiteIsGreyWhateverTheColourSlider() {
        for (s in listOf(-0.5, 0.0, 1.0)) {
            val recipe = neutral.copy(saturation = s, blackAndWhite = true)
            assertFalse(ColorAdjustment.isNeutral(recipe))
            val out = ColorAdjustment.matrix(recipe).apply(200.0, 120.0, 90.0)
            close(out[0], out[1]); close(out[1], out[2])
            // As bright as the colour was.
            close(0.2126 * 200 + 0.7152 * 120 + 0.0722 * 90, out[0])
        }
    }

    @Test
    fun aColourTurnsToTheGreyBlackAndWhiteGivesIt() {
        // The same grey as the matrix gives that colour, so a plain background matches the person.
        val matrix = ColorAdjustment.matrix(neutral.copy(blackAndWhite = true))
        for (argb in listOf(0xFF9FCBEF, 0xFF2040A0, 0xFFFFFFFF, 0xFF000000)) {
            val grey = ColorAdjustment.grey(argb)
            val shown = matrix.apply(((argb shr 16) and 0xff).toDouble(), ((argb shr 8) and 0xff).toDouble(), (argb and 0xff).toDouble())
            for (shift in listOf(16, 8, 0)) close(shown[0], ((grey shr shift) and 0xff).toDouble(), 0.6)
            assertEquals(0xFF000000, grey and 0xFF000000, "opaque")
        }
    }

    @Test
    fun colourAdjustmentKeepsGreysGrey() {
        // Skin and the backdrop shift only when they have colour; neutral greys never pick up a tint.
        for (s in listOf(-0.5, 0.5, 1.0)) {
            val out = ColorAdjustment.matrix(neutral.copy(saturation = s)).apply(90.0, 90.0, 90.0)
            out.forEach { close(90.0, it) }
        }
    }

    @Test
    fun warmthRaisesRedAndLowersBlue() {
        val warm = ColorAdjustment.matrix(neutral.copy(warmth = 1.0)).apply(100.0, 100.0, 100.0)
        assertTrue(warm[0] > 100 && warm[2] < 100)
        close(100.0, warm[1])
        val cool = ColorAdjustment.matrix(neutral.copy(warmth = -1.0)).apply(100.0, 100.0, 100.0)
        assertTrue(cool[0] < 100 && cool[2] > 100)
    }

    @Test
    fun tintMovesGreenAgainstRedAndBlueKeepingBrightness() {
        val luma = { c: List<Double> -> 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2] }
        val pink = ColorAdjustment.matrix(neutral.copy(tint = 1.0)).apply(100.0, 100.0, 100.0)
        assertTrue(pink[0] > 100 && pink[2] > 100 && pink[1] < 100, "$pink")
        close(pink[0], pink[2])
        close(100.0, luma(pink))
        val green = ColorAdjustment.matrix(neutral.copy(tint = -1.0)).apply(100.0, 100.0, 100.0)
        assertTrue(green[1] > 100 && green[0] < 100, "$green")
        close(100.0, luma(green))
        // Red and blue against green by the full range: 8 % each way.
        close(1.08 / 0.92, pink[0] / pink[1], 1e-6)
        assertTrue(!ColorAdjustment.isNeutral(neutral.copy(tint = 0.1)))
    }

    @Test
    fun alphaIsNeverTouched() {
        val m = ColorAdjustment.matrix(EditRecipe("studio-slot", exposure = 1.0, contrast = -1.0, warmth = 1.0, saturation = 1.0))
        assertContentEquals(floatArrayOf(0f, 0f, 0f, 1f, 0f), m.copyOfRange(15, 20))
        assertTrue(m.all { it.isFinite() })
    }

    @Test
    fun adjustmentsCombineInOrder() {
        // Exposure then contrast: brighten 64 → 128 (mid-grey), which contrast then leaves alone.
        val m = ColorAdjustment.matrix(neutral.copy(exposure = 1.0, contrast = 1.0))
        close(128.0, m.apply(64.0, 64.0, 64.0)[0])
    }

    @Test
    fun applyToUsesTheMatrixOffsetsAndClamps() {
        val contrast = ColorAdjustment.matrix(neutral.copy(contrast = 1.0)) // ×1.5 around 128
        val out = ColorAdjustment.applyTo(contrast, doubleArrayOf(128.0, 168.0, 250.0))
        close(128.0, out[0]); close(188.0, out[1]); close(255.0, out[2])
        val dark = ColorAdjustment.applyTo(contrast, doubleArrayOf(10.0, 10.0, 10.0))
        close(0.0, dark[0])
    }
}

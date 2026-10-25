package com.tlw.photodokan.core.imaging

import com.tlw.photodokan.core.face.Face
import com.tlw.photodokan.core.face.PointPx
import com.tlw.photodokan.core.model.EditRecipe
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoLightTest {

    private val w = 300
    private val h = 300

    /** A face whose eyes are at y 100 and mouth at y 180, between x 100 and 200. */
    private val face = Face(
        x = 80.0, y = 60.0, width = 140.0, height = 160.0,
        eyeLeft = PointPx(110.0, 100.0), eyeRight = PointPx(190.0, 100.0), nose = PointPx(150.0, 140.0),
        mouthLeft = PointPx(120.0, 180.0), mouthRight = PointPx(180.0, 180.0), score = 0.9,
    )

    private fun rgb(r: Int, g: Int, b: Int) = (0xff shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /**
     * A photo of skin of [r], [g], [b] lit from one side: brightness running from 70 % on the left
     * to 110 % on the right, on a blue backdrop.
     */
    private fun photo(r: Int, g: Int, b: Int, gains: DoubleArray = doubleArrayOf(1.0, 1.0, 1.0)) = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        if (x in 60 until 240 && y in 40 until 260) {
            val light = 0.7 + 0.4 * (x - 60) / 180.0
            rgb((r * light * gains[0]).roundToInt(), (g * light * gains[1]).roundToInt(), (b * light * gains[2]).roundToInt())
        } else {
            rgb((40 * gains[0]).roundToInt(), (90 * gains[1]).roundToInt(), (200 * gains[2]).roundToInt())
        }
    }

    /** Warm against cool and pink against green, as [AutoLight] measures them, of one colour. */
    private fun warm(c: List<Double>) = (ln(c[0] / c[1]) - ln(c[2] / c[1])) / 2
    private fun pink(c: List<Double>) = (ln(c[0] / c[1]) + ln(c[2] / c[1])) / 2

    private fun corrected(suggestion: AutoLight.Suggestion, r: Double, g: Double, b: Double): List<Double> =
        ColorAdjustment.applyTo(ColorAdjustment.matrix(suggestion.applyTo(EditRecipe("studio-slot"))), doubleArrayOf(r, g, b)).toList()

    // A brown skin, as shot in good light: warm 0.32, pink 0.05.
    private val skin = Triple(230, 158, 121)

    @Test
    fun aWellLitNaturalFaceIsLeftAlone() {
        val suggestion = assertNotNull(AutoLight.suggest(photo(skin.first, skin.second, skin.third), w, h, face))
        assertEquals(AutoLight.Suggestion(0.0, 0.0, 0.0), suggestion)
    }

    @Test
    fun fairAndDarkSkinKeepTheirOwnWarmth() {
        // Fairer, cooler skin and darker, warmer skin: both within skin's own range.
        for ((r, g, b) in listOf(Triple(236, 190, 165), Triple(215, 138, 98))) {
            val suggestion = assertNotNull(AutoLight.suggest(photo(r, g, b), w, h, face))
            assertEquals(0.0, suggestion.warmth, "warmth for $r, $g, $b")
            assertEquals(0.0, suggestion.tint, "tint for $r, $g, $b")
        }
    }

    @Test
    fun aGreenCastIsTakenOut() {
        val suggestion = assertNotNull(AutoLight.suggest(photo(skin.first, skin.second, skin.third, doubleArrayOf(0.9, 1.0, 0.9)), w, h, face))
        assertTrue(suggestion.tint > 0.3, "pinker: $suggestion")
        assertEquals(0.0, suggestion.warmth, 0.05)
        val after = corrected(suggestion, skin.first * 0.9, skin.second.toDouble(), skin.third * 0.9)
        assertTrue(pink(after) >= 0.025, "pink ${pink(after)} after")
    }

    @Test
    fun aMagentaCastIsTakenOut() {
        val suggestion = assertNotNull(AutoLight.suggest(photo(skin.first, skin.second, skin.third, doubleArrayOf(1.0, 0.92, 1.0)), w, h, face))
        assertTrue(suggestion.tint < -0.3, "greener: $suggestion")
        val after = corrected(suggestion, skin.first.toDouble(), skin.second * 0.92, skin.third.toDouble())
        assertTrue(pink(after) <= 0.075, "pink ${pink(after)} after")
    }

    @Test
    fun aBlueCastCoolerThanAnySkinIsWarmedUp() {
        val gains = doubleArrayOf(0.75, 1.0, 1.25)
        val suggestion = assertNotNull(AutoLight.suggest(photo(skin.first, skin.second, skin.third, gains), w, h, face))
        assertTrue(suggestion.warmth > 0.3, "warmer: $suggestion")
        val after = corrected(suggestion, skin.first * gains[0], skin.second * gains[1], skin.third * gains[2])
        assertTrue(warm(after) >= 0.14, "warm ${warm(after)} after")
        // A milder cast could be fair skin's own colour, so it is left for the operator.
        val mild = assertNotNull(AutoLight.suggest(photo(skin.first, skin.second, skin.third, doubleArrayOf(0.9, 1.0, 1.1)), w, h, face))
        assertEquals(0.0, mild.warmth)
    }

    @Test
    fun anOrangeCastWarmerThanAnySkinIsCooledDown() {
        val gains = doubleArrayOf(1.2, 1.0, 0.75)
        val suggestion = assertNotNull(AutoLight.suggest(photo(skin.first, skin.second, skin.third, gains), w, h, face))
        assertTrue(suggestion.warmth < -0.3, "cooler: $suggestion")
        val after = corrected(suggestion, skin.first * gains[0], skin.second * gains[1], skin.third * gains[2])
        assertTrue(warm(after) <= 0.46, "warm ${warm(after)} after")
    }

    @Test
    fun anUnderexposedFaceIsBrightened() {
        // The same face 0.7 stops darker.
        val dark = 2.0.pow(-0.7)
        val suggestion = assertNotNull(AutoLight.suggest(photo(skin.first, skin.second, skin.third, doubleArrayOf(dark, dark, dark)), w, h, face))
        assertTrue(suggestion.exposure in 0.6..0.9, "$suggestion")
        assertEquals(0.0, suggestion.warmth)
    }

    @Test
    fun darkSkinIsNotBrightenedToLookFair() {
        // Dark skin in good light: dark overall, but its highlights are as bright as anyone's.
        val dark = IntArray(w * h) { i ->
            val x = i % w
            if (x in 60 until 240 && (i / w) in 40 until 260) {
                if (x % 10 == 0) rgb(232, 160, 122) else rgb(120, 76, 54)
            } else rgb(40, 90, 200)
        }
        val suggestion = assertNotNull(AutoLight.suggest(dark, w, h, face))
        assertEquals(0.0, suggestion.exposure)
    }

    @Test
    fun anOverexposedFaceIsDarkened() {
        // Over half a stop too bright: much of the skin is clipped white.
        val bright = AutoLight.suggest(photo(skin.first, skin.second, skin.third, doubleArrayOf(1.6, 1.6, 1.6)), w, h, face)
        assertTrue(assertNotNull(bright).exposure < -0.3, "$bright")
        // So bright that no skin colour is left: still darkened, colour left alone.
        val white = assertNotNull(AutoLight.suggest(IntArray(w * h) { rgb(255, 255, 255) }, w, h, face))
        assertTrue(white.exposure < -0.3, "$white")
        assertEquals(0.0, white.warmth)
        assertEquals(0.0, white.tint)
    }

    @Test
    fun aFaceTooSmallToJudgeIsLeftAlone() {
        val tiny = face.copy(
            eyeLeft = PointPx(148.0, 100.0), eyeRight = PointPx(152.0, 100.0),
            mouthLeft = PointPx(149.0, 104.0), mouthRight = PointPx(151.0, 104.0),
        )
        assertNull(AutoLight.suggest(photo(skin.first, skin.second, skin.third), w, h, tiny))
        // Or in the dark: nothing between the eyes and mouth can be seen.
        assertNull(AutoLight.suggest(IntArray(w * h) { rgb(3, 3, 3) }, w, h, face))
    }

    @Test
    fun theSlidersGiveTheColourChangeAskedFor() {
        for ((du, dv) in listOf(0.1 to 0.1, -0.08 to 0.05, 0.12 to -0.12, 0.0 to -0.06)) {
            val (warmth, tint) = AutoLight.sliders(du, dv)
            val gains = ColorAdjustment.gains(warmth, tint)
            assertTrue(abs(ln(gains[0] / gains[1]) - du) < 1e-9, "red/green for $du, $dv")
            assertTrue(abs(ln(gains[2] / gains[1]) - dv) < 1e-9, "blue/green for $du, $dv")
        }
    }

    private fun Double.pow(x: Double) = kotlin.math.exp(x * ln(this))
}

package com.tlw.photodokan.core.matte

import com.tlw.photodokan.core.model.BackgroundConfig
import com.tlw.photodokan.core.model.BackgroundMode
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DespillTest {

    private fun rgb(r: Int, g: Int, b: Int, a: Int = 255) = (a shl 24) or (r shl 16) or (g shl 8) or b
    private fun red(c: Int) = (c shr 16) and 0xff
    private fun green(c: Int) = (c shr 8) and 0xff
    private fun blue(c: Int) = c and 0xff
    private fun luma(c: Int) = 0.2126 * red(c) + 0.7152 * green(c) + 0.0722 * blue(c)

    private val orangeWall = rgb(235, 110, 60)

    @Test
    fun hairLosesTheWallsColour() {
        // Near-black hair, lit orange at the edge; further in it is neutral.
        val tinted = rgb(70, 40, 30)
        val clean = Despill.clean(tinted, orangeWall, inside = rgb(30, 28, 27), near = 1f)
        assertTrue(red(clean) - blue(clean) <= 6, "neutral: ${(clean and 0xffffff).toString(16)}")
        assertEquals(luma(tinted), luma(clean), 1.0, "as bright")
    }

    @Test
    fun skinKeepsItsOwnColourAndLosesOnlyTheGlow() {
        val skin = rgb(190, 130, 100)
        assertEquals(skin, Despill.clean(skin, orangeWall, inside = skin, near = 1f), "no more colour than further in")
        // The same skin glowing orange at the edge goes back to about the skin's own colour.
        val glowing = rgb(215, 125, 80)
        val clean = Despill.clean(glowing, orangeWall, inside = skin, near = 1f)
        fun warmth(c: Int) = (red(c) - blue(c)).toDouble() / luma(c)
        assertTrue(abs(warmth(clean) - warmth(skin)) < 0.08, "skin's own warmth: ${warmth(clean)} against ${warmth(skin)}")
        assertTrue(red(clean) > blue(clean) + 40, "still skin, not grey: ${(clean and 0xffffff).toString(16)}")
    }

    @Test
    fun aShadowedEarKeepsItsWarmth() {
        // Darker than the skin further in, so allowed less colour, but its share of it.
        val skin = rgb(190, 130, 100)
        val shadow = rgb(95, 60, 45)
        val clean = Despill.clean(shadow, orangeWall, inside = skin, near = 1f)
        assertTrue(red(clean) - blue(clean) >= 35, "warm, not grey or blue: ${(clean and 0xffffff).toString(16)}")
        assertTrue(blue(clean) <= green(clean), "never turned blue")
    }

    @Test
    fun aGreyWallOrAPixelDeepInsideChangesNothing() {
        val tinted = rgb(70, 40, 30)
        assertEquals(tinted, Despill.clean(tinted, rgb(200, 200, 198), inside = rgb(30, 28, 27), near = 1f))
        assertEquals(tinted, Despill.clean(tinted, orangeWall, inside = rgb(30, 28, 27), near = 0f))
        val half = Despill.clean(tinted, orangeWall, inside = rgb(30, 28, 27), near = 0.5f)
        assertTrue(red(half) - blue(half) in 10..30, "half way: ${(half and 0xffffff).toString(16)}")
    }

    @Test
    fun aNewPlainBackgroundLosesTheOrangeRimButTheBlurKeepsIt() {
        // A grey person in front of an orange wall, its outer 4 pixels lit orange by it.
        val w = 400
        val h = 400
        val photo = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val inside = minOf(x - 120, 279 - x, y - 80)
            when {
                inside < 0 -> orangeWall
                inside < 4 -> rgb(110, 80, 70)
                else -> rgb(80, 80, 80)
            }
        }
        val matte = Matte(50, 50, ByteArray(50 * 50) { i -> if (i % 50 in 15 until 35 && i / 50 >= 10) -1 else 0 })
        val cutout = Cutout(photo, w, h, matte)
        val rim = 240 * w + 122 // two pixels in from the left edge
        val person = Background.person(cutout)
        assertTrue(red(person[rim]) - blue(person[rim]) <= 15, "rim, cleaned from 40: ${(person[rim] and 0xffffff).toString(16)}")
        assertEquals(rgb(80, 80, 80), person[240 * w + 200], "deep inside, as shot")
        val blurred = Background.apply(cutout, BackgroundConfig(mode = BackgroundMode.BLUR, blurStrength = 1.0))
        assertTrue(red(blurred[rim]) - blue(blurred[rim]) >= 30, "with the wall still there, its light belongs")
    }
}

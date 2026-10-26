package com.tlw.photodokan.print

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PrintToneTest {

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun red(argb: Int) = (argb shr 16) and 0xFF
    private fun green(argb: Int) = (argb shr 8) and 0xFF
    private fun blue(argb: Int) = argb and 0xFF

    @Test
    fun noToneLeavesPixelsAlone() {
        assertTrue(PrintTone.NONE.isNone)
        val pixels = intArrayOf(rgb(0, 0, 0), rgb(12, 130, 250), rgb(255, 255, 255), 0x00336699)
        val before = pixels.copyOf()
        PrintTone.NONE.applyTo(pixels)
        assertContentEquals(before, pixels)
        for (p in before) assertEquals(p, PrintTone.NONE.apply(p))
    }

    @Test
    fun brightnessLiftsMidtonesButKeepsBlackAndWhite() {
        val top = PrintTone(brightness = PrintTone.MAX_BRIGHTNESS)
        assertEquals(rgb(0, 0, 0), top.apply(rgb(0, 0, 0)))
        assertEquals(rgb(255, 255, 255), top.apply(rgb(255, 255, 255)))
        val mid = red(top.apply(rgb(128, 128, 128)))
        assertTrue(mid in 145..160, "mid-grey printed as $mid")
    }

    @Test
    fun eachBrightnessStepIsBrighterThanTheLast() {
        var last = -1
        for (step in 0..PrintTone.MAX_BRIGHTNESS) {
            val v = green(PrintTone(brightness = step).apply(rgb(100, 100, 100)))
            assertTrue(v > last, "step $step gave $v after $last")
            last = v
        }
    }

    @Test
    fun brightnessKeepsGreysGrey() {
        val out = PrintTone(brightness = 3).apply(rgb(90, 90, 90))
        assertEquals(red(out), green(out))
        assertEquals(green(out), blue(out))
    }

    @Test
    fun colourStrengthensColourButLeavesGreysAlone() {
        val tone = PrintTone(colour = PrintTone.MAX_COLOUR)
        assertEquals(rgb(120, 120, 120), tone.apply(rgb(120, 120, 120)))
        val skin = rgb(198, 142, 101)
        val out = tone.apply(skin)
        assertTrue(red(out) - blue(out) > red(skin) - blue(skin), "skin should get warmer, was ${out.toString(16)}")
    }

    @Test
    fun keepsAlpha() {
        assertEquals(0x40, PrintTone(2, 2).apply(0x40806040) ushr 24)
    }

    @Test
    fun refusesStepsOutOfRange() {
        assertFailsWith<IllegalArgumentException> { PrintTone(brightness = -1) }
        assertFailsWith<IllegalArgumentException> { PrintTone(brightness = PrintTone.MAX_BRIGHTNESS + 1) }
        assertFailsWith<IllegalArgumentException> { PrintTone(colour = PrintTone.MAX_COLOUR + 1) }
    }

    @Test
    fun describesItselfForTheOperator() {
        assertEquals("No change", PrintTone.NONE.describe())
        assertEquals("Brighter +2", PrintTone(brightness = 2).describe())
        assertEquals("More colour +1", PrintTone(colour = 1).describe())
        assertEquals("Brighter +3, more colour +2", PrintTone(3, 2).describe())
    }
}

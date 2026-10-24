package com.tlw.photodokan.core.matte

import com.tlw.photodokan.core.model.BackgroundConfig
import com.tlw.photodokan.core.model.BackgroundMode
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackgroundTest {

    private fun rgb(r: Int, g: Int, b: Int) = (0xff shl 24) or (r shl 16) or (g shl 8) or b
    private fun red(c: Int) = (c shr 16) and 0xff
    private fun green(c: Int) = (c shr 8) and 0xff
    private fun blue(c: Int) = c and 0xff

    private val w = 400
    private val h = 400

    /** The person is the square in the middle; the rest is background. */
    private fun personInTheMiddle(size: Int = 50) = Matte(size, size, ByteArray(size * size) { i ->
        val x = i % size
        val y = i / size
        if (x in size / 4 until size * 3 / 4 && y in size / 4 until size * 3 / 4) -1 else 0
    })

    /** A red person on a blue backdrop with light stripes (creases) 2 pixels wide every 8. */
    private val photo = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        when {
            x in 100 until 300 && y in 100 until 300 -> rgb(200, 30, 30)
            x % 8 < 2 -> rgb(80, 120, 230)
            else -> rgb(40, 80, 190)
        }
    }

    /** A blur of 16 pixels on this 400-pixel photo. */
    private val blur = BackgroundConfig(mode = BackgroundMode.BLUR, blurStrength = 1.0)

    @Test
    fun keepLeavesThePhotoAsItIs() {
        val kept = Background.apply(photo, w, h, personInTheMiddle(), BackgroundConfig(mode = BackgroundMode.KEEP))
        assertContentEquals(photo, kept)
    }

    @Test
    fun replacePutsTheColourBehindThePerson() {
        val white = Background.apply(photo, w, h, personInTheMiddle(), BackgroundConfig(mode = BackgroundMode.REPLACE))
        assertEquals(rgb(255, 255, 255), white[10 * w + 10], "background")
        assertEquals(rgb(200, 30, 30), white[200 * w + 200], "person untouched")
        val blue = Background.apply(photo, w, h, personInTheMiddle(), BackgroundConfig(mode = BackgroundMode.REPLACE, replaceColorArgb = BackgroundConfig.LIGHT_BLUE))
        assertEquals(BackgroundConfig.LIGHT_BLUE.toInt(), blue[390 * w + 5])
    }

    @Test
    fun theEdgeOfThePersonBlends() {
        val half = Matte(1, 1, byteArrayOf(-128)) // just over half person everywhere
        val out = Background.apply(IntArray(4) { rgb(0, 0, 0) }, 2, 2, half, BackgroundConfig(mode = BackgroundMode.REPLACE))
        assertEquals(127, red(out[0]))
    }

    @Test
    fun blurSmoothsTheBackdropsCreases() {
        val out = Background.apply(photo, w, h, personInTheMiddle(), blur)
        // Before: stripes 40 apart in red; after, nearby pixels are nearly the same.
        val row = 40 * w
        val spread = (0 until 40).map { red(out[row + it]) }.let { it.max() - it.min() }
        assertTrue(spread <= 5, "stripes left: $spread of 40")
        // The backdrop stays the same blue on average.
        val average = (0 until 40).map { blue(out[row + it]) }.average()
        assertTrue(abs(average - (2 * 230 + 6 * 190) / 8.0) < 3, "blue $average")
    }

    @Test
    fun theBlurNeverSmearsThePersonIntoTheBackdrop() {
        val out = Background.apply(photo, w, h, personInTheMiddle(), blur)
        // Just outside the red person, the backdrop has no more red than the backdrop's own (50 on
        // average); a plain blur would spread the person's 200 out to here.
        for (x in listOf(80, 90)) assertTrue(red(out[200 * w + x]) <= 53, "red ${red(out[200 * w + x])} at $x")
        assertEquals(rgb(200, 30, 30), out[200 * w + 200], "the person stays sharp")
    }

    @Test
    fun theBlurLooksTheSameAtAnySize() {
        // The same photo at half size: the blur is a share of the photo, not a number of pixels.
        val small = IntArray((w / 2) * (h / 2)) { i -> photo[(i / (w / 2)) * 2 * w + (i % (w / 2)) * 2] }
        val big = Background.apply(photo, w, h, personInTheMiddle(), blur)
        val half = Background.apply(small, w / 2, h / 2, personInTheMiddle(), blur)
        for ((x, y) in listOf(20 to 20, 60 to 320, 360 to 180)) {
            val difference = abs(blue(big[y * w + x]) - blue(half[(y / 2) * (w / 2) + x / 2]))
            assertTrue(difference <= 3, "$difference apart at ($x, $y)")
        }
    }

    @Test
    fun noBlurLeavesTheBackdrop() {
        val out = Background.apply(photo, w, h, personInTheMiddle(), blur.copy(blurStrength = 0.0))
        assertContentEquals(photo, out)
    }
}

package com.tlw.photodokan.core.matte

import com.tlw.photodokan.core.model.BackgroundConfig
import com.tlw.photodokan.core.model.BackgroundMode
import com.tlw.photodokan.core.model.BrushMode
import com.tlw.photodokan.core.model.MaskPoint
import com.tlw.photodokan.core.model.MaskStroke
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
    fun noBlueShowsBetweenStrandsOfHair() {
        // Dark hair thinning out to the left over a blue backdrop; the model saw only a fade.
        val hair = rgb(40, 30, 25)
        val strands = IntArray(w * h) { val x = it % w; if (x >= 240 || (x >= 160 && x % 8 < (x - 160) / 10)) hair else rgb(40, 90, 200) }
        val fade = Matte(50, 50, ByteArray(50 * 50) { ((it % 50 - 20).coerceIn(0, 10) * 255 / 10).toByte() })
        val white = Background.apply(strands, w, h, fade, BackgroundConfig(mode = BackgroundMode.REPLACE))
        for (x in 160 until 230) {
            val c = white[200 * w + x]
            if (strands[200 * w + x] == hair) {
                assertTrue(red(c) < 90 && blue(c) < 70, "strand at $x is ${(c and 0xffffff).toString(16)}")
            } else {
                assertTrue(red(c) >= 225 && green(c) >= 225 && blue(c) >= 225, "between strands at $x is ${(c and 0xffffff).toString(16)}")
            }
        }
    }

    @Test
    fun aSoftEdgeLosesTheOldBackdropsColour() {
        // A red person over blue whose edge fades over 8 pixels, as a lens draws it.
        val person = rgb(200, 60, 50)
        val backdrop = rgb(40, 90, 200)
        val white = rgb(255, 255, 255)
        fun blend(a: Double, p: Int, q: Int): Int {
            fun channel(shift: Int) = (((p shr shift) and 0xff) * a + ((q shr shift) and 0xff) * (1 - a)).toInt()
            return rgb(channel(16), channel(8), channel(0))
        }
        fun share(x: Int) = ((x - 196) / 8.0).coerceIn(0.0, 1.0)
        val soft = IntArray(w * h) { blend(share(it % w), person, backdrop) }
        val matte = Matte(50, 50, ByteArray(50 * 50) { if (it % 50 >= 25) -1 else 0 })

        val out = Background.apply(soft, w, h, matte, BackgroundConfig(mode = BackgroundMode.REPLACE))

        // How much bluer than the person over white each pixel is. Mixing the photo's pixel with
        // white by the matte leaves up to 56 of the backdrop's blue, by the fitted matte 55.
        fun blueness(c: Int) = blue(c) - (red(c) + green(c)) / 2
        val scaled = matte.resized(w, h)
        for (x in 190 until 210) {
            val wanted = blueness(blend(share(x), person, white))
            val now = blueness(out[200 * w + x]) - wanted
            val mixed = blueness(blend((scaled[200 * w + x].toInt() and 0xff) / 255.0, soft[200 * w + x], white)) - wanted
            assertTrue(now <= 40, "$now bluer at $x")
            assertTrue(now <= mixed, "$now bluer at $x, plainly mixed $mixed")
        }
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
    fun touchUpsChangeWhatIsReplaced() {
        val erased = BackgroundConfig(
            mode = BackgroundMode.REPLACE,
            touchUps = listOf(MaskStroke(BrushMode.ERASE, 0.05, listOf(MaskPoint(0.5, 0.5)))),
        )
        val out = Background.apply(photo, w, h, personInTheMiddle(), erased)
        assertEquals(rgb(255, 255, 255), out[200 * w + 200], "erased from the person")
        assertEquals(rgb(200, 30, 30), out[150 * w + 150], "the rest of the person stays")
    }

    @Test
    fun oneCutoutServesEveryBackground() {
        val cutout = Cutout(photo, w, h, personInTheMiddle())
        for (config in listOf(blur, blur.copy(blurStrength = 0.3), BackgroundConfig(mode = BackgroundMode.REPLACE), BackgroundConfig(mode = BackgroundMode.KEEP))) {
            assertContentEquals(Background.apply(photo, w, h, personInTheMiddle(), config), Background.apply(cutout, config), "$config")
        }
    }

    @Test
    fun noBlurLeavesTheBackdrop() {
        val out = Background.apply(photo, w, h, personInTheMiddle(), blur.copy(blurStrength = 0.0))
        assertContentEquals(photo, out)
    }
}

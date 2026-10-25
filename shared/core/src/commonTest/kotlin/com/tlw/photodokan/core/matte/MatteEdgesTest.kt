package com.tlw.photodokan.core.matte

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MatteEdgesTest {

    private fun rgb(r: Int, g: Int, b: Int) = (0xff shl 24) or (r shl 16) or (g shl 8) or b

    private val w = 400
    private val h = 400
    private val person = rgb(200, 150, 120)
    private val backdrop = rgb(60, 110, 200)

    /** The person is everything right of [edge]: a sharp edge in the photo. */
    private fun photo(edge: Int = 203) = IntArray(w * h) { if (it % w >= edge) person else backdrop }

    /** The same, from the model: 50 values across, so 8 photo pixels each and soft when scaled up. */
    private fun matte(from: Int = 25) = Matte(50, 50, ByteArray(50 * 50) { if (it % 50 >= from) -1 else 0 })

    private fun ByteArray.at(x: Int, y: Int = 200) = this[y * w + x].toInt() and 0xff

    @Test
    fun aSoftEdgeSnapsToThePhotosEdge() {
        val scaled = matte().resized(w, h)
        assertTrue(scaled.at(202) > 128, "scaled up, the model's edge is a blur: ${scaled.at(202)} on the backdrop")
        assertTrue(scaled.at(197) < 64, "and the person is part backdrop: ${scaled.at(197)}")

        val fitted = MatteEdges.fit(photo(), w, h, matte())
        // Next to the edge some backdrop stays slightly part person: a guided filter matches the
        // matte to the colours only roughly.
        for (x in 180 until 203) assertTrue(fitted.at(x) <= 64, "backdrop at $x is ${fitted.at(x)}")
        for (x in 180 until 196) assertEquals(0, fitted.at(x), "backdrop at $x")
        for (x in 203 until 230) assertTrue(fitted.at(x) >= 250, "person at $x is ${fitted.at(x)}")
    }

    @Test
    fun awayFromTheEdgeTheMatteIsKept() {
        val fitted = MatteEdges.fit(photo(), w, h, matte())
        for (x in listOf(0, 50, 150)) assertEquals(0, fitted.at(x, 30))
        for (x in listOf(250, 330, 399)) assertEquals(255, fitted.at(x, 370))
    }

    @Test
    fun strandsOfHairStandOutFromTheBackdropBetweenThem() {
        // Dark hair on a light backdrop, its strands thinning out to the left from x = 240 to 160;
        // the model only saw it fade from person to backdrop across that band.
        val hair = rgb(40, 30, 25)
        val photo = IntArray(w * h) { val x = it % w; if (x >= 240 || (x >= 160 && x % 8 < (x - 160) / 10)) hair else rgb(230, 230, 225) }
        val fade = Matte(50, 50, ByteArray(50 * 50) { ((it % 50 - 20).coerceIn(0, 10) * 255 / 10).toByte() })
        val fitted = MatteEdges.fit(photo, w, h, fade)
        for (x in 170 until 230) {
            if (photo[200 * w + x] == hair) assertTrue(fitted.at(x) >= 200, "strand at $x is ${fitted.at(x)}")
            else assertTrue(fitted.at(x) <= 40, "gap at $x is ${fitted.at(x)}")
        }
    }

    @Test
    fun aMatteBiggerThanThePhotoWorksToo() {
        val small = IntArray(40 * 40) { if (it % 40 >= 20) person else backdrop }
        val fitted = MatteEdges.fit(small, 40, 40, matte())
        assertEquals(40 * 40, fitted.size)
        assertEquals(0, fitted[20 * 40 + 5].toInt() and 0xff)
        assertEquals(255, fitted[20 * 40 + 35].toInt() and 0xff)
    }
}

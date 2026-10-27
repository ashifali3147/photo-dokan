package com.tlw.photodokan.core.imaging

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ShrinkTest {

    @Test
    fun theFactorBringsTheLongerSideToTheSizeOrJustUnder() {
        assertEquals(2, Shrink.factorFor(1152, 1536, 800))
        assertEquals(3, Shrink.factorFor(2000, 1500, 800))
        assertEquals(1, Shrink.factorFor(600, 800, 800), "already small enough")
        assertEquals(1, Shrink.factorFor(300, 200, 800))
    }

    @Test
    fun eachBlockBecomesTheAverageOfItsColours() {
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        val red = 0xFFFF0000.toInt()
        // Left block: two black, two white. Right block: all red.
        val photo = intArrayOf(
            black, white, red, red,
            white, black, red, red,
        )
        val shrunk = Shrink.box(photo, 4, 2, 2)
        assertEquals(2, shrunk.width)
        assertEquals(1, shrunk.height)
        assertContentEquals(intArrayOf(0xFF808080.toInt(), red), shrunk.argb)
    }

    @Test
    fun pixelsPastTheLastWholeBlockAreLeftOut() {
        val shrunk = Shrink.box(IntArray(5 * 7) { 0xFF102030.toInt() }, 5, 7, 2)
        assertEquals(2, shrunk.width)
        assertEquals(3, shrunk.height)
        assertContentEquals(IntArray(6) { 0xFF102030.toInt() }, shrunk.argb)
    }

    @Test
    fun aFactorOfOneIsACopy() {
        val photo = intArrayOf(1, 2, 3, 4)
        val shrunk = Shrink.box(photo, 2, 2, 1)
        assertContentEquals(photo, shrunk.argb)
        photo[0] = 9
        assertEquals(1, shrunk.argb[0], "the copy doesn't change with the photo")
    }

    @Test
    fun aFactorBiggerThanThePhotoIsRefused() {
        assertFailsWith<IllegalArgumentException> { Shrink.box(IntArray(4), 2, 2, 3) }
    }
}

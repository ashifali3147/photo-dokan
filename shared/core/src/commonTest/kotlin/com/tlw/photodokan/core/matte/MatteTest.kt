package com.tlw.photodokan.core.matte

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MatteTest {

    private fun close(expected: Double, actual: Double) = assertTrue(abs(expected - actual) < 1e-9, "expected $expected, was $actual")

    /** Background on the left half, the person on the right. */
    private val split = Matte(4, 2, byteArrayOf(0, 0, -1, -1, 0, 0, -1, -1))

    @Test
    fun valuesRunFromBackgroundToPerson() {
        assertEquals(0, split[0, 0])
        assertEquals(255, split[3, 1])
        close(0.0, split.at(0.1, 0.5))
        close(1.0, split.at(0.9, 0.5))
    }

    @Test
    fun betweenPixelsTheEdgeIsSmooth() {
        // Halfway across the photo is the boundary between pixel 1 (0) and pixel 2 (255).
        close(0.5, split.at(0.5, 0.5))
        close(0.25, split.at(0.4375, 0.5))
    }

    @Test
    fun theEdgesOfThePhotoUseTheEdgePixels() {
        close(0.0, split.at(0.0, 0.0))
        close(1.0, split.at(1.0, 1.0))
    }

    @Test
    fun mattesWithTheSameValuesAreEqual() {
        assertEquals(split, Matte(4, 2, split.alpha.copyOf()))
        assertNotEquals(split, Matte(2, 4, split.alpha.copyOf()))
    }

    @Test
    fun aMatteMustCoverItsSize() {
        assertFailsWith<IllegalArgumentException> { Matte(4, 2, ByteArray(7)) }
        assertFailsWith<IllegalArgumentException> { Matte(0, 0, ByteArray(0)) }
    }
}

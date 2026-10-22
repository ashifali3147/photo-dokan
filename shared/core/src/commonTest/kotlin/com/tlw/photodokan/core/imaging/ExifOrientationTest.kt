package com.tlw.photodokan.core.imaging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExifOrientationTest {

    // Minimal JPEGs built byte by byte: no photos are stored in the repo.

    private fun u16(v: Int, big: Boolean) = if (big) listOf(v shr 8, v and 0xFF) else listOf(v and 0xFF, v shr 8)
    private fun u32(v: Int, big: Boolean) =
        if (big) u16(v ushr 16, true) + u16(v and 0xFFFF, true) else u16(v and 0xFFFF, false) + u16(v ushr 16, false)

    /** TIFF block with IFD0 holding the given (tag, type, value) entries. */
    private fun tiff(entries: List<Triple<Int, Int, Int>>, big: Boolean): List<Int> {
        val header = (if (big) listOf('M'.code, 'M'.code) else listOf('I'.code, 'I'.code)) + u16(42, big) + u32(8, big)
        val ifd = u16(entries.size, big) + entries.flatMap { (tag, type, value) ->
            u16(tag, big) + u16(type, big) + u32(1, big) + u16(value, big) + listOf(0, 0)
        } + u32(0, big)
        return header + ifd
    }

    private fun segment(marker: Int, payload: List<Int>) = listOf(0xFF, marker) + u16(payload.size + 2, true) + payload

    private fun exifSegment(tiff: List<Int>) = segment(0xE1, "Exif".map { it.code } + listOf(0, 0) + tiff)

    private fun jpeg(vararg segments: List<Int>): ByteArray =
        (listOf(0xFF, 0xD8) + segments.flatMap { it } + listOf(0xFF, 0xDA, 0x00, 0x02, 0xFF, 0xD9))
            .map { it.toByte() }.toByteArray()

    private fun orientationJpeg(value: Int, big: Boolean = true) =
        jpeg(exifSegment(tiff(listOf(Triple(0x0112, 3, value)), big)))

    @Test
    fun readsEveryOrientationInBothByteOrders() {
        for (expected in ExifOrientation.entries) {
            assertEquals(expected, ExifOrientation.read(orientationJpeg(expected.exifValue, big = true)))
            assertEquals(expected, ExifOrientation.read(orientationJpeg(expected.exifValue, big = false)))
        }
    }

    @Test
    fun phonePortraitIsRotatedClockwise() {
        // Most phones store portrait shots sideways with orientation 6.
        val o = ExifOrientation.read(orientationJpeg(6))
        assertEquals(90, o.rotationDegrees)
        assertFalse(o.mirrored)
        assertTrue(o.swapsDimensions)
    }

    @Test
    fun findsTheTagAfterOtherEntriesAndSegments() {
        val entries = listOf(Triple(0x010F, 2, 0), Triple(0x0110, 2, 0), Triple(0x0112, 3, 8))
        val xmp = segment(0xE1, "http://ns.adobe.com/xap/1.0/".map { it.code } + listOf(0))
        val jfif = segment(0xE0, "JFIF".map { it.code } + listOf(0, 1, 2, 0, 0, 1, 0, 1, 0, 0))
        assertEquals(ExifOrientation.ROTATE_270, ExifOrientation.read(jpeg(jfif, xmp, exifSegment(tiff(entries, big = false)))))
    }

    @Test
    fun missingOrUnknownOrientationIsNormal() {
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.read(jpeg()))
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.read(orientationJpeg(9)))
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.read(jpeg(exifSegment(tiff(listOf(Triple(0x0110, 2, 0)), big = true)))))
    }

    @Test
    fun otherFormatsAndDamagedDataAreNormal() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.read(png))
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.read(ByteArray(0)))

        val good = orientationJpeg(6)
        for (cut in 4 until good.size) {
            ExifOrientation.read(good.copyOf(cut)) // must not throw on a truncated file
        }
        val badLength = good.copyOf().also { it[4] = 0x7F; it[5] = 0x7F }
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.read(badLength))
    }

    @Test
    fun onlyQuarterTurnsSwapWidthAndHeight() {
        val swapping = ExifOrientation.entries.filter { it.swapsDimensions }.map { it.exifValue }.toSet()
        assertEquals(setOf(5, 6, 7, 8), swapping)
    }
}

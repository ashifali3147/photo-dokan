package com.tlw.photodokan.core.imaging

import com.tlw.photodokan.core.model.CropConfig
import com.tlw.photodokan.core.model.FaceGuide
import com.tlw.photodokan.core.model.PhotoSizePreset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CropGeometryTest {

    private val passport = PhotoSizePreset.PASSPORT_35X45.aspect // 35 / 45, taller than wide
    private fun close(expected: Double, actual: Double, tolerance: Double = 1e-6) =
        assertTrue(abs(expected - actual) < tolerance, "expected $expected, was $actual")

    /** The frame's four corners in photo pixels, after turning it back by the rotation. */
    private fun corners(r: CropRect): List<Pair<Double, Double>> {
        val a = -r.rotationDegrees * PI / 180
        return listOf(-1 to -1, 1 to -1, 1 to 1, -1 to 1).map { (sx, sy) ->
            val x = sx * r.width / 2
            val y = sy * r.height / 2
            (r.centerX + x * cos(a) - y * sin(a)) to (r.centerY + x * sin(a) + y * cos(a))
        }
    }

    private fun assertInside(r: CropRect, w: Int, h: Int) {
        for ((x, y) in corners(r)) {
            assertTrue(x >= -1e-6 && x <= w + 1e-6 && y >= -1e-6 && y <= h + 1e-6, "corner ($x, $y) outside $w × $h for $r")
        }
    }

    @Test
    fun defaultCropIsTheLargestCentredFrameOfThePhotoSize() {
        // Phone portrait 3000 × 4000: limited by width.
        val tall = CropGeometry.rect(3000, 4000, passport, CropConfig())
        close(3000.0, tall.width)
        close(3000.0 * 45 / 35, tall.height)
        close(1500.0, tall.centerX)
        close(2000.0, tall.centerY)

        // Landscape 4000 × 3000: limited by height.
        val wide = CropGeometry.rect(4000, 3000, passport, CropConfig())
        close(3000.0, wide.height)
        close(3000.0 * 35 / 45, wide.width)
    }

    @Test
    fun cropAlwaysKeepsThePhotoSizesAspect() {
        val random = Random(7)
        repeat(200) {
            val crop = CropGeometry.clamp(
                3000, 4000, passport,
                CropConfig(random.nextDouble(), random.nextDouble(), 1 + random.nextDouble() * 9, random.nextDouble(-45.0, 45.0)),
            )
            val r = CropGeometry.rect(3000, 4000, passport, crop)
            close(passport, r.width / r.height, 1e-9)
        }
    }

    @Test
    fun clampedCropNeverLeavesThePhotoEvenWhenTurned() {
        val random = Random(42)
        for ((w, h) in listOf(3000 to 4000, 4000 to 3000, 1000 to 1000, 6000 to 1500)) {
            repeat(300) {
                val wanted = CropConfig(random.nextDouble(), random.nextDouble(), 1 + random.nextDouble() * 9, random.nextDouble(-45.0, 45.0))
                val crop = CropGeometry.clamp(w, h, passport, wanted)
                assertInside(CropGeometry.rect(w, h, passport, crop), w, h)
                assertTrue(crop.scale <= CropGeometry.MAX_SCALE + 1e-9)
            }
        }
    }

    @Test
    fun straighteningZoomsInOnlyAsMuchAsNeeded() {
        val turned = CropGeometry.rotate(3000, 4000, passport, CropConfig(), 5.0)
        assertTrue(turned.scale > 1.0)
        // A touch less zoom and a corner would show outside the photo.
        val tooWide = CropGeometry.rect(3000, 4000, passport, turned.copy(scale = turned.scale * 0.99))
        assertTrue(corners(tooWide).any { (x, y) -> x < 0 || x > 3000 || y < 0 || y > 4000 })
    }

    @Test
    fun alreadyFittingCropIsLeftAlone() {
        val crop = CropConfig(centerX = 0.5, centerY = 0.4, scale = 2.0, rotationDegrees = 3.0)
        assertEquals(crop, CropGeometry.clamp(3000, 4000, passport, crop))
    }

    @Test
    fun zoomStaysBetweenFullFrameAndTheLimit() {
        assertEquals(1.0, CropGeometry.zoom(3000, 4000, passport, CropConfig(), 0.5).scale)
        assertEquals(CropGeometry.MAX_SCALE, CropGeometry.zoom(3000, 4000, passport, CropConfig(scale = 6.0), 3.0).scale)
    }

    @Test
    fun draggingRightMovesThePhotoRightUnderTheFrame() {
        val zoomed = CropConfig(scale = 2.0)
        val moved = CropGeometry.pan(3000, 4000, passport, zoomed, dx = 0.25, dy = 0.0)
        // The photo moves right, so the frame looks further left in the photo.
        assertTrue(moved.centerX < zoomed.centerX)
        close(zoomed.centerY, moved.centerY)
        val r = CropGeometry.rect(3000, 4000, passport, zoomed)
        close(1500.0 - 0.25 * r.width, moved.centerX * 3000)
    }

    @Test
    fun draggingFollowsTheFingerWhenTheFrameIsTurned() {
        // Turned 90° would swap axes; at 30° a sideways drag moves the frame partly up the photo.
        val crop = CropGeometry.clamp(3000, 4000, passport, CropConfig(scale = 3.0, rotationDegrees = 30.0))
        val moved = CropGeometry.pan(3000, 4000, passport, crop, dx = 0.1, dy = 0.0)
        assertTrue(moved.centerX < crop.centerX)
        assertTrue(moved.centerY > crop.centerY)
    }

    @Test
    fun panStopsAtThePhotoEdge() {
        val atEdge = CropGeometry.pan(3000, 4000, passport, CropConfig(scale = 2.0), dx = 50.0, dy = 0.0)
        val r = CropGeometry.rect(3000, 4000, passport, atEdge)
        close(r.width / 2, r.centerX)
    }

    @Test
    fun rotationIsLimitedToStraightening() {
        assertEquals(45.0, CropGeometry.rotate(3000, 4000, passport, CropConfig(), 90.0).rotationDegrees)
        assertEquals(-45.0, CropGeometry.rotate(3000, 4000, passport, CropConfig(), -60.0).rotationDegrees)
    }

    @Test
    fun faceGuideMustRunTopToBottom() {
        assertFailsWith<IllegalArgumentException> { FaceGuide(headTop = 0.8, chin = 0.2) }
        assertTrue(PhotoSizePreset.DEFAULTS.all { it.guide.headTop < it.guide.chin })
    }
}

package com.tlw.photodokan.core.face

import com.tlw.photodokan.core.imaging.CropGeometry
import com.tlw.photodokan.core.model.FaceGuide
import com.tlw.photodokan.core.model.PhotoSizePreset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FaceCropTest {

    private val passport = PhotoSizePreset.PASSPORT_35X45 // guide: head top 10 %, chin 78 %

    /**
     * An upright face with its eyes at ([x], [eyeY]), [d] pixels above the mouth, turned clockwise
     * by [tiltDegrees] about the eyes.
     */
    private fun face(x: Double = 500.0, eyeY: Double = 600.0, d: Double = 100.0, tiltDegrees: Double = 0.0): Face {
        val a = tiltDegrees * PI / 180
        fun at(dx: Double, dy: Double) = PointPx(x + dx * cos(a) - dy * sin(a), eyeY + dx * sin(a) + dy * cos(a))
        return Face(
            x = x - d, y = eyeY - d, width = 2 * d, height = 2.4 * d,
            eyeLeft = at(-0.7 * d, 0.0), eyeRight = at(0.7 * d, 0.0), nose = at(0.0, 0.5 * d),
            mouthLeft = at(-0.45 * d, d), mouthRight = at(0.45 * d, d), score = 0.95,
        )
    }

    /** Where in the photo the frame's point at ([fx], [fy]), as fractions of the frame, falls. */
    private fun inPhoto(width: Int, height: Int, preset: PhotoSizePreset, fit: FaceFit, fx: Double, fy: Double): PointPx {
        val rect = CropGeometry.rect(width, height, preset.aspect, fit.crop)
        // A large output so pixel centres are close to the exact fractions.
        val outW = 3500
        val outH = (outW / preset.aspect).toInt()
        val (px, py) = CropGeometry.sourcePoint(rect, outW, outH, (fx * outW).toInt(), (fy * outH).toInt())
        return PointPx(px, py)
    }

    private fun near(expected: PointPx, actual: PointPx, tolerance: Double = 1.0) =
        assertTrue(abs(expected.x - actual.x) < tolerance && abs(expected.y - actual.y) < tolerance, "expected $expected, was $actual")

    @Test
    fun theHeadLandsOnTheGuide() {
        val fit = assertNotNull(FaceCrop.fit(1000, 1500, passport, face()))
        assertTrue(fit.fits)
        assertEquals(0.0, fit.crop.rotationDegrees)
        // Top of the head 1.7 eye-to-mouth distances above the eyes, the chin 1.85 below.
        near(PointPx(500.0, 430.0), inPhoto(1000, 1500, passport, fit, 0.5, passport.guide.headTop))
        near(PointPx(500.0, 785.0), inPhoto(1000, 1500, passport, fit, 0.5, passport.guide.chin))
    }

    @Test
    fun aTiltedHeadIsStraightened() {
        val fit = assertNotNull(FaceCrop.fit(1000, 1500, passport, face(tiltDegrees = 8.0)))
        assertTrue(abs(fit.crop.rotationDegrees + 8.0) < 1e-9, "turned back by 8°, was ${fit.crop.rotationDegrees}")
        // The eyes come out level, at the same height in the frame.
        val f = face(tiltDegrees = 8.0)
        val rect = CropGeometry.rect(1000, 1500, passport.aspect, fit.crop)
        val a = rect.rotationDegrees * PI / 180
        fun frameY(p: PointPx) = -(p.x - rect.centerX) * sin(-a) + (p.y - rect.centerY) * cos(-a)
        assertTrue(abs(frameY(f.eyeLeft) - frameY(f.eyeRight)) < 1e-6)
        val crown = PointPx(500 + 170 * sin(8 * PI / 180), 600 - 170 * cos(8 * PI / 180))
        near(crown, inPhoto(1000, 1500, passport, fit, 0.5, passport.guide.headTop))
    }

    @Test
    fun eachPhotoSizeUsesItsOwnGuide() {
        val low = passport.copy(guide = FaceGuide(headTop = 0.2, chin = 0.9))
        val fit = assertNotNull(FaceCrop.fit(1000, 1500, low, face()))
        near(PointPx(500.0, 430.0), inPhoto(1000, 1500, low, fit, 0.5, 0.2))
        near(PointPx(500.0, 785.0), inPhoto(1000, 1500, low, fit, 0.5, 0.9))
        val wide = assertNotNull(FaceCrop.fit(1000, 1500, PhotoSizePreset.STUDIO_SLOT, face()))
        near(PointPx(500.0, 430.0), inPhoto(1000, 1500, PhotoSizePreset.STUDIO_SLOT, wide, 0.5, PhotoSizePreset.STUDIO_SLOT.guide.headTop))
    }

    @Test
    fun aPhotoCutCloseAboveTheHeadDoesNotFit() {
        // Hair touches the top edge: no room for the space the guide wants above the head.
        val fit = assertNotNull(FaceCrop.fit(1000, 1500, passport, face(eyeY = 200.0)))
        assertFalse(fit.fits)
        assertEquals(fit.crop, CropGeometry.clamp(1000, 1500, passport.aspect, fit.crop), "still a crop that fits the photo")
    }

    @Test
    fun aHeadFillingThePhotoDoesNotFit() {
        val fit = assertNotNull(FaceCrop.fit(1000, 1500, passport, face(eyeY = 700.0, d = 300.0)))
        assertFalse(fit.fits)
        assertEquals(1.0, fit.crop.scale, "the whole photo, as close as it gets")
    }

    @Test
    fun aFaceUpsideDownOrSidewaysIsLeftAlone() {
        assertNull(FaceCrop.fit(1000, 1500, passport, face(tiltDegrees = 90.0)))
        assertNull(FaceCrop.fit(1000, 1500, passport, face(tiltDegrees = 180.0)))
    }

    @Test
    fun theCustomerIsTheLargestFace() {
        val small = face(x = 200.0, d = 40.0)
        val big = face(x = 600.0, d = 100.0)
        assertEquals(big, FaceCrop.main(listOf(small, big)))
        assertNull(FaceCrop.main(emptyList()))
    }
}

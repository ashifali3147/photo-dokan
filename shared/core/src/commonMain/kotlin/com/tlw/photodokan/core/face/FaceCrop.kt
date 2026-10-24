package com.tlw.photodokan.core.face

import com.tlw.photodokan.core.imaging.CropGeometry
import com.tlw.photodokan.core.model.CropConfig
import com.tlw.photodokan.core.model.PhotoSizePreset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * A suggested crop for a face: [crop] puts the head on the photo size's guide with the eyes level.
 * [fits] is false when the photo is cut too close to the head (or the head is too big) for that,
 * and [crop] is then the nearest crop that stays inside the photo.
 */
data class FaceFit(val crop: CropConfig, val fits: Boolean)

/**
 * Crops a photo around a face so the head sits between the guide's head-top and chin lines, as an
 * operator would by hand. The detector finds eyes and mouth, not the top of the head or the chin,
 * so those are estimated from the eye-to-mouth distance by typical head proportions; hair and
 * unusual faces may need a small touch by hand.
 */
object FaceCrop {
    /**
     * From the eyes up to the top of the head, in eye-to-mouth distances (short hair included).
     * This and [CHIN_BELOW_EYES] were checked on a few portraits; tune them on the shop's photos.
     */
    const val CROWN_ABOVE_EYES = 1.7

    /** From the eyes down to the chin, in eye-to-mouth distances. */
    const val CHIN_BELOW_EYES = 1.85

    /** The customer's face: the largest one on the photo, or null when there is none. */
    fun main(faces: List<Face>): Face? = faces.maxByOrNull { it.area }

    /** The crop of an [imageWidth] × [imageHeight] photo that puts [face] on [preset]'s guide, or null for a face it can't read. */
    fun fit(imageWidth: Int, imageHeight: Int, preset: PhotoSizePreset, face: Face): FaceFit? {
        val eyes = mid(face.eyeLeft, face.eyeRight)
        val mouth = mid(face.mouthLeft, face.mouthRight)
        // How far the eyes are from level; the photo is turned the other way to straighten them.
        val tilt = atan2(face.eyeRight.y - face.eyeLeft.y, face.eyeRight.x - face.eyeLeft.x)
        if (abs(tilt) > CropGeometry.MAX_ROTATION * PI / 180) return null
        // "Down" along the face, square to the eye line.
        val downX = -sin(tilt)
        val downY = cos(tilt)
        val eyesToMouth = (mouth.x - eyes.x) * downX + (mouth.y - eyes.y) * downY
        if (eyesToMouth <= 0) return null

        val guide = preset.guide
        val crownX = eyes.x - CROWN_ABOVE_EYES * eyesToMouth * downX
        val crownY = eyes.y - CROWN_ABOVE_EYES * eyesToMouth * downY
        val head = (CROWN_ABOVE_EYES + CHIN_BELOW_EYES) * eyesToMouth
        val frameHeight = head / (guide.chin - guide.headTop)
        val toCentre = (0.5 - guide.headTop) * frameHeight
        val centreX = (crownX + toCentre * downX) / imageWidth
        val centreY = (crownY + toCentre * downY) / imageHeight
        val scale = CropGeometry.rect(imageWidth, imageHeight, preset.aspect, CropConfig()).height / frameHeight

        val wanted = CropConfig(
            centerX = centreX.coerceIn(0.0, 1.0),
            centerY = centreY.coerceIn(0.0, 1.0),
            scale = scale.coerceIn(1.0, CropGeometry.MAX_SCALE),
            rotationDegrees = -tilt * 180 / PI + 0.0, // no -0.0 in saved recipes
        )
        val crop = CropGeometry.clamp(imageWidth, imageHeight, preset.aspect, wanted)
        val fits = centreX in 0.0..1.0 && centreY in 0.0..1.0 && scale in 1.0..CropGeometry.MAX_SCALE && crop.near(wanted)
        return FaceFit(crop, fits)
    }

    private fun mid(a: PointPx, b: PointPx) = PointPx((a.x + b.x) / 2, (a.y + b.y) / 2)

    private fun CropConfig.near(other: CropConfig) =
        abs(centerX - other.centerX) < 1e-6 && abs(centerY - other.centerY) < 1e-6 &&
            abs(scale - other.scale) < 1e-6 && abs(rotationDegrees - other.rotationDegrees) < 1e-6
}

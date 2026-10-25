package com.tlw.photodokan.core.quality

import com.tlw.photodokan.core.face.Face
import com.tlw.photodokan.core.imaging.AutoLight
import com.tlw.photodokan.core.matte.Matte
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * What can go wrong when a photo goes through Photo Dokan's automatic steps, as the quality report
 * groups them. Each is something the operator would have to fix by hand.
 */
enum class Problem(val title: String, val meaning: String) {
    UNREADABLE("Can't be opened", "The file isn't a photo Photo Dokan can read."),
    FAILED("Processing failed", "A step threw an error; the message is in the report."),
    NO_FACE("No face found", "The crop can't be fitted; the operator crops by hand."),
    SEVERAL_FACES("Several faces", "Another face nearly as big as the customer's: the wrong one may be cropped."),
    HEAD_AT_EDGE("Head at the edge", "The head is too close to the photo's edge to sit on the guide."),
    HEAD_TILTED("Head tilted", "The crop had to turn the photo a lot to level the eyes."),
    LOW_RESOLUTION("Too few pixels", "The crop has fewer pixels than a 300 dpi print needs."),
    SKIN_UNREADABLE("Skin can't be judged", "Too little of the face's skin shows to set light and colour."),
    POOR_LIGHT("Badly exposed", "Auto light changed the brightness by half a stop or more."),
    COLOUR_CAST("Strong colour cast", "Auto light moved warmth or tint by half the slider or more."),
    CUTOUT_MISSES_FACE("Cut-out misses the face", "Part of the face was taken for background."),
    CUTOUT_KEEPS_BACKGROUND("Cut-out keeps background", "The top corners, usually backdrop, were taken for the person."),
    ROUGH_EDGE("Unsure edge", "The cut-out's edge is wide and soft: hair or a busy backdrop confused it."),
    SLOW("Slow", "The automatic steps took longer than the operator should wait."),
}

/** How the cut-out looks for a photo with a face: each a share, 0 to 1. */
data class CutoutStats(
    /** How much of the skin between the eyes and the mouth is the person. */
    val faceCovered: Double,
    /** How much of the photo's top corners, usually backdrop, is the person. */
    val cornersKept: Double,
    /** How wide the unsure band along the person's outline is, as a share of the photo's height. */
    val edgeWidth: Double,
) {
    companion object {
        /** [matte]'s stats for [face], found on a [width] × [height] photo. */
        fun of(matte: Matte, face: Face, width: Int, height: Int): CutoutStats {
            // The face between the eyes and the mouth, in matte pixels.
            val sx = matte.width.toDouble() / width
            val sy = matte.height.toDouble() / height
            val left = (min(face.eyeLeft.x, face.mouthLeft.x) * sx).roundToInt().coerceIn(0, matte.width - 1)
            val right = (max(face.eyeRight.x, face.mouthRight.x) * sx).roundToInt().coerceIn(left, matte.width - 1)
            val top = ((face.eyeLeft.y + face.eyeRight.y) / 2 * sy).roundToInt().coerceIn(0, matte.height - 1)
            val bottom = ((face.mouthLeft.y + face.mouthRight.y) / 2 * sy).roundToInt().coerceIn(top, matte.height - 1)
            val faceCovered = mean(matte, left..right, top..bottom)

            // The top tenth of the photo, its outer sixth on each side.
            val cornerRows = 0 until max(1, matte.height / 10)
            val cornerWidth = max(1, matte.width / 6)
            val cornersKept = (mean(matte, 0 until cornerWidth, cornerRows) + mean(matte, matte.width - cornerWidth until matte.width, cornerRows)) / 2

            // Unsure pixels for each pixel of outline: how wide the soft band is.
            var unsure = 0
            var outline = 0
            for (y in 0 until matte.height) for (x in 0 until matte.width) {
                val a = matte[x, y]
                if (a in UNSURE_LOW..UNSURE_HIGH) unsure++
                if (x + 1 < matte.width && (a >= 128) != (matte[x + 1, y] >= 128)) outline++
                if (y + 1 < matte.height && (a >= 128) != (matte[x, y + 1] >= 128)) outline++
            }
            val edgeWidth = if (outline == 0) 0.0 else unsure.toDouble() / outline / matte.height
            return CutoutStats(faceCovered, cornersKept, edgeWidth)
        }

        private const val UNSURE_LOW = 26
        private const val UNSURE_HIGH = 229

        private fun mean(matte: Matte, xs: IntRange, ys: IntRange): Double {
            var sum = 0L
            var n = 0
            for (y in ys) for (x in xs) {
                sum += matte[x, y]
                n++
            }
            return if (n == 0) 0.0 else sum / 255.0 / n
        }
    }
}

/**
 * What the automatic steps found on one photo, with a face or without one. Null values are steps
 * that didn't run: no face to fit, or no matter to cut out with.
 */
data class Findings(
    /** The faces found, the customer's first. */
    val faces: List<Face>,
    /** Whether the head fits the photo size's guide, once cropped to it. */
    val headFits: Boolean? = null,
    /** How far the crop turned the photo to level the eyes, in degrees. */
    val tiltDegrees: Double? = null,
    /** The crop's height in pixels of the original photo, and how many a 300 dpi print needs. */
    val cropPixels: Double? = null,
    val neededPixels: Double? = null,
    val light: AutoLight.Suggestion? = null,
    val cutout: CutoutStats? = null,
    /** How long the automatic steps took, in milliseconds. */
    val millis: Long = 0,
)

/** Turns what was found on a photo into the problems the report lists. */
object QualityChecks {
    /** Other faces at least this share of the customer's face's area could be cropped instead. */
    const val OTHER_FACE = 0.25
    const val TILT_DEGREES = 8.0
    /** Half a stop of light, or half the warmth or tint slider. */
    const val BIG_CHANGE = 0.5
    const val FACE_COVERED = 0.9
    const val CORNERS_KEPT = 0.2
    /**
     * An unsure band wider than 1.2 % of the photo's height: about twice the widest on good
     * portraits so far. Check it on the shop's photos.
     */
    const val EDGE_WIDTH = 0.012
    const val SLOW_MILLIS = 3_000L

    fun problems(found: Findings): List<Problem> = buildList {
        val face = found.faces.firstOrNull()
        if (face == null) {
            add(Problem.NO_FACE)
        } else {
            if (found.faces.drop(1).any { it.area >= OTHER_FACE * face.area }) add(Problem.SEVERAL_FACES)
            if (found.headFits == false) add(Problem.HEAD_AT_EDGE)
            if (found.tiltDegrees != null && abs(found.tiltDegrees) > TILT_DEGREES) add(Problem.HEAD_TILTED)
            if (found.cropPixels != null && found.neededPixels != null && found.cropPixels < found.neededPixels) add(Problem.LOW_RESOLUTION)
            val light = found.light
            if (light == null) {
                add(Problem.SKIN_UNREADABLE)
            } else {
                if (abs(light.exposure) >= BIG_CHANGE) add(Problem.POOR_LIGHT)
                if (abs(light.warmth) >= BIG_CHANGE || abs(light.tint) >= BIG_CHANGE) add(Problem.COLOUR_CAST)
            }
        }
        found.cutout?.let { cutout ->
            if (face != null && cutout.faceCovered < FACE_COVERED) add(Problem.CUTOUT_MISSES_FACE)
            if (cutout.cornersKept > CORNERS_KEPT) add(Problem.CUTOUT_KEEPS_BACKGROUND)
            if (cutout.edgeWidth > EDGE_WIDTH) add(Problem.ROUGH_EDGE)
        }
        if (found.millis > SLOW_MILLIS) add(Problem.SLOW)
    }
}

package com.tlw.photodokan.core.face

/** A point on a photo, in its pixels: x to the right, y down. */
data class PointPx(val x: Double, val y: Double)

/**
 * A face found on a photo, in that photo's pixels: its box, and its eyes, nose tip and mouth
 * corners. Left and right are as the photo shows them, so [eyeLeft] is the eye nearer the photo's
 * left edge. [score] is how sure the detector is, 0 to 1.
 */
data class Face(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val eyeLeft: PointPx,
    val eyeRight: PointPx,
    val nose: PointPx,
    val mouthLeft: PointPx,
    val mouthRight: PointPx,
    val score: Double,
) {
    val area: Double get() = width * height
}

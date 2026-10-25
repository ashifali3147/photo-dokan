package com.tlw.photodokan.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class BrushMode {
    /** Paints the person back where the cut-out missed them: hair, an ear, a shoulder. */
    KEEP,

    /** Paints the background where the cut-out kept some of it. */
    ERASE,
}

/** A point on the upright photo, as fractions of its width and height. */
@Serializable
data class MaskPoint(val x: Double, val y: Double)

/**
 * One stroke of the touch-up brush over the cut-out: where the brush went and how big it was.
 * Kept with the recipe, so it is redone on the full-size photo for printing and can be undone.
 *
 * @param radius the brush's radius as a share of the photo's height.
 */
@Serializable
data class MaskStroke(val mode: BrushMode, val radius: Double, val points: List<MaskPoint>) {
    init {
        require(radius > 0 && radius <= 0.5) { "A brush radius must be in (0, 0.5] of the photo's height, was $radius" }
        require(points.isNotEmpty()) { "A stroke needs at least one point" }
    }
}

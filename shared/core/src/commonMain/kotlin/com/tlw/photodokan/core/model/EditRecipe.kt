package com.tlw.photodokan.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class BackgroundMode {
    /** Leave the background exactly as shot. */
    KEEP,

    /** Keep the studio backdrop and blur it softly. The studio default. */
    BLUR,

    /** Replace the background with a solid colour. */
    REPLACE,
}

@Serializable
data class BackgroundConfig(
    val mode: BackgroundMode = BackgroundMode.BLUR,
    /** 0 = no blur, 1 = strongest blur. */
    val blurStrength: Double = 0.35,
    /** ARGB, e.g. 0xFFFFFFFF for white. Used when [mode] is REPLACE. */
    val replaceColorArgb: Long = WHITE,
    /** The operator's fixes to the cut-out, in the order they were painted. */
    val touchUps: List<MaskStroke> = emptyList(),
) {
    init {
        require(blurStrength in 0.0..1.0) { "Blur strength must be in 0..1, was $blurStrength" }
    }

    companion object {
        const val WHITE = 0xFFFFFFFF
        const val LIGHT_BLUE = 0xFFDCE9F7
    }
}

/** Crop in normalised image coordinates: centre in 0..1, scale ≥ 1 zooms in. */
@Serializable
data class CropConfig(
    val centerX: Double = 0.5,
    val centerY: Double = 0.5,
    val scale: Double = 1.0,
    val rotationDegrees: Double = 0.0,
) {
    init {
        require(centerX in 0.0..1.0 && centerY in 0.0..1.0) { "Crop centre must be in 0..1" }
        require(scale >= 1.0) { "Crop scale must be at least 1, was $scale" }
        require(rotationDegrees in -45.0..45.0) { "Rotation must be within ±45°, was $rotationDegrees" }
    }
}

/**
 * Every edit as data. The original photo is never modified; the recipe is re-applied for
 * preview (reduced size) and for printing (full resolution). Adjustments run from -1 to 1.
 */
@Serializable
data class EditRecipe(
    val presetId: String,
    val crop: CropConfig = CropConfig(),
    val exposure: Double = 0.0,
    val contrast: Double = 0.0,
    val warmth: Double = 0.0,
    /** Green (−1) to pink (+1): the other half of white balance, with [warmth]. */
    val tint: Double = 0.0,
    val saturation: Double = 0.0,
    val background: BackgroundConfig = BackgroundConfig(),
    /** 0 = off (default), 1 = strongest gentle smoothing. */
    val retouch: Double = 0.0,
    /** The studio look, 0 (off) to 1 (full): bright, clean and crisp, as a studio edits a portrait. */
    val enhance: Double = 0.0,
    /** The face as measured when the photo was opened, which the studio look works from; null without one. */
    val portrait: Portrait? = null,
) {
    init {
        for ((name, value) in listOf("exposure" to exposure, "contrast" to contrast, "warmth" to warmth, "tint" to tint, "saturation" to saturation)) {
            require(value in -1.0..1.0) { "$name must be in -1..1, was $value" }
        }
        require(retouch in 0.0..1.0) { "retouch must be in 0..1, was $retouch" }
        require(enhance in 0.0..1.0) { "enhance must be in 0..1, was $enhance" }
    }
}

/**
 * A portrait's face, measured once when the photo is opened and kept with the recipe, so the
 * studio look comes out the same on the preview, final.jpg and the full-size print.
 */
@Serializable
data class Portrait(
    /** How bright the face's skin is in the middle, 0 to 1. */
    val skinLevel: Double,
    /** The face's height as a share of the photo's height. */
    val faceHeight: Double,
) {
    init {
        require(skinLevel in 0.0..1.0) { "Skin level must be in 0..1, was $skinLevel" }
        require(faceHeight > 0 && faceHeight <= 1) { "Face height must be in (0, 1] of the photo, was $faceHeight" }
    }
}

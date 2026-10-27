package com.tlw.photodokan.core.model

import com.tlw.photodokan.core.geometry.SizeMm
import kotlinx.serialization.Serializable

/**
 * Where the head should sit in the photo, as fractions of its height from the top edge: the top
 * of the head at [headTop] and the chin at [chin]. Only a visual aid in the editor.
 */
@Serializable
data class FaceGuide(val headTop: Double = 0.10, val chin: Double = 0.78) {
    init {
        require(headTop in 0.0..1.0 && chin in 0.0..1.0 && headTop < chin) { "Guide needs 0 ≤ headTop < chin ≤ 1, was $headTop, $chin" }
    }
}

/** A printable photo size. Presets are editable starting points, never "official" sizes. */
@Serializable
data class PhotoSizePreset(
    val id: String,
    val name: String,
    val size: SizeMm,
    val guide: FaceGuide = FaceGuide(),
) {
    init {
        require(id.isNotBlank()) { "Preset id must not be blank" }
    }

    /** Width / height: the crop frame's shape. */
    val aspect: Double get() = size.width / size.height

    companion object {
        val PASSPORT_35X45 = PhotoSizePreset("passport-35x45", "Passport 35 × 45 mm", SizeMm(35.0, 45.0))
        val STAMP_20X25 = PhotoSizePreset("stamp-20x25", "Stamp 20 × 25 mm", SizeMm(20.0, 25.0))

        // TODO(phase 0): replace with the measured slot size of the studio's 5 × 8 sheet.
        // The head a little smaller than on a passport photo, with the shoulders in, as the studio frames it.
        val STUDIO_SLOT = PhotoSizePreset("studio-slot", "Studio slot (5 × 8 sheet)", SizeMm(36.0, 34.0), FaceGuide(headTop = 0.09, chin = 0.72))

        val DEFAULTS = listOf(STUDIO_SLOT, PASSPORT_35X45, STAMP_20X25)
    }
}

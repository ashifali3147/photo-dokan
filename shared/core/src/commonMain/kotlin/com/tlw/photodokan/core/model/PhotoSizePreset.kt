package com.tlw.photodokan.core.model

import com.tlw.photodokan.core.geometry.SizeMm
import kotlinx.serialization.Serializable

/** A printable photo size. Presets are editable starting points, never "official" sizes. */
@Serializable
data class PhotoSizePreset(
    val id: String,
    val name: String,
    val size: SizeMm,
) {
    init {
        require(id.isNotBlank()) { "Preset id must not be blank" }
    }

    companion object {
        val PASSPORT_35X45 = PhotoSizePreset("passport-35x45", "Passport 35 × 45 mm", SizeMm(35.0, 45.0))
        val STAMP_20X25 = PhotoSizePreset("stamp-20x25", "Stamp 20 × 25 mm", SizeMm(20.0, 25.0))

        // TODO(phase 0): replace with the measured slot size of the studio's 5 × 8 sheet.
        val STUDIO_SLOT = PhotoSizePreset("studio-slot", "Studio slot (5 × 8 sheet)", SizeMm(36.0, 34.0))

        val DEFAULTS = listOf(STUDIO_SLOT, PASSPORT_35X45, STAMP_20X25)
    }
}

package com.tlw.photodokan.core.geometry

import kotlinx.serialization.Serializable

/** Millimetres are the source of truth everywhere; points and pixels exist only at render time. */
const val MM_PER_INCH = 25.4
const val POINTS_PER_INCH = 72.0

internal const val EPSILON_MM = 1e-6

fun mmToPoints(mm: Double): Double = mm * POINTS_PER_INCH / MM_PER_INCH

fun pointsToMm(points: Double): Double = points * MM_PER_INCH / POINTS_PER_INCH

fun mmToPixels(mm: Double, dpi: Double): Double {
    require(dpi > 0) { "dpi must be positive, was $dpi" }
    return mm * dpi / MM_PER_INCH
}

@Serializable
data class SizeMm(val width: Double, val height: Double) {
    init {
        require(width > 0 && height > 0) { "Size must be positive, was $width × $height mm" }
    }

    fun rotated(): SizeMm = SizeMm(height, width)
}

@Serializable
data class RectMm(val x: Double, val y: Double, val width: Double, val height: Double) {
    init {
        require(width > 0 && height > 0) { "Rect must have positive size, was $width × $height mm" }
    }

    val right: Double get() = x + width
    val bottom: Double get() = y + height

    fun overlaps(other: RectMm): Boolean =
        x < other.right - EPSILON_MM && other.x < right - EPSILON_MM &&
            y < other.bottom - EPSILON_MM && other.y < bottom - EPSILON_MM

    fun isInside(container: SizeMm, marginMm: Double = 0.0): Boolean =
        x >= marginMm - EPSILON_MM && y >= marginMm - EPSILON_MM &&
            right <= container.width - marginMm + EPSILON_MM &&
            bottom <= container.height - marginMm + EPSILON_MM
}

@Serializable
data class LineMm(val x1: Double, val y1: Double, val x2: Double, val y2: Double)

object PaperSizes {
    val A4 = SizeMm(210.0, 297.0)
    val PHOTO_4X6 = SizeMm(101.6, 152.4)
}

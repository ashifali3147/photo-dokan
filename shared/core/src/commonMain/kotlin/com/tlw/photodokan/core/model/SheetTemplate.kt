package com.tlw.photodokan.core.model

import com.tlw.photodokan.core.geometry.EPSILON_MM
import com.tlw.photodokan.core.geometry.LineMm
import com.tlw.photodokan.core.geometry.PaperSizes
import com.tlw.photodokan.core.geometry.RectMm
import com.tlw.photodokan.core.geometry.SizeMm
import kotlinx.serialization.Serializable

/**
 * A sheet of photo paper divided into a grid of equal photo slots, centred on the page.
 * Slots are numbered in reading order: left to right, top to bottom, starting at 0.
 */
@Serializable
data class SheetTemplate(
    val name: String,
    val paper: SizeMm,
    val photo: SizeMm,
    val columns: Int,
    val rows: Int,
    val gapMm: Double,
    val minMarginMm: Double = 3.0,
    val cutLines: Boolean = true,
) {
    init {
        require(columns > 0 && rows > 0) { "Grid must have at least one column and row" }
        require(gapMm >= 0) { "Gap must not be negative" }
        require(minMarginMm >= 0) { "Margin must not be negative" }
        require(gridWidth <= paper.width - 2 * minMarginMm + EPSILON_MM) {
            "$columns columns of ${photo.width} mm need $gridWidth mm, wider than the printable ${paper.width - 2 * minMarginMm} mm"
        }
        require(gridHeight <= paper.height - 2 * minMarginMm + EPSILON_MM) {
            "$rows rows of ${photo.height} mm need $gridHeight mm, taller than the printable ${paper.height - 2 * minMarginMm} mm"
        }
    }

    val capacity: Int get() = columns * rows
    val gridWidth: Double get() = columns * photo.width + (columns - 1) * gapMm
    val gridHeight: Double get() = rows * photo.height + (rows - 1) * gapMm
    val originX: Double get() = (paper.width - gridWidth) / 2
    val originY: Double get() = (paper.height - gridHeight) / 2

    fun slotRect(index: Int): RectMm {
        require(index in 0 until capacity) { "Slot $index is outside 0 until $capacity" }
        val column = index % columns
        val row = index / columns
        return RectMm(
            x = originX + column * (photo.width + gapMm),
            y = originY + row * (photo.height + gapMm),
            width = photo.width,
            height = photo.height,
        )
    }

    /** Cut guides along the centre of every gap and around the grid's outer edge. */
    fun cutLineSegments(): List<LineMm> {
        if (!cutLines) return emptyList()
        val half = gapMm / 2
        val top = originY - half
        val bottom = originY + gridHeight + half
        val left = originX - half
        val right = originX + gridWidth + half
        val vertical = (0..columns).map { c ->
            val x = originX + c * (photo.width + gapMm) - half
            LineMm(x, top, x, bottom)
        }
        val horizontal = (0..rows).map { r ->
            val y = originY + r * (photo.height + gapMm) - half
            LineMm(left, y, right, y)
        }
        return vertical + horizontal
    }

    companion object {
        /** The studio's Camedia Master layout: A4 photo paper, 5 columns × 8 rows. */
        val STUDIO_A4_5X8 = SheetTemplate(
            name = "Studio A4 5 × 8",
            paper = PaperSizes.A4,
            photo = PhotoSizePreset.STUDIO_SLOT.size,
            columns = 5,
            rows = 8,
            gapMm = 2.0,
        )
    }
}

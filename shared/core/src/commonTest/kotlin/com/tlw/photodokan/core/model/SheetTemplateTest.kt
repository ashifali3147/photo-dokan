package com.tlw.photodokan.core.model

import com.tlw.photodokan.core.geometry.PaperSizes
import com.tlw.photodokan.core.geometry.SizeMm
import com.tlw.photodokan.core.geometry.mmToPixels
import com.tlw.photodokan.core.geometry.mmToPoints
import com.tlw.photodokan.core.geometry.pointsToMm
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SheetTemplateTest {

    private fun close(expected: Double, actual: Double, tolerance: Double = 1e-6) =
        assertTrue(abs(expected - actual) < tolerance, "expected $expected, was $actual")

    @Test
    fun a4ConvertsToStandardPoints() {
        close(595.2756, mmToPoints(210.0), 1e-3)
        close(841.8898, mmToPoints(297.0), 1e-3)
        close(210.0, pointsToMm(mmToPoints(210.0)))
    }

    @Test
    fun a4At300DpiIsAbout2480Pixels() {
        close(2480.3, mmToPixels(210.0, 300.0), 0.1)
    }

    @Test
    fun studioSheetHasFortySlotsCentredOnA4() {
        val t = SheetTemplate.STUDIO_A4_5X8
        assertEquals(40, t.capacity)
        close(t.originX, PaperSizes.A4.width - (t.originX + t.gridWidth))
        close(t.originY, PaperSizes.A4.height - (t.originY + t.gridHeight))
    }

    @Test
    fun slotsAreInReadingOrder() {
        val t = SheetTemplate.STUDIO_A4_5X8
        val first = t.slotRect(0)
        val second = t.slotRect(1)
        val sixth = t.slotRect(5)
        close(first.right + t.gapMm, second.x)
        close(first.y, second.y)
        close(first.x, sixth.x)
        close(first.bottom + t.gapMm, sixth.y)
        close(t.photo.width, first.width)
        close(t.photo.height, first.height)
    }

    @Test
    fun rejectsGridWiderThanThePaper() {
        assertFailsWith<IllegalArgumentException> {
            SheetTemplate("too wide", PaperSizes.A4, SizeMm(45.0, 35.0), 5, 2, 2.0)
        }
    }

    @Test
    fun rejectsSlotOutsideTheGrid() {
        assertFailsWith<IllegalArgumentException> { SheetTemplate.STUDIO_A4_5X8.slotRect(40) }
    }

    @Test
    fun cutLinesRunThroughEveryGapAndTheOuterEdge() {
        val t = SheetTemplate.STUDIO_A4_5X8
        assertEquals((t.columns + 1) + (t.rows + 1), t.cutLineSegments().size)
        assertTrue(t.copy(cutLines = false).cutLineSegments().isEmpty())
    }
}

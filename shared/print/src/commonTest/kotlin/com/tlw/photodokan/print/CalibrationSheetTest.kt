package com.tlw.photodokan.print

import com.tlw.photodokan.core.geometry.PaperSizes
import com.tlw.photodokan.core.geometry.SizeMm
import com.tlw.photodokan.core.model.PhotoSizePreset
import com.tlw.photodokan.print.CalibrationSheet.LEFT_RULER_FROM
import com.tlw.photodokan.print.CalibrationSheet.LEFT_RULER_TO
import com.tlw.photodokan.print.CalibrationSheet.LEFT_RULER_X
import com.tlw.photodokan.print.CalibrationSheet.SQUARE
import com.tlw.photodokan.print.CalibrationSheet.TOP_RULER_FROM
import com.tlw.photodokan.print.CalibrationSheet.TOP_RULER_TO
import com.tlw.photodokan.print.CalibrationSheet.TOP_RULER_Y
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CalibrationSheetTest {

    private val page = CalibrationSheet.page()
    private val lines = page.items.filterIsInstance<PageItem.Line>().map { it.line }
    private val boxes = page.items.filterIsInstance<PageItem.Box>().map { it.rect }
    private val texts = page.items.filterIsInstance<PageItem.Text>()

    @Test
    fun isA4WithAnExact100mmSquare() {
        assertEquals(PaperSizes.A4, page.paper)
        assertTrue(SQUARE in boxes)
        assertEquals(100.0, SQUARE.width)
        assertEquals(100.0, SQUARE.height)
        assertTrue(texts.any { it.text == "100 × 100 mm" })
    }

    @Test
    fun topRulerHasATickEveryMillimetreFromThePaperEdge() {
        val ticks = lines.filter { it.x1 == it.x2 && it.y1 == TOP_RULER_Y && it.y2 - it.y1 <= 5.0 }
        assertEquals((TOP_RULER_FROM..TOP_RULER_TO).map(Int::toDouble), ticks.map { it.x1 })
        for (tick in ticks) {
            val length = tick.y2 - tick.y1
            val mm = tick.x1.toInt()
            assertEquals(if (mm % 10 == 0) 5.0 else if (mm % 5 == 0) 3.0 else 1.5, length, "tick at $mm mm")
        }
    }

    @Test
    fun leftRulerHasATickEveryMillimetreFromThePaperEdge() {
        val ticks = lines.filter { it.y1 == it.y2 && it.x1 == LEFT_RULER_X && it.x2 - it.x1 <= 5.0 }
        assertEquals((LEFT_RULER_FROM..LEFT_RULER_TO).map(Int::toDouble), ticks.map { it.y1 })
    }

    @Test
    fun rulerNumbersAreTheDistanceFromThePaperEdge() {
        val topLabels = texts.filter { it.baselineY > TOP_RULER_Y && it.baselineY < TOP_RULER_Y + 10 }
        assertEquals((TOP_RULER_FROM..TOP_RULER_TO step 10).map(Int::toString), topLabels.map { it.text })
        for (label in topLabels) assertEquals(label.text.toDouble(), label.x - 0.4, 1e-9)

        val leftLabels = texts.filter { it.x > LEFT_RULER_X && it.x < LEFT_RULER_X + 10 && it.baselineY > LEFT_RULER_FROM }
        assertEquals((LEFT_RULER_FROM..LEFT_RULER_TO step 10).map(Int::toString), leftLabels.map { it.text })
    }

    @Test
    fun everyPhotoSizeHasABoxOfThatSize() {
        for (preset in PhotoSizePreset.DEFAULTS) {
            assertTrue(boxes.any { it.width == preset.size.width && it.height == preset.size.height }, preset.name)
            assertTrue(texts.any { it.text == preset.name }, preset.name)
        }
        // Sizes are spelled out once: under names that lack them, never repeated under names that have them.
        assertTrue(texts.any { it.text == "36 × 34 mm" })
        assertTrue(texts.none { it.text == "35 × 45 mm" })
    }

    @Test
    fun boxesDoNotOverlapEachOtherOrTheRulers() {
        for ((i, a) in boxes.withIndex()) for (b in boxes.drop(i + 1)) assertTrue(!a.overlaps(b), "$a overlaps $b")
        for (box in boxes) {
            assertTrue(box.y > TOP_RULER_Y + 10, "$box is under the top ruler")
            assertTrue(box.x > LEFT_RULER_X + 15, "$box is over the left ruler")
        }
    }

    @Test
    fun everythingStaysInsideThePrintableArea() {
        val margin = 3.0
        for (box in boxes) assertTrue(box.isInside(page.paper, margin), "$box")
        for (l in lines) {
            assertTrue(minOf(l.x1, l.x2) >= margin && maxOf(l.x1, l.x2) <= page.paper.width - margin, "$l")
            assertTrue(minOf(l.y1, l.y2) >= margin && maxOf(l.y1, l.y2) <= page.paper.height - margin, "$l")
        }
        for (t in texts) assertTrue(t.x >= margin && t.baselineY in margin..page.paper.height - margin, t.text)
    }

    @Test
    fun rejectsPhotoSizesThatDoNotFitAcross() {
        val wide = PhotoSizePreset("wide", "Wide", SizeMm(120.0, 40.0))
        assertFailsWith<IllegalArgumentException> { CalibrationSheet.page(listOf(wide, wide)) }
    }
}

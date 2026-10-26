package com.tlw.photodokan.print

import com.tlw.photodokan.core.geometry.PaperSizes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToneStripTest {

    @Test
    fun everyToneHasOnePatchNumber() {
        val tones = (1..ToneStrip.PATCHES).map { ToneStrip.toneOf(it)!! }
        assertEquals(ToneStrip.PATCHES, tones.toSet().size)
        assertEquals(PrintTone.NONE, ToneStrip.toneOf(1))
        assertEquals(PrintTone(PrintTone.MAX_BRIGHTNESS, PrintTone.MAX_COLOUR), ToneStrip.toneOf(ToneStrip.PATCHES))
        for ((i, tone) in tones.withIndex()) assertEquals(i + 1, ToneStrip.numberOf(tone))
        assertNull(ToneStrip.toneOf(0))
        assertNull(ToneStrip.toneOf(ToneStrip.PATCHES + 1))
    }

    @Test
    fun theChartIsTheSameOnScreenAndInTheFirstPatch() {
        val chart = ToneStrip.chart()
        assertEquals(ToneStrip.CHART_PX * ToneStrip.CHART_PX, chart.size)
        assertTrue(chart.all { it ushr 24 == 0xFF }, "opaque")
        val tone = PrintTone(2, 1)
        assertContentEquals(chart.map(tone::apply).toIntArray(), ToneStrip.chart(tone))
    }

    @Test
    fun printsEveryPatchWithItsToneAndNumberOnA4() {
        val made = mutableListOf<IntArray>()
        val page = ToneStrip.page { argb, w, h ->
            assertEquals(ToneStrip.CHART_PX, w)
            assertEquals(ToneStrip.CHART_PX, h)
            made += argb
            byteArrayOf(made.size.toByte())
        }
        assertEquals(PaperSizes.A4, page.paper)
        val images = page.items.filterIsInstance<PageItem.Image>()
        assertEquals(ToneStrip.PATCHES, images.size)
        for (number in 1..ToneStrip.PATCHES) {
            assertContentEquals(ToneStrip.chart(ToneStrip.toneOf(number)!!), made[number - 1])
        }
        val labels = page.items.filterIsInstance<PageItem.Text>().map { it.text }
        for (number in 1..ToneStrip.PATCHES) assertTrue(number.toString() in labels, "label $number")
    }

    @Test
    fun patchesStayInsideThePrintableAreaAndApart() {
        val rects = ToneStrip.page { _, _, _ -> byteArrayOf(1) }.items.filterIsInstance<PageItem.Image>().map { it.rect }
        for (r in rects) {
            assertTrue(r.x >= 10 && r.y >= 10 && r.right <= PaperSizes.A4.width - 10 && r.bottom <= PaperSizes.A4.height - 10, "$r")
        }
        for (a in rects) for (b in rects) {
            if (a !== b) assertTrue(a.right <= b.x || b.right <= a.x || a.bottom <= b.y || b.bottom <= a.y, "$a overlaps $b")
        }
    }
}

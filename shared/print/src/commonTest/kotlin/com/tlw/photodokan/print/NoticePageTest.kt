package com.tlw.photodokan.print

import com.tlw.photodokan.core.geometry.PaperSizes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoticePageTest {

    @Test
    fun linesBreakAtSpaces() {
        assertEquals(listOf("Choose A4", "paper, upright."), NoticePage.wrap("Choose A4 paper, upright.", 15))
        assertEquals(listOf("one", "unbreakablewordhere", "two"), NoticePage.wrap("one unbreakablewordhere two", 10))
        assertEquals(emptyList(), NoticePage.wrap("   ", 10))
    }

    @Test
    fun theNoticeSaysWhatToChangeOnThePaperChosen() {
        val problem = "The printer is set to 216 × 279 mm paper, but this sheet needs 210 × 297 mm. Choose that paper size, upright (portrait), in the printer settings."
        val page = NoticePage.page(PaperSizes.A4, problem, "Then the sheet appears here.")
        assertEquals(PaperSizes.A4, page.paper)
        val lines = page.items.filterIsInstance<PageItem.Text>()
        assertEquals(NoticePage.TITLE, lines.first().text)
        assertEquals(problem + " Then the sheet appears here.", lines.drop(1).joinToString(" ") { it.text })
        assertTrue(lines.zipWithNext().all { (a, b) -> b.baselineY > a.baselineY }, "each line below the last")
        assertTrue(lines.all { it.baselineY < PaperSizes.A4.height }, "all on the page")
    }
}

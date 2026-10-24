package com.tlw.photodokan.print

import com.tlw.photodokan.core.geometry.SizeMm

/**
 * A page of plain text shown in a print preview in place of the sheet, saying why the sheet can't
 * be printed with the settings chosen, so the operator changes them before wasting photo paper.
 */
object NoticePage {

    const val TITLE = "Don't print this page."

    /** [problem] and [advice] on a page of [paper] size, wrapped to fit. */
    fun page(paper: SizeMm, problem: String, advice: String): PrintPage {
        val items = mutableListOf<PageItem>()
        var y = TOP_MM
        items += PageItem.Text(LEFT_MM, y, TITLE_SIZE_MM, TITLE)
        y += TITLE_SIZE_MM * 2
        for (paragraph in listOf(problem, advice)) {
            for (line in wrap(paragraph, maxChars(paper))) {
                items += PageItem.Text(LEFT_MM, y, TEXT_SIZE_MM, line)
                y += TEXT_SIZE_MM * LINE_SPACING
            }
            y += TEXT_SIZE_MM
        }
        return PrintPage(paper, items)
    }

    /** Splits [text] at spaces into lines of at most [maxChars] characters; a longer word gets a line of its own. */
    fun wrap(text: String, maxChars: Int): List<String> {
        val lines = mutableListOf<String>()
        var line = ""
        for (word in text.split(' ').filter { it.isNotEmpty() }) {
            line = when {
                line.isEmpty() -> word
                line.length + 1 + word.length <= maxChars -> "$line $word"
                else -> {
                    lines += line
                    word
                }
            }
        }
        if (line.isNotEmpty()) lines += line
        return lines
    }

    /** Helvetica averages about half its size per character; a little less keeps lines inside the margins. */
    private fun maxChars(paper: SizeMm) = ((paper.width - 2 * LEFT_MM) / (TEXT_SIZE_MM * 0.55)).toInt().coerceAtLeast(20)

    private const val LEFT_MM = 15.0
    private const val TOP_MM = 30.0
    private const val TITLE_SIZE_MM = 12.0
    private const val TEXT_SIZE_MM = 7.0
    private const val LINE_SPACING = 1.5
}

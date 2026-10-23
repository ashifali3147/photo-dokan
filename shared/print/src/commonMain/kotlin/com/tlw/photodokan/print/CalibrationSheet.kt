package com.tlw.photodokan.print

import com.tlw.photodokan.core.geometry.LineMm
import com.tlw.photodokan.core.geometry.PaperSizes
import com.tlw.photodokan.core.geometry.RectMm
import com.tlw.photodokan.core.model.PhotoSizePreset

/**
 * The A4 calibration sheet. The operator prints it at actual size and measures it with a steel ruler:
 * - a 100 × 100 mm square shows the printer's scale in X and Y;
 * - rulers along the top and left edges are numbered by their distance from the paper edge, so a
 *   ruler held against the edge shows the printer's offset as well;
 * - one box per photo size shows what the customer's photo will measure.
 */
object CalibrationSheet {
    internal const val TOP_RULER_Y = 20.0
    internal const val TOP_RULER_FROM = 10
    internal const val TOP_RULER_TO = 200
    internal const val LEFT_RULER_X = 10.0
    internal const val LEFT_RULER_FROM = 40
    internal const val LEFT_RULER_TO = 280
    /** The square the operator measures; its size and distance from the paper edges are what a correction is worked out from. */
    val SQUARE = RectMm(60.0, 45.0, 100.0, 100.0)

    private const val TICK_MM = 1.5
    private const val TICK_HALF = 3.0
    private const val TICK_TEN = 5.0
    private const val LABEL_SIZE = 2.5
    private const val PRESETS_TOP = 165.0
    private const val PRESETS_LEFT = 40.0
    private const val PRESET_GAP = 12.0
    private const val INSTRUCTIONS_TOP = 235.0

    private val INSTRUCTIONS = listOf(
        "How to check:",
        "1. Print from Photo Dokan (Print > Calibrate) on the paper you print photos on.",
        "2. With a steel ruler, measure the square's width and height: each should be 100 mm (±0.5 mm).",
        "3. Measure from the left edge of the paper to the square: it should be 60 mm.",
        "4. Measure from the top edge of the paper to the square: it should be 45 mm.",
        "5. Enter the four numbers in Photo Dokan. The rulers above help check the result.",
    )

    fun page(presets: List<PhotoSizePreset> = PhotoSizePreset.DEFAULTS): PrintPage {
        val items = mutableListOf<PageItem>()
        items += PageItem.Text(LEFT_RULER_X, 12.0, 4.0, "Photo Dokan calibration sheet - print at Actual size (100%)")
        items += topRuler()
        items += leftRuler()
        items += PageItem.Box(SQUARE)
        items += PageItem.Text(SQUARE.x + 25, SQUARE.y + 52, 5.0, "100 × 100 mm")
        items += presetBoxes(presets)
        INSTRUCTIONS.forEachIndexed { i, line ->
            items += PageItem.Text(25.0, INSTRUCTIONS_TOP + i * 5.5, 3.2, line)
        }
        return PrintPage(PaperSizes.A4, items)
    }

    private fun tickLength(mm: Int) = when {
        mm % 10 == 0 -> TICK_TEN
        mm % 5 == 0 -> TICK_HALF
        else -> TICK_MM
    }

    private fun tick(line: LineMm) = PageItem.Line(line, widthMm = 0.12, gray = 0.0)

    private fun topRuler(): List<PageItem> = buildList {
        add(tick(LineMm(TOP_RULER_FROM.toDouble(), TOP_RULER_Y, TOP_RULER_TO.toDouble(), TOP_RULER_Y)))
        for (x in TOP_RULER_FROM..TOP_RULER_TO) {
            add(tick(LineMm(x.toDouble(), TOP_RULER_Y, x.toDouble(), TOP_RULER_Y + tickLength(x))))
            if (x % 10 == 0) add(PageItem.Text(x + 0.4, TOP_RULER_Y + TICK_TEN + 3.0, LABEL_SIZE, x.toString()))
        }
    }

    private fun leftRuler(): List<PageItem> = buildList {
        add(tick(LineMm(LEFT_RULER_X, LEFT_RULER_FROM.toDouble(), LEFT_RULER_X, LEFT_RULER_TO.toDouble())))
        for (y in LEFT_RULER_FROM..LEFT_RULER_TO) {
            add(tick(LineMm(LEFT_RULER_X, y.toDouble(), LEFT_RULER_X + tickLength(y), y.toDouble())))
            if (y % 10 == 0) add(PageItem.Text(LEFT_RULER_X + TICK_TEN + 0.8, y + 0.9, LABEL_SIZE, y.toString()))
        }
    }

    private fun presetBoxes(presets: List<PhotoSizePreset>): List<PageItem> = buildList {
        var x = PRESETS_LEFT
        for (preset in presets) {
            val size = preset.size
            add(PageItem.Box(RectMm(x, PRESETS_TOP, size.width, size.height)))
            val dimensions = "${size.width.mm()} × ${size.height.mm()} mm"
            add(PageItem.Text(x, PRESETS_TOP + size.height + 5.0, 2.8, preset.name))
            if (dimensions !in preset.name) add(PageItem.Text(x, PRESETS_TOP + size.height + 9.0, 2.8, dimensions))
            x += size.width + PRESET_GAP
        }
        require(x - PRESET_GAP <= TOP_RULER_TO) { "Photo sizes do not fit across the calibration sheet" }
    }

    /** 35.0 → "35", 25.4 → "25.4". */
    private fun Double.mm(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()
}

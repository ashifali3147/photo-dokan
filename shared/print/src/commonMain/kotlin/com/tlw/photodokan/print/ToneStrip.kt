package com.tlw.photodokan.print

import com.tlw.photodokan.core.geometry.PaperSizes
import com.tlw.photodokan.core.geometry.RectMm

/**
 * The A4 strip that finds a printer's [PrintTone]: the same small chart of greys, skin tones and
 * backdrop colours, printed once for every tone. Brightness grows to the right and colour grows
 * downwards. The operator holds the print next to the screen, which shows the chart untouched,
 * and enters the number of the patch that looks the same.
 */
object ToneStrip {
    /** The chart's size in pixels; flat patches of colour, so it can be stretched to any size. */
    const val CHART_PX = 240

    private const val COLUMNS = PrintTone.MAX_BRIGHTNESS + 1
    private const val ROWS = PrintTone.MAX_COLOUR + 1
    const val PATCHES = COLUMNS * ROWS

    private const val PATCH_MM = 28.0
    private const val LEFT = 15.5
    private const val TOP = 30.0
    private const val COLUMN_STEP = 31.0
    private const val ROW_STEP = 38.0
    private const val INSTRUCTIONS_TOP = 195.0

    private val GREYS = intArrayOf(35, 75, 115, 155, 195, 235)
    /** Fair, medium and dark skin. */
    private val SKIN = intArrayOf(0xF1C9A5, 0xC68E65, 0x8D5A3B)
    /** The studio's blue backdrop, then a red and a green to judge colour by. */
    private val COLOURS = intArrayOf(0x3F6FB5, 0xB8423A, 0x4E8A4A)

    private val INSTRUCTIONS = listOf(
        "How to match prints to the screen:",
        "1. Print from Photo Dokan (Print > Match colours) on the paper you print photos on.",
        "2. Hold this sheet next to the screen, where Photo Dokan shows the same patch.",
        "3. Find the patch that looks most like the screen: its skin, greys and blue.",
        "4. Enter its number in Photo Dokan. Patches to the right are brighter; lower rows have more colour.",
    )

    /** The tone patch [number] (1 to [PATCHES]) was printed with, or null for any other number. */
    fun toneOf(number: Int): PrintTone? {
        if (number !in 1..PATCHES) return null
        val i = number - 1
        return PrintTone(brightness = i % COLUMNS, colour = i / COLUMNS)
    }

    fun numberOf(tone: PrintTone): Int = tone.colour * COLUMNS + tone.brightness + 1

    /** The chart as 0xAARRGGBB pixels, [CHART_PX] square, through [tone]. */
    fun chart(tone: PrintTone = PrintTone.NONE): IntArray {
        val size = CHART_PX
        val greysEnd = size * 35 / 100
        val skinEnd = size * 75 / 100
        val pixels = IntArray(size * size) { i ->
            val x = i % size
            val y = i / size
            val rgb = when {
                y < greysEnd -> GREYS[x * GREYS.size / size].let { (it shl 16) or (it shl 8) or it }
                y < skinEnd -> SKIN[x * SKIN.size / size]
                else -> COLOURS[x * COLOURS.size / size]
            }
            OPAQUE or rgb
        }
        tone.applyTo(pixels)
        return pixels
    }

    /**
     * The strip page, each patch made into a JPEG by [jpeg] from its pixels, width and height.
     * Print it with [PrintDocument.applyTone] off: the patches already carry their tones.
     */
    fun page(jpeg: (argb: IntArray, width: Int, height: Int) -> ByteArray): PrintPage {
        val items = mutableListOf<PageItem>()
        items += PageItem.Text(LEFT, 15.0, 4.0, "Photo Dokan colour strip - which patch looks like the screen?")
        for (number in 1..PATCHES) {
            val tone = toneOf(number)!!
            val x = LEFT + tone.brightness * COLUMN_STEP
            val y = TOP + tone.colour * ROW_STEP
            items += PageItem.Image(RectMm(x, y, PATCH_MM, PATCH_MM), jpeg(chart(tone), CHART_PX, CHART_PX))
            items += PageItem.Text(x + PATCH_MM / 2 - 2.0, y + PATCH_MM + 5.0, 4.0, number.toString())
        }
        INSTRUCTIONS.forEachIndexed { i, line ->
            items += PageItem.Text(LEFT, INSTRUCTIONS_TOP + i * 5.5, 3.2, line)
        }
        return PrintPage(PaperSizes.A4, items)
    }

    private const val OPAQUE = 0xFF shl 24
}

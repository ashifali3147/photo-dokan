package com.tlw.photodokan.print

import com.tlw.photodokan.core.geometry.PaperSizes
import com.tlw.photodokan.core.geometry.RectMm
import com.tlw.photodokan.core.model.Order
import com.tlw.photodokan.core.model.SheetState
import com.tlw.photodokan.core.model.SheetTemplate
import com.tlw.photodokan.core.packing.PackingEngine
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrinterPageTest {

    /** A4 at 360 dpi (2976 × 4209 px) with a 3 mm (43 px) unprintable border, as an Epson driver reports it. */
    private val a4 = PrinterPage(
        dpiX = 360, dpiY = 360,
        paperWidthPx = 2976, paperHeightPx = 4209,
        offsetXPx = 43, offsetYPx = 43,
        printableWidthPx = 2976 - 86, printableHeightPx = 4209 - 86,
    )

    private val template = SheetTemplate.STUDIO_A4_5X8
    private val jpeg = byteArrayOf(1)

    private fun sheet(): PrintPage {
        val packed = PackingEngine.pack(template, SheetState.FRESH, listOf(Order("A", "Customer A", 40))).sheets.single()
        return sheetPage(template, packed) { jpeg }
    }

    @Test
    fun positionsAreShiftedBackByTheUnprintableBorder() {
        // 25.4 mm is exactly 360 px from the paper edge, 317 px from where the driver starts drawing.
        assertEquals(317, a4.x(25.4))
        assertEquals(317, a4.y(25.4))
        assertEquals(-43, a4.x(0.0))
    }

    @Test
    fun aSlotMeasuresItsSizeInPixels() {
        val r = a4.rect(RectMm(10.0, 20.0, 36.0, 34.0))
        // 36 mm = 510.2 px and 34 mm = 481.9 px at 360 dpi; rounding each edge keeps it within a pixel.
        assertTrue(r.width in 510..511, "width was ${r.width}")
        assertTrue(r.height in 481..482, "height was ${r.height}")
    }

    @Test
    fun slotsDoNotDriftAcrossTheSheet() {
        // The last column's right edge lands where the mm layout says, not 5 rounding errors later.
        val last = template.slotRect(4)
        assertEquals(a4.x(last.right), a4.rect(last).right)
        val expected = (last.right * 360 / 25.4).roundToInt() - 43
        assertEquals(expected, a4.rect(last).right)
    }

    @Test
    fun paperSizeComesFromTheDriver() {
        assertEquals(210.0, a4.paper.width, 0.1)
        assertEquals(297.0, a4.paper.height, 0.1)
    }

    @Test
    fun theStudioSheetFitsA4() {
        assertNull(a4.problemWith(sheet()))
    }

    @Test
    fun letterPaperIsRefused() {
        val letter = a4.copy(paperWidthPx = 3060, paperHeightPx = 3960, printableWidthPx = 2974, printableHeightPx = 3874)
        val problem = assertNotNull(letter.problemWith(sheet()))
        assertTrue(problem.startsWith("The printer is set to 216 × 279 mm paper, but this sheet needs 210 × 297 mm."), problem)
    }

    @Test
    fun landscapeIsRefused() {
        val landscape = a4.copy(paperWidthPx = 4209, paperHeightPx = 2976, printableWidthPx = 4123, printableHeightPx = 2890)
        assertNotNull(landscape.problemWith(sheet()))
    }

    @Test
    fun photosInTheUnprintableBorderAreRefused() {
        // A driver with a 15 mm bottom border would cut off the last row (it ends 5.5 mm from the edge).
        val wideBorder = a4.copy(printableHeightPx = 4209 - 43 - 213)
        assertEquals("Part of the sheet falls in the printer's unprintable border, so photos would be cut off.", wideBorder.problemWith(sheet()))
    }

    @Test
    fun androidsA4WithItsMarginsTakesTheSheet() {
        // Android's A4 is 8270 × 11690 mils; 118 mils is a 3 mm border.
        assertNull(PrinterPage.fromMils(8270, 11690, 0, 0, 0, 0).problemWith(sheet()))
        val bordered = PrinterPage.fromMils(8270, 11690, 118, 118, 118, 118)
        assertEquals(210.06, bordered.paper.width, 0.01)
        assertNull(bordered.problemWith(sheet()))
    }

    @Test
    fun androidsLetterPaperAndWideMarginsAreRefused() {
        val letter = assertNotNull(PrinterPage.fromMils(8500, 11000, 118, 118, 118, 118).problemWith(sheet()))
        assertTrue(letter.startsWith("The printer is set to 216 × 279 mm paper"), letter)
        // A quarter-inch (6.35 mm) bottom margin cuts into the last row, 5.5 mm from the edge.
        assertEquals(
            "Part of the sheet falls in the printer's unprintable border, so photos would be cut off.",
            PrinterPage.fromMils(8270, 11690, 118, 118, 118, 250).problemWith(sheet()),
        )
    }

    @Test
    fun linesNeverDisappear() {
        assertEquals(1, a4.width(0.01))
        assertEquals(3, a4.width(0.2))
    }

    @Test
    fun theCalibrationSheetFits() {
        val page = CalibrationSheet.page()
        assertEquals(PaperSizes.A4, page.paper)
        assertNull(a4.problemWith(page))
    }

    @Test
    fun theCalibrationMovesEveryPosition() {
        // 1% smaller and 2.54 mm (36 px) left: 25.4 mm lands at 25.146 − 2.54 = 22.606 mm = 320.4 px.
        val calibrated = a4.copy(calibration = PrintCalibration(scaleX = 0.99, offsetXMm = -2.54))
        assertEquals(320 - 43, calibrated.x(25.4))
        assertEquals(a4.y(25.4), calibrated.y(25.4), "Y is not corrected")
        val slot = calibrated.rect(template.slotRect(0))
        val expected = (template.photo.width * 0.99 / 25.4 * 360).roundToInt()
        assertTrue(kotlin.math.abs(slot.width - expected) <= 1, "slot is ${slot.width} px, expected about $expected")
    }

    @Test
    fun aCalibrationThatPushesPhotosIntoTheBorderIsRefused() {
        // The studio sheet starts a few mm from the edge; moving it far up-left reaches the border.
        val shifted = a4.copy(calibration = PrintCalibration(offsetXMm = -9.0, offsetYMm = -9.0))
        assertNotNull(shifted.problemWith(sheet()))
    }
}

package com.tlw.photodokan.print

import com.tlw.photodokan.print.CalibrationSheet.SQUARE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrintCalibrationTest {

    /** A printer that puts a position sent as c mm at c × k + e mm on the paper, per axis. */
    private class Printer(val kx: Double, val ex: Double, val ky: Double, val ey: Double) {
        fun x(c: Double) = c * kx + ex
        fun y(c: Double) = c * ky + ey

        /** Prints the calibration sheet with [calibration] applied and measures it exactly. */
        fun measure(calibration: PrintCalibration): CalibrationMeasurement {
            val left = x(calibration.x(SQUARE.x))
            val right = x(calibration.x(SQUARE.right))
            val top = y(calibration.y(SQUARE.y))
            val bottom = y(calibration.y(SQUARE.bottom))
            return CalibrationMeasurement(right - left, bottom - top, left, top)
        }
    }

    private val positions = listOf(0.0, 10.0, 60.0, 105.0, 200.0, 297.0)

    private fun assertLandsWhereMeant(printer: Printer, calibration: PrintCalibration) {
        for (p in positions) {
            assertEquals(p, printer.x(calibration.x(p)), 1e-9, "x $p mm")
            assertEquals(p, printer.y(calibration.y(p)), 1e-9, "y $p mm")
        }
    }

    @Test
    fun noCorrectionLeavesPositionsAlone() {
        assertTrue(PrintCalibration.NONE.isNone)
        assertEquals(123.4, PrintCalibration.NONE.x(123.4))
        assertEquals(56.7, PrintCalibration.NONE.y(56.7))
    }

    @Test
    fun anExactPrinterNeedsNoCorrection() {
        val measured = CalibrationMeasurement(100.0, 100.0, SQUARE.x, SQUARE.y)
        assertEquals(PrintCalibration.NONE, PrintCalibration.NONE.corrected(measured))
    }

    @Test
    fun oneRoundCancelsTheScaleAndShift() {
        // 2% too wide and 0.8 mm right; 1% too short and 0.5 mm up.
        val printer = Printer(kx = 1.02, ex = 0.8, ky = 0.99, ey = -0.5)
        val measured = printer.measure(PrintCalibration.NONE)
        assertEquals(102.0, measured.squareWidthMm, 1e-9)
        assertEquals(62.0, measured.leftGapMm, 1e-9)

        assertLandsWhereMeant(printer, PrintCalibration.NONE.corrected(measured))
    }

    @Test
    fun aSecondRoundBuildsOnTheFirst() {
        val first = PrintCalibration.NONE.corrected(Printer(1.02, 0.8, 0.99, -0.5).measure(PrintCalibration.NONE))
        // A new driver, or a better measurement, behaves a little differently.
        val printer = Printer(kx = 1.01, ex = 0.3, ky = 1.005, ey = 1.2)
        val second = first.corrected(printer.measure(first))
        assertLandsWhereMeant(printer, second)
    }

    @Test
    fun describesTheCorrectionForTheOperator() {
        assertEquals("No correction", PrintCalibration.NONE.describe())
        assertEquals(
            "Size +0.4% across, −0.2% down; moved 0.6 mm left, 0.3 mm down",
            PrintCalibration(scaleX = 1.004, scaleY = 0.998, offsetXMm = -0.6, offsetYMm = 0.3).describe(),
        )
        assertEquals("Size 0.0% across, 0.0% down; moved 1.0 mm right, 0.0 mm down", PrintCalibration(offsetXMm = 1.0).describe())
    }

    @Test
    fun refusesCorrectionsNoRealPrinterNeeds() {
        assertFailsWith<IllegalArgumentException> { PrintCalibration(scaleX = 1.2) }
        assertFailsWith<IllegalArgumentException> { PrintCalibration(offsetYMm = -15.0) }
    }

    @Test
    fun plausibleMeasurementsHaveNoProblem() {
        assertNull(CalibrationMeasurement(100.5, 99.5, 60.5, 44.0).problem())
        assertNull(CalibrationMeasurement(103.0, 97.0, 54.0, 51.0).problem())
    }

    @Test
    fun typosAndWrongEdgesAreCaught() {
        assertEquals(
            "The square should be close to 100 mm wide. Check the width measurement.",
            CalibrationMeasurement(1000.0, 100.0, 60.0, 45.0).problem(),
        )
        assertNotNull(CalibrationMeasurement(100.0, 10.0, 60.0, 45.0).problem())
        // Measured from the right edge of the paper: 210 − 160 = 50, still plausible; 150 is not.
        assertEquals(
            "The gap on the left should be close to 60 mm. Measure from the left edge of the paper to the square.",
            CalibrationMeasurement(100.0, 100.0, 150.0, 45.0).problem(),
        )
        assertNotNull(CalibrationMeasurement(100.0, 100.0, 60.0, 145.0).problem())
    }

    @Test
    fun readsNumbersAsTheOperatorTypesThem() {
        assertEquals(100.0, CalibrationMeasurement.parseMm("100"))
        assertEquals(100.5, CalibrationMeasurement.parseMm(" 100.5 "))
        assertEquals(100.5, CalibrationMeasurement.parseMm("100,5"))
        assertNull(CalibrationMeasurement.parseMm(""))
        assertNull(CalibrationMeasurement.parseMm("abc"))
        assertNull(CalibrationMeasurement.parseMm("-3"))
    }
}

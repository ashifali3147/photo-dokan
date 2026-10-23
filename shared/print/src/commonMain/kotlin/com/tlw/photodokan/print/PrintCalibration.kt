package com.tlw.photodokan.print

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Corrects a printer that prints slightly too large, too small or shifted. A position on the
 * paper, in mm, is sent to the printer as `position × scale + offset`, separately for X and Y,
 * so that it lands where it was meant to.
 */
data class PrintCalibration(
    val scaleX: Double = 1.0,
    val scaleY: Double = 1.0,
    val offsetXMm: Double = 0.0,
    val offsetYMm: Double = 0.0,
) {
    init {
        require(scaleX in MIN_SCALE..MAX_SCALE && scaleY in MIN_SCALE..MAX_SCALE) { "Scale out of range: $scaleX × $scaleY" }
        require(abs(offsetXMm) <= MAX_OFFSET_MM && abs(offsetYMm) <= MAX_OFFSET_MM) { "Offset out of range: $offsetXMm, $offsetYMm mm" }
    }

    val isNone: Boolean get() = this == NONE

    fun x(mm: Double): Double = mm * scaleX + offsetXMm

    fun y(mm: Double): Double = mm * scaleY + offsetYMm

    /**
     * The correction after measuring a calibration sheet printed with this one applied. The
     * measurement shows what is still wrong, so a second round refines the first.
     */
    fun corrected(measured: CalibrationMeasurement): PrintCalibration {
        // The printed sheet maps a position p to p × k + e on paper, per axis.
        val kx = measured.squareWidthMm / CalibrationSheet.SQUARE.width
        val ky = measured.squareHeightMm / CalibrationSheet.SQUARE.height
        val ex = measured.leftGapMm - CalibrationSheet.SQUARE.x * kx
        val ey = measured.topGapMm - CalibrationSheet.SQUARE.y * ky
        // Undo that first, then apply this correction: p → ((p − e) / k) × scale + offset.
        return PrintCalibration(
            scaleX = scaleX / kx,
            scaleY = scaleY / ky,
            offsetXMm = offsetXMm - ex * scaleX / kx,
            offsetYMm = offsetYMm - ey * scaleY / ky,
        )
    }

    /** For the operator, e.g. "Size +0.4% across, −0.2% down; moved 0.6 mm left, 0.3 mm down". */
    fun describe(): String {
        if (isNone) return "No correction"
        fun percent(scale: Double): String {
            val tenths = ((scale - 1) * 1000).roundToInt()
            val sign = if (tenths > 0) "+" else if (tenths < 0) "−" else ""
            return "$sign${abs(tenths) / 10}.${abs(tenths) % 10}%"
        }
        fun mm(offset: Double): String {
            val tenths = abs((offset * 10).roundToInt())
            return "${tenths / 10}.${tenths % 10} mm"
        }
        val across = if (offsetXMm >= 0) "right" else "left"
        val down = if (offsetYMm >= 0) "down" else "up"
        return "Size ${percent(scaleX)} across, ${percent(scaleY)} down; moved ${mm(offsetXMm)} $across, ${mm(offsetYMm)} $down"
    }

    companion object {
        /** A driver printing at actual size is never more than a few percent off. */
        const val MIN_SCALE = 0.95
        const val MAX_SCALE = 1.05
        const val MAX_OFFSET_MM = 10.0

        val NONE = PrintCalibration()
    }
}

/**
 * What the operator measured on the printed [CalibrationSheet], in mm: the square's sides, and
 * the gaps between the paper's left and top edges and the square.
 */
data class CalibrationMeasurement(
    val squareWidthMm: Double,
    val squareHeightMm: Double,
    val leftGapMm: Double,
    val topGapMm: Double,
) {
    /**
     * Why these numbers can't be right, in words for the operator, or null when they look
     * plausible. A typo (1000 for 100.0) or a ruler read from the wrong end must not end up as
     * a correction on every print.
     */
    fun problem(): String? {
        val square = CalibrationSheet.SQUARE
        fun near(value: Double, expected: Double, tolerance: Double) = abs(value - expected) <= tolerance
        return when {
            !near(squareWidthMm, square.width, SIZE_TOLERANCE_MM) ->
                "The square should be close to ${square.width.toInt()} mm wide. Check the width measurement."
            !near(squareHeightMm, square.height, SIZE_TOLERANCE_MM) ->
                "The square should be close to ${square.height.toInt()} mm tall. Check the height measurement."
            !near(leftGapMm, square.x, GAP_TOLERANCE_MM) ->
                "The gap on the left should be close to ${square.x.toInt()} mm. Measure from the left edge of the paper to the square."
            !near(topGapMm, square.y, GAP_TOLERANCE_MM) ->
                "The gap at the top should be close to ${square.y.toInt()} mm. Measure from the top edge of the paper to the square."
            else -> null
        }
    }

    companion object {
        const val SIZE_TOLERANCE_MM = 4.0
        const val GAP_TOLERANCE_MM = 8.0

        /** Accepts "100", "100.5" and "100,5"; null for anything else. */
        fun parseMm(text: String): Double? =
            text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    }
}

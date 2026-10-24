package com.tlw.photodokan.ui.editor

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import com.tlw.photodokan.core.imaging.ColorAdjustment
import com.tlw.photodokan.core.imaging.CropGeometry
import com.tlw.photodokan.core.imaging.CropRect
import com.tlw.photodokan.core.model.EditRecipe
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Checks that [renderCrop] turns a recipe into the same pixels on every platform, by comparing it
 * with the pure maths in CropGeometry and ColorAdjustment. The same file runs as a desktop unit
 * test and as an Android instrumented test, so passing on both means the phone's final.jpg and
 * print match the PC's for the same recipe.
 */
class RenderParityTest {

    private val width = 160
    private val height = 120

    /** A synthetic photo whose channels change smoothly, so any good sampling filter lands on the exact value. */
    private fun photoAt(x: Double, y: Double) = doubleArrayOf(
        60 + 130 * x / width,
        60 + 130 * y / height,
        190 - 130 * x / width,
    )

    private val photo: ImageBitmap = ImageBitmap(width, height).also { image ->
        val canvas = Canvas(image)
        val paint = Paint().apply { isAntiAlias = false }
        for (y in 0 until height) for (x in 0 until width) {
            val (r, g, b) = photoAt(x + 0.5, y + 0.5).map { it.roundToInt() }
            paint.color = Color(r, g, b)
            canvas.drawRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, paint)
        }
    }

    private val neutral = EditRecipe(presetId = "studio-slot")

    /**
     * Renders [rect] at 90 × 90 with [recipe]'s adjustments and checks every pixel away from the
     * edges against the reference: where it comes from in the photo, then the colour matrix.
     */
    private fun assertMatchesReference(rect: CropRect, recipe: EditRecipe = neutral) {
        val size = 90
        val matrix = ColorAdjustment.matrix(recipe)
        val pixels = IntArray(size * size).also { renderCrop(photo, rect, size, size, matrix).readPixels(it) }

        var worst = 0
        var where = ""
        for (y in 2 until size - 2) for (x in 2 until size - 2) {
            val (sx, sy) = CropGeometry.sourcePoint(rect, size, size, x, y)
            val expected = ColorAdjustment.applyTo(matrix, photoAt(sx, sy))
            val argb = pixels[y * size + x]
            val actual = intArrayOf(argb shr 16 and 0xFF, argb shr 8 and 0xFF, argb and 0xFF)
            for (c in 0 until 3) {
                val diff = abs(actual[c] - expected[c]).roundToInt()
                if (diff > worst) {
                    worst = diff
                    where = "pixel ($x, $y) channel $c: expected ${expected[c].roundToInt()}, was ${actual[c]}"
                }
            }
        }
        assertTrue(worst <= TOLERANCE, "rendered pixels differ from the reference by up to $worst; worst at $where")
    }

    @Test
    fun plainCrop() = assertMatchesReference(CropRect(80.0, 60.0, 60.0, 60.0, 0.0))

    @Test
    fun straightenedCrop() = assertMatchesReference(CropRect(80.0, 60.0, 60.0, 60.0, 30.0))

    @Test
    fun offCentreCropTurnedTheOtherWay() = assertMatchesReference(CropRect(70.0, 55.0, 60.0, 60.0, -12.0))

    @Test
    fun brighter() = assertMatchesReference(CropRect(80.0, 60.0, 60.0, 60.0, 0.0), neutral.copy(exposure = 1.0))

    @Test
    fun darker() = assertMatchesReference(CropRect(80.0, 60.0, 60.0, 60.0, 0.0), neutral.copy(exposure = -1.0))

    /** Contrast is the one adjustment with an offset column, the part platforms disagree on most easily. */
    @Test
    fun moreContrast() = assertMatchesReference(CropRect(80.0, 60.0, 60.0, 60.0, 0.0), neutral.copy(contrast = 1.0))

    @Test
    fun lessContrast() = assertMatchesReference(CropRect(80.0, 60.0, 60.0, 60.0, 0.0), neutral.copy(contrast = -1.0))

    @Test
    fun warmerAndGrey() = assertMatchesReference(CropRect(80.0, 60.0, 60.0, 60.0, 0.0), neutral.copy(warmth = 1.0, saturation = -1.0))

    @Test
    fun everythingAtOnce() = assertMatchesReference(
        CropRect(75.0, 58.0, 60.0, 60.0, 8.0),
        neutral.copy(exposure = 0.4, contrast = 0.6, warmth = -0.5, saturation = 0.7),
    )

    private companion object {
        /** Rounding, 8-bit colour and filter differences; a wrong matrix or turn is off by tens. */
        const val TOLERANCE = 4
    }

    @Test
    fun pixelsWrittenAreThePixelsReadBack() {
        val argb = IntArray(7 * 5) { i -> (0xff shl 24) or (i * 7 shl 16) or (255 - i shl 8) or (i * 3) }
        val back = IntArray(argb.size).also { com.tlw.photodokan.ui.platform.imageBitmapOf(argb, 7, 5).readPixels(it) }
        assertTrue(argb.contentEquals(back), "the same pixels on this platform")
    }
}

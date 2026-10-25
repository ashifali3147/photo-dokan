package com.tlw.photodokan.ui.editor

import androidx.compose.ui.graphics.ImageBitmap
import com.tlw.photodokan.core.matte.Matte
import com.tlw.photodokan.core.model.BackgroundConfig
import com.tlw.photodokan.core.model.BackgroundMode
import com.tlw.photodokan.core.model.BrushMode
import com.tlw.photodokan.core.model.MaskPoint
import com.tlw.photodokan.core.model.MaskStroke
import com.tlw.photodokan.ui.platform.imageBitmapOf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame

class BackgroundPainterTest {

    private val size = 64

    /** A square person of [colour] in the middle of a blue backdrop. */
    private fun photo(colour: Int) = imageBitmapOf(IntArray(size * size) { i ->
        val x = i % size
        val y = i / size
        if (x in 16 until 48 && y in 16 until 48) colour else 0xff2850c8.toInt()
    }, size, size)

    private val matte = Matte(16, 16, ByteArray(16 * 16) { i -> if (i % 16 in 4 until 12 && i / 16 in 4 until 12) -1 else 0 })

    private fun ImageBitmap.pixels() = IntArray(width * height).also { readPixels(it) }

    @Test
    fun changingTheSettingsPaintsAsAFreshStartWould() {
        val painter = BackgroundPainter()
        val image = photo(0xffc83c32.toInt())
        for (config in listOf(
            BackgroundConfig(mode = BackgroundMode.BLUR, blurStrength = 1.0),
            BackgroundConfig(mode = BackgroundMode.REPLACE),
            BackgroundConfig(mode = BackgroundMode.REPLACE, replaceColorArgb = BackgroundConfig.LIGHT_BLUE),
        )) {
            assertContentEquals(image.withBackground(matte, config).pixels(), painter.paint(image, matte, config).pixels(), "$config")
        }
    }

    @Test
    fun anotherPhotoIsCutOutAfresh() {
        val painter = BackgroundPainter()
        val white = BackgroundConfig(mode = BackgroundMode.REPLACE)
        painter.paint(photo(0xffc83c32.toInt()), matte, white)
        val green = photo(0xff28a03c.toInt())
        assertContentEquals(green.withBackground(matte, white).pixels(), painter.paint(green, matte, white).pixels())
    }

    @Test
    fun aNewFixCutsThePersonOutAgain() {
        val painter = BackgroundPainter()
        val image = photo(0xffc83c32.toInt())
        val white = BackgroundConfig(mode = BackgroundMode.REPLACE)
        painter.paint(image, matte, white)
        val erased = white.copy(touchUps = listOf(MaskStroke(BrushMode.ERASE, 0.2, listOf(MaskPoint(0.5, 0.5)))))
        val painted = painter.paint(image, matte, erased).pixels()
        assertContentEquals(image.withBackground(matte, erased).pixels(), painted)
        assertEquals(0, painted[32 * size + 32] ushr 24, "the middle of the person, erased: clear for the white to show")
    }

    @Test
    fun keepingTheBackgroundIsThePhotoItself() {
        val image = photo(0xffc83c32.toInt())
        assertSame(image, BackgroundPainter().paint(image, matte, BackgroundConfig(mode = BackgroundMode.KEEP)))
        assertSame(image, BackgroundPainter().paint(image, null, BackgroundConfig(mode = BackgroundMode.REPLACE)))
    }
}

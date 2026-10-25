package com.tlw.photodokan.ui.editor

import androidx.compose.ui.graphics.ImageBitmap
import com.tlw.photodokan.core.matte.Matte
import com.tlw.photodokan.core.model.BackgroundConfig
import com.tlw.photodokan.core.model.BackgroundMode
import com.tlw.photodokan.core.model.BrushMode
import com.tlw.photodokan.core.model.MaskPoint
import com.tlw.photodokan.core.model.MaskStroke
import com.tlw.photodokan.core.model.Portrait
import com.tlw.photodokan.ui.platform.imageBitmapOf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PhotoPainterTest {

    private val size = 64

    /** A square person of [colour] in the middle of a blue backdrop. */
    private fun photo(colour: Int) = imageBitmapOf(IntArray(size * size) { i ->
        val x = i % size
        val y = i / size
        if (x in 16 until 48 && y in 16 until 48) colour else 0xff2850c8.toInt()
    }, size, size)

    private val matte = Matte(16, 16, ByteArray(16 * 16) { i -> if (i % 16 in 4 until 12 && i / 16 in 4 until 12) -1 else 0 })

    private fun ImageBitmap.pixels() = IntArray(width * height).also { readPixels(it) }

    /** Just the background, no studio look. */
    private fun only(config: BackgroundConfig) = Painting(config, enhance = 0.0, portrait = null)

    @Test
    fun changingTheSettingsPaintsAsAFreshStartWould() {
        val painter = PhotoPainter()
        val image = photo(0xffc83c32.toInt())
        for (config in listOf(
            BackgroundConfig(mode = BackgroundMode.BLUR, blurStrength = 1.0),
            BackgroundConfig(mode = BackgroundMode.REPLACE),
            BackgroundConfig(mode = BackgroundMode.REPLACE, replaceColorArgb = BackgroundConfig.LIGHT_BLUE),
        )) {
            assertContentEquals(image.painted(matte, only(config)).pixels(), painter.paint(image, matte, only(config)).pixels(), "$config")
        }
    }

    @Test
    fun anotherPhotoIsCutOutAfresh() {
        val painter = PhotoPainter()
        val white = BackgroundConfig(mode = BackgroundMode.REPLACE)
        painter.paint(photo(0xffc83c32.toInt()), matte, only(white))
        val green = photo(0xff28a03c.toInt())
        assertContentEquals(green.painted(matte, only(white)).pixels(), painter.paint(green, matte, only(white)).pixels())
    }

    @Test
    fun aNewFixCutsThePersonOutAgain() {
        val painter = PhotoPainter()
        val image = photo(0xffc83c32.toInt())
        val white = BackgroundConfig(mode = BackgroundMode.REPLACE)
        painter.paint(image, matte, only(white))
        val erased = white.copy(touchUps = listOf(MaskStroke(BrushMode.ERASE, 0.2, listOf(MaskPoint(0.5, 0.5)))))
        val painted = painter.paint(image, matte, only(erased)).pixels()
        assertContentEquals(image.painted(matte, only(erased)).pixels(), painted)
        assertEquals(0, painted[32 * size + 32] ushr 24, "the middle of the person, erased: clear for the white to show")
    }

    @Test
    fun keepingTheBackgroundIsThePhotoItself() {
        val image = photo(0xffc83c32.toInt())
        assertSame(image, PhotoPainter().paint(image, matte, only(BackgroundConfig(mode = BackgroundMode.KEEP))))
        assertSame(image, PhotoPainter().paint(image, null, only(BackgroundConfig(mode = BackgroundMode.REPLACE))))
    }

    @Test
    fun theStudioLookPaintsTheCutOutPerson() {
        val image = photo(0xff96624a.toInt()) // brown skin, dimly lit
        val look = Painting(BackgroundConfig(mode = BackgroundMode.REPLACE), enhance = 1.0, portrait = Portrait(skinLevel = 0.4, faceHeight = 0.3))
        val painted = PhotoPainter().paint(image, matte, look).pixels()
        val plain = PhotoPainter().paint(image, matte, look.copy(enhance = 0.0)).pixels()
        val middle = 32 * size + 32
        assertTrue((painted[middle] and 0xff00) > (plain[middle] and 0xff00), "brighter skin")
        for (i in painted.indices) assertEquals(plain[i] ushr 24, painted[i] ushr 24, "still cut out at $i")
    }

    @Test
    fun fairSkinPaintsOnItsOwn() {
        val image = photo(0xff96624a.toInt())
        val fair = Painting(BackgroundConfig(mode = BackgroundMode.KEEP), enhance = 0.0, portrait = Portrait(skinLevel = 0.4, faceHeight = 0.3), fairness = 1.0)
        val painted = PhotoPainter().paint(image, null, fair).pixels()
        assertTrue((painted[32 * size + 32] and 0xff00) > (image.pixels()[32 * size + 32] and 0xff00), "fairer skin")
        assertSame(image, PhotoPainter().paint(image, null, fair.copy(portrait = null)), "no face, nothing to make fair")
    }

    @Test
    fun theLookWorksWithTheBackgroundAsShot() {
        val image = photo(0xff96624a.toInt())
        val look = Painting(BackgroundConfig(mode = BackgroundMode.KEEP), enhance = 1.0, portrait = Portrait(skinLevel = 0.4, faceHeight = 0.3))
        val painted = PhotoPainter().paint(image, null, look)
        assertTrue(painted.pixels()[32 * size + 32] != image.pixels()[32 * size + 32])
        assertContentEquals(image.painted(matte, look).pixels(), painted.pixels(), "a matte isn't used when the background stays")
    }
}

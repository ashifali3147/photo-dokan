package com.tlw.photodokan.ui.editor

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.tlw.photodokan.core.imaging.CropRect
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CropRenderTest {

    /** 40 × 20, red on the left half and blue on the right. */
    private val photo = BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB).apply {
        createGraphics().apply { color = Color.RED; fillRect(0, 0, 20, 20); color = Color.BLUE; fillRect(20, 0, 20, 20); dispose() }
    }.toComposeImageBitmap()

    private fun render(rect: CropRect) = renderCrop(photo, rect, 100, 100).toAwtImage()
    private fun BufferedImage.isRed(x: Int, y: Int) = Color(getRGB(x, y)).let { it.red > 200 && it.blue < 60 }
    private fun BufferedImage.isBlue(x: Int, y: Int) = Color(getRGB(x, y)).let { it.blue > 200 && it.red < 60 }

    @Test
    fun outputHasTheRequestedSize() {
        val out = renderCrop(photo, CropRect(20.0, 10.0, 20.0, 10.0, 0.0), 300, 150)
        assertEquals(300, out.width)
        assertEquals(150, out.height)
    }

    @Test
    fun centredSquareShowsBothHalvesSideBySide() {
        val out = render(CropRect(20.0, 10.0, 20.0, 20.0, 0.0))
        assertTrue(out.isRed(25, 50) && out.isBlue(75, 50))
    }

    @Test
    fun cropOfTheRightPartIsAllBlue() {
        val out = render(CropRect(30.0, 10.0, 10.0, 10.0, 0.0))
        assertTrue(out.isBlue(10, 10) && out.isBlue(90, 90) && out.isBlue(50, 50))
    }

    @Test
    fun positiveRotationTurnsThePhotoClockwise() {
        // Turned a quarter clockwise, the left (red) half ends up on top.
        val out = render(CropRect(20.0, 10.0, 20.0, 20.0, 90.0))
        assertTrue(out.isRed(50, 25) && out.isBlue(50, 75))
    }
}

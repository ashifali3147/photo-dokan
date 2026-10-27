package com.tlw.photodokan.ui.photo

import androidx.compose.ui.graphics.toAwtImage
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoPreviewTest {

    /** A 40 × 20 JPEG, red on the left half and blue on the right, tagged with [orientation]. */
    private fun sidewaysPhoto(orientation: Int): ByteArray {
        val image = BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            color = Color.RED; fillRect(0, 0, 20, 20)
            color = Color.BLUE; fillRect(20, 0, 20, 20)
            dispose()
        }
        val jpeg = ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
        return jpeg.copyOfRange(0, 2) + exifSegment(orientation) + jpeg.copyOfRange(2, jpeg.size)
    }

    /** APP1 segment with a big-endian TIFF whose IFD0 holds only the orientation tag. */
    private fun exifSegment(orientation: Int): ByteArray {
        val tiff = listOf('M'.code, 'M'.code, 0, 42, 0, 0, 0, 8, 0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, orientation, 0, 0, 0, 0, 0, 0)
        val payload = "Exif".map { it.code } + listOf(0, 0) + tiff
        val length = payload.size + 2
        return (listOf(0xFF, 0xE1, length shr 8, length and 0xFF) + payload).map { it.toByte() }.toByteArray()
    }

    private fun BufferedImage.isRed(x: Int, y: Int) = Color(getRGB(x, y)).let { it.red > 200 && it.blue < 80 }
    private fun BufferedImage.isBlue(x: Int, y: Int) = Color(getRGB(x, y)).let { it.blue > 200 && it.red < 80 }

    @Test
    fun portraitTaggedPhotoComesOutUpright() {
        // Orientation 6: rotate 90° clockwise, so the left (red) half ends up on top.
        val preview = decodePreview(sidewaysPhoto(6))!!
        val image = preview.image.toAwtImage()
        assertEquals(20, image.width)
        assertEquals(40, image.height)
        assertEquals(20, preview.originalWidth)
        assertEquals(40, preview.originalHeight)
        assertTrue(image.isRed(10, 8) && image.isBlue(10, 32))
    }

    @Test
    fun everyOrientationPutsTheRedHalfWhereItBelongs() {
        // Where the red (left) half of the stored image lands once the photo is upright.
        val expectRedAt = mapOf(1 to "left", 2 to "right", 3 to "right", 4 to "left", 5 to "top", 6 to "top", 7 to "bottom", 8 to "bottom")
        for ((orientation, side) in expectRedAt) {
            val image = decodePreview(sidewaysPhoto(orientation))!!.image.toAwtImage()
            val (red, blue) = when (side) {
                "left" -> (8 to 10) to (32 to 10)
                "right" -> (32 to 10) to (8 to 10)
                "top" -> (10 to 8) to (10 to 32)
                else -> (10 to 32) to (10 to 8)
            }
            assertTrue(image.isRed(red.first, red.second) && image.isBlue(blue.first, blue.second), "orientation $orientation")
        }
    }

    @Test
    fun largePhotoIsReducedButReportsItsFullSize() {
        val big = BufferedImage(4000, 1000, BufferedImage.TYPE_INT_RGB)
        val jpeg = ByteArrayOutputStream().also { ImageIO.write(big, "jpg", it) }.toByteArray()
        val preview = decodePreview(jpeg, maxSide = 1000)!!
        assertTrue(preview.image.width <= 1000, "preview width ${preview.image.width}")
        assertEquals(4000, preview.originalWidth)
        assertEquals(1000, preview.originalHeight)
    }

    @Test
    fun scaledDownPhotoFitsTheSideExactly() {
        // Whole steps alone leave 1167 × 234 for a 1000 side; scaling takes it the rest of the way.
        val big = BufferedImage(3500, 700, BufferedImage.TYPE_INT_RGB)
        val jpeg = ByteArrayOutputStream().also { ImageIO.write(big, "jpg", it) }.toByteArray()
        val image = decodePreview(jpeg, maxSide = 1000, scaleDown = true)!!.image
        assertEquals(1000, image.width)
        assertEquals(200, image.height)
    }

    @Test
    fun scalingDownLeavesASmallPhotoAlone() {
        val image = decodePreview(sidewaysPhoto(6), maxSide = 1000, scaleDown = true)!!.image
        assertEquals(20, image.width)
        assertEquals(40, image.height)
    }

    @Test
    fun notAPhotoGivesNull() {
        assertNull(decodePreview("not a photo".encodeToByteArray()))
        assertNull(decodePreview(ByteArray(0)))
    }
}

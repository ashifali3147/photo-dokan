package com.tlw.photodokan.vision

import com.tlw.photodokan.core.matte.Matte
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MattePngTest {

    /** A person-shaped blob: background around, the person in the middle, soft edges between. */
    private fun blob(width: Int = 512, height: Int = 640) = Matte(width, height, ByteArray(width * height) { i ->
        val dx = (i % width - width / 2.0) / (width * 0.3)
        val dy = (i / width - height * 0.6) / (height * 0.4)
        ((1.2 - (dx * dx + dy * dy)).coerceIn(0.0, 0.2) / 0.2 * 255).toInt().toByte()
    })

    @Test
    fun aMatteComesBackExactly() {
        val matte = blob()
        assertEquals(matte, MattePng.decode(MattePng.encode(matte)))
        val noise = Matte(37, 11, Random(1).nextBytes(37 * 11))
        assertEquals(noise, MattePng.decode(MattePng.encode(noise)))
    }

    @Test
    fun itIsARealPngThatOtherProgramsOpen() {
        val matte = blob()
        val image = ImageIO.read(MattePng.encode(matte).inputStream())
        assertEquals(512, image.width)
        assertEquals(640, image.height)
        assertEquals(matte[256, 400], image.raster.getSample(256, 400, 0))
        assertEquals(matte[3, 3], image.raster.getSample(3, 3, 0))
    }

    @Test
    fun itIsSmall() {
        val size = MattePng.encode(blob()).size
        assertTrue(size < 20_000, "$size bytes for a 512 × 640 matte")
    }

    @Test
    fun aDamagedFileIsNoMatte() {
        val png = MattePng.encode(blob())
        assertNull(MattePng.decode(png.copyOf(png.size / 2)))
        assertNull(MattePng.decode("not a png".encodeToByteArray()))
        assertNull(MattePng.decode(ByteArray(0)))
    }
}

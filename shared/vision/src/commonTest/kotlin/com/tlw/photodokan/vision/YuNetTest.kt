package com.tlw.photodokan.vision

import com.tlw.photodokan.core.face.PointPx
import kotlin.math.abs
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YuNetTest {

    private val plane = YuNet.SIZE * YuNet.SIZE

    private fun close(expected: Double, actual: Double, tolerance: Double = 1e-3) =
        assertTrue(abs(expected - actual) < tolerance, "expected $expected, was $actual")

    @Test
    fun aPhotoIsShrunkIntoTheTopLeftAsBlueGreenRed() {
        val colour = (0xff shl 24) or (10 shl 16) or (20 shl 8) or 30
        val input = YuNet.input(IntArray(1280 * 640) { colour }, 1280, 640)
        assertEquals(0.5, input.scale)
        assertEquals(30f, input.pixels[0], "blue first")
        assertEquals(20f, input.pixels[plane], "then green")
        assertEquals(10f, input.pixels[2 * plane], "then red")
        assertEquals(30f, input.pixels[319 * YuNet.SIZE + 639], "the photo fills the width")
        assertEquals(0f, input.pixels[320 * YuNet.SIZE], "black below it")
    }

    @Test
    fun eachModelPixelAveragesThePhotoPixelsItCovers() {
        // Black and white stripes one pixel wide, shrunk to half: grey.
        val stripes = IntArray(1280 * 1280) { i -> if (i % 2 == 0) 0xff000000.toInt() else 0xffffffff.toInt() }
        val input = YuNet.input(stripes, 1280, 1280)
        assertEquals(127.5f, input.pixels[123 * YuNet.SIZE + 45])
    }

    @Test
    fun aSmallPhotoIsNotEnlarged() {
        val input = YuNet.input(IntArray(320 * 240) { 0xffffffff.toInt() }, 320, 240)
        assertEquals(1.0, input.scale)
        assertEquals(255f, input.pixels[239 * YuNet.SIZE + 319])
        assertEquals(0f, input.pixels[240 * YuNet.SIZE])
    }

    /** All-empty model outputs, with [cells] (stride to cell index to its values) filled in. */
    private fun outputs(vararg cells: Cell): Map<String, FloatArray> {
        val map = mutableMapOf<String, FloatArray>()
        for (stride in listOf(8, 16, 32)) {
            val n = (YuNet.SIZE / stride) * (YuNet.SIZE / stride)
            map["cls_$stride"] = FloatArray(n)
            map["obj_$stride"] = FloatArray(n)
            map["bbox_$stride"] = FloatArray(n * 4)
            map["kps_$stride"] = FloatArray(n * 10)
        }
        for (c in cells) {
            val i = c.row * (YuNet.SIZE / c.stride) + c.col
            map.getValue("cls_${c.stride}")[i] = c.score
            map.getValue("obj_${c.stride}")[i] = c.score
            c.box.copyInto(map.getValue("bbox_${c.stride}"), i * 4)
            c.landmarks.copyInto(map.getValue("kps_${c.stride}"), i * 10)
        }
        return map
    }

    private class Cell(
        val stride: Int, val row: Int, val col: Int, val score: Float,
        val box: FloatArray = floatArrayOf(0.5f, 0.5f, ln(4.0).toFloat(), ln(5.0).toFloat()),
        val landmarks: FloatArray = floatArrayOf(-1f, -1f, 2f, -1f, 0.5f, 0.5f, -0.5f, 1.5f, 1.5f, 1.5f),
    )

    @Test
    fun aFaceIsReadBackInThePhotosPixels() {
        // Stride 16, row 10, column 20: the cell's corner is at (320, 160) in the model's square.
        val face = YuNet.faces(outputs(Cell(16, 10, 20, 0.9f)), scale = 0.5).single()
        close(0.9, face.score)
        // Centre (20.5, 10.5) cells, 4 × 5 cells big; doubled back to the photo's pixels.
        close((20.5 * 16 - 32) * 2, face.x)
        close((10.5 * 16 - 40) * 2, face.y)
        close(128.0, face.width)
        close(160.0, face.height)
        // The person's right eye is the photo's left.
        assertEquals(PointPx(19.0 * 32, 9.0 * 32), face.eyeLeft)
        assertEquals(PointPx(22.0 * 32, 9.0 * 32), face.eyeRight)
        assertEquals(PointPx(20.5 * 32, 10.5 * 32), face.nose)
        assertEquals(PointPx(19.5 * 32, 11.5 * 32), face.mouthLeft)
        assertEquals(PointPx(21.5 * 32, 11.5 * 32), face.mouthRight)
    }

    @Test
    fun oneFaceSeenByNeighbouringCellsIsOneFace() {
        // The same 64 × 80 box from two neighbouring cells, and from the coarser grid.
        val coarse = floatArrayOf(0.5f, 0.5f, ln(2.0).toFloat(), ln(2.5).toFloat())
        val faces = YuNet.faces(outputs(Cell(16, 10, 20, 0.8f), Cell(16, 10, 21, 0.95f), Cell(32, 5, 10, 0.7f, box = coarse)), scale = 1.0)
        assertEquals(1, faces.size)
        close(0.95, faces.single().score)
    }

    @Test
    fun twoPeopleAreTwoFacesMostCertainFirst() {
        val faces = YuNet.faces(outputs(Cell(16, 10, 5, 0.7f), Cell(16, 10, 30, 0.9f)), scale = 1.0)
        assertEquals(2, faces.size)
        close(0.9, faces[0].score)
        close(0.7, faces[1].score)
    }

    @Test
    fun anUnsureFaceIsDropped() {
        assertTrue(YuNet.faces(outputs(Cell(8, 3, 3, 0.5f)), scale = 1.0).isEmpty())
    }
}

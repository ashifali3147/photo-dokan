package com.tlw.photodokan.vision

import ai.onnxruntime.OnnxTensor
import java.nio.FloatBuffer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * What the models shipped with each app must do: load from the app itself, offline, and run on
 * this machine. The desktop tests run it on the PC, the instrumented tests on a phone.
 */
abstract class ModelsContract {
    abstract val files: ModelFiles

    private val models by lazy { VisionModels(files) }

    @AfterTest
    fun close() = models.close()

    /** Runs [model] on a plain grey image of [width] × [height]: its outputs' shapes and values, by name. */
    private fun runGrey(model: VisionModel, width: Int, height: Int): Map<String, Pair<LongArray, FloatArray>> {
        val session = models.session(model)
        val pixels = FloatBuffer.wrap(FloatArray(3 * width * height) { 0.5f })
        val started = System.nanoTime()
        return OnnxTensor.createTensor(models.ort, pixels, longArrayOf(1, 3, height.toLong(), width.toLong())).use { input ->
            session.run(mapOf(session.inputNames.single() to input)).use { result ->
                println("$model ran in ${(System.nanoTime() - started) / 1_000_000} ms")
                result.associate { (name, value) ->
                    val tensor = value as OnnxTensor
                    val buffer = tensor.floatBuffer
                    name to (tensor.info.shape to FloatArray(buffer.remaining()).also(buffer::get))
                }
            }
        }
    }

    @Test
    fun theFaceDetectorFindsNoFaceInAPlainImage() {
        val outputs = runGrey(VisionModel.FaceDetector, 640, 640)
        assertEquals(12, outputs.size, "scores, boxes and landmarks at three scales")
        assertContentEquals(longArrayOf(1, 6400, 4), outputs.getValue("bbox_8").first)
        assertContentEquals(longArrayOf(1, 400, 10), outputs.getValue("kps_32").first)
        val best = listOf("8", "16", "32").maxOf { stride ->
            val cls = outputs.getValue("cls_$stride").second
            val obj = outputs.getValue("obj_$stride").second
            cls.indices.maxOf { cls[it] * obj[it] }
        }
        assertTrue(best < 0.5f, "no face, best score $best")
    }

    @Test
    fun noFaceIsFoundOnAPlainPhoto() {
        assertTrue(OnnxFaceFinder(models).find(IntArray(1200 * 1600) { 0xff808080.toInt() }, 1200, 1600).isEmpty())
    }

    @Test
    fun theMattingModelGivesAMaskTheSizeOfThePhoto() {
        // MODNet takes any size that is a multiple of 32.
        val (shape, mask) = runGrey(VisionModel.PortraitMatting, 384, 512).values.single()
        assertContentEquals(longArrayOf(1, 1, 512, 384), shape)
        assertTrue(mask.all { it in 0f..1f }, "how much of each pixel is the person, 0 to 1")
    }

    @Test
    fun aPhotoGetsAMatteAtTheModelsSize() {
        val started = System.nanoTime()
        val matte = OnnxPortraitMatter(models).matte(IntArray(1500 * 2000) { 0xff808080.toInt() }, 1500, 2000)
        println("Matte of 1500 × 2000 in ${(System.nanoTime() - started) / 1_000_000} ms")
        assertEquals(512, matte.width)
        assertEquals(672, matte.height)
    }

    @Test
    fun eachModelIsLoadedOnce() {
        val started = System.nanoTime()
        val first = models.session(VisionModel.PortraitMatting)
        println("PortraitMatting loaded in ${(System.nanoTime() - started) / 1_000_000} ms")
        assertSame(first, models.session(VisionModel.PortraitMatting))
    }

    @Test
    fun aDamagedModelSaysToReinstall() {
        val damaged = VisionModels { "not a model".encodeToByteArray() }
        val error = assertFailsWith<VisionException> { damaged.session(VisionModel.FaceDetector) }
        assertEquals("The face_detection_yunet_2023mar.onnx model could not be loaded. Reinstall Photo Dokan.", error.message)
    }
}

package com.tlw.photodokan.vision

import ai.onnxruntime.OnnxTensor
import com.tlw.photodokan.core.face.Face
import java.nio.FloatBuffer

/** Finds faces with the YuNet model in [models]. */
class OnnxFaceFinder(private val models: VisionModels) : FaceFinder {

    override fun find(argb: IntArray, width: Int, height: Int): List<Face> {
        val input = YuNet.input(argb, width, height)
        val session = models.session(VisionModel.FaceDetector)
        val shape = longArrayOf(1, 3, YuNet.SIZE.toLong(), YuNet.SIZE.toLong())
        return OnnxTensor.createTensor(models.ort, FloatBuffer.wrap(input.pixels), shape).use { tensor ->
            session.run(mapOf(session.inputNames.single() to tensor)).use { result ->
                val outputs = result.associate { (name, value) ->
                    val buffer = (value as OnnxTensor).floatBuffer
                    name to FloatArray(buffer.remaining()).also(buffer::get)
                }
                YuNet.faces(outputs, input.scale)
            }
        }
    }
}

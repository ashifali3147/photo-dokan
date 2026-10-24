package com.tlw.photodokan.vision

import ai.onnxruntime.OnnxTensor
import com.tlw.photodokan.core.matte.Matte
import java.nio.FloatBuffer

/** Separates the person from the background with the MODNet model in [models]. */
class OnnxPortraitMatter(private val models: VisionModels, private val model: VisionModel = VisionModel.PortraitMatting) : PortraitMatter {

    override fun matte(argb: IntArray, width: Int, height: Int): Matte {
        val input = ModNet.input(argb, width, height)
        val session = models.session(model)
        val shape = longArrayOf(1, 3, input.height.toLong(), input.width.toLong())
        return OnnxTensor.createTensor(models.ort, FloatBuffer.wrap(input.pixels), shape).use { tensor ->
            session.run(mapOf(session.inputNames.single() to tensor)).use { result ->
                val buffer = (result.single().value as OnnxTensor).floatBuffer
                ModNet.matte(FloatArray(buffer.remaining()).also(buffer::get), input.width, input.height)
            }
        }
    }
}

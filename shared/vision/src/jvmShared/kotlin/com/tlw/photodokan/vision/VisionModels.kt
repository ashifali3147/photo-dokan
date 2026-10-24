package com.tlw.photodokan.vision

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession

/**
 * The models, loaded into ONNX Runtime the first time each is needed and kept for the rest of the
 * run. Loading takes a moment, running a model much less. Safe to use from several threads.
 */
class VisionModels(private val files: ModelFiles) : AutoCloseable {
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val sessions = mutableMapOf<VisionModel, OrtSession>()

    /** The session that runs [model]. Throws [VisionException] when it can't be loaded. */
    @Synchronized
    fun session(model: VisionModel): OrtSession = sessions.getOrPut(model) {
        val bytes = files.read(model)
        try {
            OrtSession.SessionOptions().use { options ->
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                environment.createSession(bytes, options)
            }
        } catch (e: OrtException) {
            throw VisionException("The ${model.fileName} model could not be loaded. Reinstall Photo Dokan.", e)
        }
    }

    val ort: OrtEnvironment get() = environment

    @Synchronized
    override fun close() {
        sessions.values.forEach(OrtSession::close)
        sessions.clear()
    }
}

package com.tlw.photodokan.vision

/** The models inside the app's jar, under `models/`. */
class ResourceModelFiles : ModelFiles {
    override fun read(model: VisionModel): ByteArray =
        ResourceModelFiles::class.java.getResourceAsStream("/models/${model.fileName}")?.use { it.readBytes() }
            ?: throw VisionException("The ${model.fileName} model is missing from this build. Reinstall Photo Dokan.")
}

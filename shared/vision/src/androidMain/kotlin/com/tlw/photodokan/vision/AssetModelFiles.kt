package com.tlw.photodokan.vision

import android.content.Context
import java.io.FileNotFoundException

/** The models inside the APK, in its assets under `models/`. */
class AssetModelFiles(context: Context) : ModelFiles {
    private val assets = context.applicationContext.assets

    override fun read(model: VisionModel): ByteArray = try {
        assets.open("models/${model.fileName}").use { it.readBytes() }
    } catch (e: FileNotFoundException) {
        throw VisionException("The ${model.fileName} model is missing from this build. Reinstall Photo Dokan.", e)
    }
}

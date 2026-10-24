package com.tlw.photodokan.vision

/**
 * The small vision models Photo Dokan runs on the phone and the PC, offline. They ship inside the
 * app (see `downloadModels` in this module's build file); nothing is downloaded at run time.
 */
enum class VisionModel(val fileName: String) {
    /** YuNet: where the faces are, with eyes, nose and mouth corners. */
    FaceDetector("face_detection_yunet_2023mar.onnx"),

    /** MODNet: how much of each pixel is the person rather than the background. */
    PortraitMatting("modnet.onnx"),
}

/** Reads a model shipped with the app: from the APK's assets on the phone, the app's jar on the PC. */
fun interface ModelFiles {
    /** The model's bytes. Throws [VisionException] when this build doesn't have it. */
    fun read(model: VisionModel): ByteArray
}

/** Why a model couldn't be used; [message] is for the operator. */
class VisionException(message: String, cause: Throwable? = null) : Exception(message, cause)

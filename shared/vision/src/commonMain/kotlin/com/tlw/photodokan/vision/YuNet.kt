package com.tlw.photodokan.vision

import com.tlw.photodokan.core.face.Face
import com.tlw.photodokan.core.face.PointPx
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The YuNet face detector's input and output (opencv_zoo, face_detection_yunet_2023mar): a
 * [SIZE] × [SIZE] BGR image with values 0–255, and, for each cell of three grids, a face score,
 * box and five landmarks.
 */
object YuNet {
    const val SIZE = 640
    private val STRIDES = intArrayOf(8, 16, 32)

    /** Faces less sure than this are dropped; a customer facing the camera scores far higher. */
    const val MIN_SCORE = 0.6

    /** Boxes overlapping more than this are the same face. */
    private const val SAME_FACE = 0.3

    /** The photo scaled down by [scale] into the top-left of the model's square, the rest black. */
    class Input(val pixels: FloatArray, val scale: Double)

    /**
     * A [width] × [height] ARGB photo as the model's input: shrunk to fit [SIZE], each pixel the
     * average of the photo pixels it covers so small details don't flicker in or out.
     */
    fun input(argb: IntArray, width: Int, height: Int): Input {
        val scale = min(1.0, SIZE.toDouble() / max(width, height))
        val outW = max(1, (width * scale).toInt())
        val outH = max(1, (height * scale).toInt())
        val plane = SIZE * SIZE
        val pixels = FloatArray(3 * plane)
        shrink(argb, width, height, outW, outH) { i, r, g, b ->
            // Into the square's rows, which are wider than the photo's.
            val at = (i / outW) * SIZE + i % outW
            pixels[at] = b
            pixels[plane + at] = g
            pixels[2 * plane + at] = r
        }
        return Input(pixels, scale)
    }

    /**
     * The faces in the model's [outputs] (by output name, e.g. `cls_8`), most certain first, in the
     * photo's pixels: the model's divided by [scale].
     */
    fun faces(outputs: Map<String, FloatArray>, scale: Double): List<Face> {
        val found = mutableListOf<Face>()
        for (stride in STRIDES) {
            val cls = outputs.getValue("cls_$stride")
            val obj = outputs.getValue("obj_$stride")
            val box = outputs.getValue("bbox_$stride")
            val kps = outputs.getValue("kps_$stride")
            val cols = SIZE / stride
            for (i in cls.indices) {
                val score = sqrt(cls[i].coerceIn(0f, 1f).toDouble() * obj[i].coerceIn(0f, 1f))
                if (score < MIN_SCORE) continue
                val col = i % cols
                val row = i / cols
                fun point(k: Int) = PointPx((col + kps[i * 10 + 2 * k]) * stride / scale, (row + kps[i * 10 + 2 * k + 1]) * stride / scale)
                val cx = (col + box[i * 4]) * stride
                val cy = (row + box[i * 4 + 1]) * stride
                val w = exp(box[i * 4 + 2].toDouble()) * stride
                val h = exp(box[i * 4 + 3].toDouble()) * stride
                // YuNet's landmarks are the person's right eye, left eye, nose, right and left mouth corner:
                // the person's right is the photo's left.
                found += Face(
                    x = (cx - w / 2) / scale, y = (cy - h / 2) / scale, width = w / scale, height = h / scale,
                    eyeLeft = point(0), eyeRight = point(1), nose = point(2), mouthLeft = point(3), mouthRight = point(4),
                    score = score,
                )
            }
        }
        // Each face shows up in several neighbouring cells; keep the most certain of each.
        val kept = mutableListOf<Face>()
        for (face in found.sortedByDescending { it.score }) {
            if (kept.none { overlap(it, face) > SAME_FACE }) kept += face
        }
        return kept
    }

    /** Intersection over union of two faces' boxes. */
    private fun overlap(a: Face, b: Face): Double {
        val w = min(a.x + a.width, b.x + b.width) - max(a.x, b.x)
        val h = min(a.y + a.height, b.y + b.height) - max(a.y, b.y)
        if (w <= 0 || h <= 0) return 0.0
        val shared = w * h
        return shared / (a.area + b.area - shared)
    }
}

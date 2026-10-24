package com.tlw.photodokan.vision

import com.tlw.photodokan.core.face.Face

/** Finds the faces on a photo. Blocking: call it off the main thread. */
fun interface FaceFinder {
    /**
     * The faces on a [width] × [height] photo given as ARGB pixels, row by row, in that photo's
     * pixels; none when there is no face. Throws [VisionException] when the model can't be used.
     */
    fun find(argb: IntArray, width: Int, height: Int): List<Face>
}

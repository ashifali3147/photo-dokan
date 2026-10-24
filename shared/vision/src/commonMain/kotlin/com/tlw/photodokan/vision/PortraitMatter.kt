package com.tlw.photodokan.vision

import com.tlw.photodokan.core.matte.Matte

/** Separates the person on a photo from the background. Blocking and slow: call it off the main thread. */
fun interface PortraitMatter {
    /**
     * How much of each pixel of a [width] × [height] photo, given as ARGB pixels row by row, is
     * the person. Throws [VisionException] when the model can't be used.
     */
    fun matte(argb: IntArray, width: Int, height: Int): Matte
}

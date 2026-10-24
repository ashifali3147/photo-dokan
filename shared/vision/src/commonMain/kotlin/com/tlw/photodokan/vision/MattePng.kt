package com.tlw.photodokan.vision

import com.tlw.photodokan.core.matte.Matte

/** Mattes as small greyscale PNG files, for keeping in the photo library. */
expect object MattePng {
    fun encode(matte: Matte): ByteArray

    /** The matte in a PNG made by [encode], or null for anything else, e.g. a damaged file. */
    fun decode(png: ByteArray): Matte?
}

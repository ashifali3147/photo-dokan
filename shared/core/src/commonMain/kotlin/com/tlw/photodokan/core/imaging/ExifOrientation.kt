package com.tlw.photodokan.core.imaging

/**
 * How a camera stored a photo relative to how it should be shown. Phones usually save the sensor
 * image as-is and record the rotation here instead of rotating the pixels.
 *
 * To show the photo upright: mirror it left-right if [mirrored], then rotate it clockwise by
 * [rotationDegrees]. The original file is never changed; this only affects how it is drawn.
 */
enum class ExifOrientation(val exifValue: Int, val rotationDegrees: Int, val mirrored: Boolean) {
    NORMAL(1, 0, false),
    MIRROR(2, 0, true),
    ROTATE_180(3, 180, false),
    MIRROR_ROTATE_180(4, 180, true),
    MIRROR_ROTATE_270(5, 270, true),
    ROTATE_90(6, 90, false),
    MIRROR_ROTATE_90(7, 90, true),
    ROTATE_270(8, 270, false);

    /** True when the upright photo's width is the stored height. */
    val swapsDimensions: Boolean get() = rotationDegrees % 180 != 0

    companion object {
        private const val ORIENTATION_TAG = 0x0112
        private const val TYPE_SHORT = 3

        fun fromExifValue(value: Int): ExifOrientation? = entries.firstOrNull { it.exifValue == value }

        /**
         * Reads the orientation from a JPEG's EXIF data. Anything else (PNG, no EXIF, damaged
         * data) is treated as [NORMAL]: a photo should still open, just as stored.
         */
        fun read(jpeg: ByteArray): ExifOrientation = runCatching { find(jpeg) }.getOrNull() ?: NORMAL

        private fun find(b: ByteArray): ExifOrientation? {
            if (b.size < 4 || b.u8(0) != 0xFF || b.u8(1) != 0xD8) return null
            var i = 2
            while (i + 4 <= b.size) {
                if (b.u8(i) != 0xFF) return null
                val marker = b.u8(i + 1)
                when {
                    marker == 0xFF -> { i++; continue } // fill byte
                    marker == 0xD9 || marker == 0xDA -> return null // end of image / start of pixel data
                    marker == 0x01 || marker in 0xD0..0xD7 -> { i += 2; continue } // markers without a length
                }
                val length = b.u16(i + 2, bigEndian = true)
                val start = i + 4
                val end = i + 2 + length
                if (length < 2 || end > b.size) return null
                if (marker == 0xE1 && end - start >= 6 && b.isExifHeader(start)) {
                    return readTiff(b, start + 6, end)
                }
                i = end
            }
            return null
        }

        private fun readTiff(b: ByteArray, tiff: Int, end: Int): ExifOrientation? {
            if (tiff + 8 > end) return null
            val bigEndian = when {
                b.u8(tiff) == 'M'.code && b.u8(tiff + 1) == 'M'.code -> true
                b.u8(tiff) == 'I'.code && b.u8(tiff + 1) == 'I'.code -> false
                else -> return null
            }
            if (b.u16(tiff + 2, bigEndian) != 42) return null
            val ifd = tiff + b.u32(tiff + 4, bigEndian)
            if (ifd < tiff || ifd + 2 > end) return null
            val count = b.u16(ifd, bigEndian)
            for (n in 0 until count) {
                val entry = ifd + 2 + n * 12
                if (entry + 12 > end) return null
                if (b.u16(entry, bigEndian) == ORIENTATION_TAG && b.u16(entry + 2, bigEndian) == TYPE_SHORT) {
                    return fromExifValue(b.u16(entry + 8, bigEndian))
                }
            }
            return null
        }

        private fun ByteArray.isExifHeader(at: Int) =
            u8(at) == 'E'.code && u8(at + 1) == 'x'.code && u8(at + 2) == 'i'.code &&
                u8(at + 3) == 'f'.code && u8(at + 4) == 0 && u8(at + 5) == 0

        private fun ByteArray.u8(at: Int): Int = this[at].toInt() and 0xFF

        private fun ByteArray.u16(at: Int, bigEndian: Boolean): Int =
            if (bigEndian) (u8(at) shl 8) or u8(at + 1) else (u8(at + 1) shl 8) or u8(at)

        private fun ByteArray.u32(at: Int, bigEndian: Boolean): Int =
            if (bigEndian) (u16(at, true) shl 16) or u16(at + 2, true) else (u16(at + 2, false) shl 16) or u16(at, false)
    }
}

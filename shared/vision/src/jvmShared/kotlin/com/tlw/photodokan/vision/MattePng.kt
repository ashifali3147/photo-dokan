package com.tlw.photodokan.vision

import com.tlw.photodokan.core.matte.Matte
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * 8-bit greyscale PNG with plain `java.util.zip`, the same on the phone and the PC. Each row is
 * stored as the difference from the row above, which suits a matte's large flat areas.
 */
actual object MattePng {
    private val SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
    private const val GREY = 0
    private const val FILTER_NONE = 0
    private const val FILTER_UP = 2

    actual fun encode(matte: Matte): ByteArray {
        val w = matte.width
        val rows = ByteArrayOutputStream()
        DeflaterOutputStream(rows, Deflater(Deflater.BEST_COMPRESSION)).use { out ->
            val line = ByteArray(w + 1)
            for (y in 0 until matte.height) {
                line[0] = FILTER_UP.toByte()
                for (x in 0 until w) {
                    val above = if (y == 0) 0 else matte.alpha[(y - 1) * w + x].toInt()
                    line[x + 1] = (matte.alpha[y * w + x] - above).toByte()
                }
                out.write(line)
            }
        }
        val png = ByteArrayOutputStream()
        png.write(SIGNATURE)
        val header = ByteArrayOutputStream().also { h ->
            DataOutputStream(h).apply { writeInt(w); writeInt(matte.height); write(byteArrayOf(8, GREY.toByte(), 0, 0, 0)) }
        }.toByteArray()
        chunk(png, "IHDR", header)
        chunk(png, "IDAT", rows.toByteArray())
        chunk(png, "IEND", ByteArray(0))
        return png.toByteArray()
    }

    actual fun decode(png: ByteArray): Matte? = runCatching {
        val input = DataInputStream(png.inputStream())
        if (!ByteArray(8).also(input::readFully).contentEquals(SIGNATURE)) return null
        var width = 0
        var height = 0
        val data = ByteArrayOutputStream()
        while (true) {
            val length = input.readInt()
            val type = ByteArray(4).also(input::readFully).decodeToString()
            val body = ByteArray(length).also(input::readFully)
            input.readInt() // CRC: a damaged file fails below instead
            when (type) {
                "IHDR" -> {
                    val h = DataInputStream(body.inputStream())
                    width = h.readInt()
                    height = h.readInt()
                    // Only what encode writes: 8-bit grey, not interlaced.
                    if (body[8].toInt() != 8 || body[9].toInt() != GREY || body[12].toInt() != 0) return null
                }
                "IDAT" -> data.write(body)
                "IEND" -> break
            }
        }
        val raw = InflaterInputStream(data.toByteArray().inputStream(), Inflater()).readBytes()
        if (width <= 0 || height <= 0 || raw.size != (width + 1) * height) return null
        val alpha = ByteArray(width * height)
        for (y in 0 until height) {
            val filter = raw[y * (width + 1)].toInt()
            for (x in 0 until width) {
                val value = raw[y * (width + 1) + 1 + x].toInt()
                val above = if (y == 0) 0 else alpha[(y - 1) * width + x].toInt()
                alpha[y * width + x] = when (filter) {
                    FILTER_NONE -> value
                    FILTER_UP -> value + above
                    else -> return null
                }.toByte()
            }
        }
        Matte(width, height, alpha)
    }.getOrNull()

    private fun chunk(out: ByteArrayOutputStream, type: String, body: ByteArray) {
        val typed = type.encodeToByteArray() + body
        val crc = CRC32().apply { update(typed) }.value.toInt()
        DataOutputStream(out).apply { writeInt(body.size); write(typed); writeInt(crc) }
    }
}

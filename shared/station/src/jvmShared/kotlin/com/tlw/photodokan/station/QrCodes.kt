package com.tlw.photodokan.station

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

actual object QrCodes {

    actual fun encode(text: String): List<BooleanArray> {
        // Medium error correction: a little smudge or glare on the screen still reads.
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0)
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        return List(matrix.height) { y -> BooleanArray(matrix.width) { x -> matrix[x, y] } }
    }

    actual fun decode(luminance: ByteArray, width: Int, height: Int): String? {
        val source = PlanarYUVLuminanceSource(luminance, width, height, 0, 0, width, height, false)
        return try {
            QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), mapOf(DecodeHintType.TRY_HARDER to true)).text
        } catch (e: NotFoundException) {
            null
        } catch (e: ReaderException) {
            null // found but unreadable this frame; the next one may do
        }
    }
}

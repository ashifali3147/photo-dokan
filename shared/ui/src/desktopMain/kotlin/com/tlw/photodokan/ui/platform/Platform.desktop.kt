package com.tlw.photodokan.ui.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import com.tlw.photodokan.core.library.StudioDate
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

actual fun today(): StudioDate = LocalDate.now().let { StudioDate(it.year, it.monthValue, it.dayOfMonth) }

actual fun encodeJpeg(image: ImageBitmap, quality: Int): ByteArray {
    // JPEG has no alpha channel: draw onto plain RGB first, or ImageIO writes nothing.
    val source = image.toAwtImage()
    val rgb = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB)
    rgb.createGraphics().apply { drawImage(source, 0, 0, null); dispose() }

    val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
    val out = ByteArrayOutputStream()
    ImageIO.createImageOutputStream(out).use { stream ->
        writer.output = stream
        val param = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = quality / 100f
        }
        writer.write(null, IIOImage(rgb, null, null), param)
        writer.dispose()
    }
    return out.toByteArray()
}

package com.tlw.photodokan.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
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

actual fun imageBitmapOf(argb: IntArray, width: Int, height: Int): ImageBitmap =
    BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply { setRGB(0, 0, width, height, argb, 0, width) }.toComposeImageBitmap()

actual fun monotonicNanos(): Long = System.nanoTime()

/** The studio PC is measured with its own tools; nothing is logged here. */
actual fun logTiming(what: String, millis: Long) = Unit

@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

@Composable
actual fun SystemBarsFollowTheme(dark: Boolean) = Unit

actual val tapVerb: String = "click"

package com.tlw.photodokan.ui.platform

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.tlw.photodokan.core.library.StudioDate
import java.io.ByteArrayOutputStream
import java.time.LocalDate

actual fun today(): StudioDate = LocalDate.now().let { StudioDate(it.year, it.monthValue, it.dayOfMonth) }

actual fun encodeJpeg(image: ImageBitmap, quality: Int): ByteArray =
    ByteArrayOutputStream().also { image.asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

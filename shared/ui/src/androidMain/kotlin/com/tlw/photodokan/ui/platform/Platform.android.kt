package com.tlw.photodokan.ui.platform

import android.graphics.Bitmap
import android.os.Debug
import android.os.SystemClock
import android.util.Log
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

actual fun monotonicNanos(): Long = SystemClock.elapsedRealtimeNanos()

actual fun logTiming(what: String, millis: Long) {
    val runtime = Runtime.getRuntime()
    val heapMb = (runtime.totalMemory() - runtime.freeMemory()) shr 20
    // Bitmaps' pixels live outside the Java heap, in native memory.
    val nativeMb = Debug.getNativeHeapAllocatedSize() shr 20
    Log.i("PhotoDokanPerf", "$what: $millis ms (heap $heapMb of ${runtime.maxMemory() shr 20} MB, native $nativeMb MB)")
}

@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

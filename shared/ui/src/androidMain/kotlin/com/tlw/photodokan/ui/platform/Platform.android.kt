package com.tlw.photodokan.ui.platform

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.BackHandler
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.tlw.photodokan.core.library.StudioDate
import java.io.ByteArrayOutputStream
import java.time.LocalDate

actual fun today(): StudioDate = LocalDate.now().let { StudioDate(it.year, it.monthValue, it.dayOfMonth) }

actual fun encodeJpeg(image: ImageBitmap, quality: Int): ByteArray =
    ByteArrayOutputStream().also { image.asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

actual fun imageBitmapOf(argb: IntArray, width: Int, height: Int): ImageBitmap =
    Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

actual fun Paint.blur(radius: Float) {
    asFrameworkPaint().maskFilter = if (radius > 0f) BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL) else null
}

actual val tapVerb: String = "tap"

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

@Composable
actual fun SystemBarsFollowTheme(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = view.context.activity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

package com.tlw.photodokan.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import com.tlw.photodokan.core.library.StudioDate

/** Today's date on this device's clock. */
expect fun today(): StudioDate

/** Encodes an image as JPEG, e.g. the final.jpg kept in the library. */
expect fun encodeJpeg(image: ImageBitmap, quality: Int = 90): ByteArray

/** A [width] × [height] image of [argb] pixels, row by row: the counterpart of [ImageBitmap.readPixels]. */
expect fun imageBitmapOf(argb: IntArray, width: Int, height: Int): ImageBitmap

/**
 * Runs [block] and, on the phone, logs how long [what] took and how much memory is in use, for
 * checking speed on a real phone: `adb logcat -s PhotoDokanPerf`.
 */
inline fun <T> timed(what: String, block: () -> T): T {
    val start = monotonicNanos()
    val result = block()
    logTiming(what, (monotonicNanos() - start) / 1_000_000)
    return result
}

/** A clock for measuring durations only; it has nothing to do with the date. */
expect fun monotonicNanos(): Long

/** Logs that [what] took [millis] on the phone; nothing on the PC. */
expect fun logTiming(what: String, millis: Long)

/**
 * Runs [onBack] instead of the system Back gesture while [enabled]. The innermost enabled one
 * wins. Desktop windows have no Back, so there it does nothing.
 */
@Composable
expect fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit)

/**
 * Draws the phone's status and navigation bar icons dark on a light app, light on a dark one,
 * following the app's own theme rather than the phone's. Nothing on the PC.
 */
@Composable
expect fun SystemBarsFollowTheme(dark: Boolean)

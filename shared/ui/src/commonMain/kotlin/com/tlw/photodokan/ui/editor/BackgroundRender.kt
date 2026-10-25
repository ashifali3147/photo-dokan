package com.tlw.photodokan.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.tlw.photodokan.core.matte.Background
import com.tlw.photodokan.core.matte.Cutout
import com.tlw.photodokan.core.matte.Matte
import com.tlw.photodokan.core.matte.touchedUp
import com.tlw.photodokan.core.model.BackgroundConfig
import com.tlw.photodokan.core.model.BackgroundMode
import com.tlw.photodokan.core.model.MaskStroke
import com.tlw.photodokan.ui.platform.imageBitmapOf
import com.tlw.photodokan.ui.platform.timed
import kotlin.concurrent.Volatile

/**
 * This photo with its background changed as [config] says, behind the person in [matte]: the same
 * for the editor, final.jpg and print. For a plain colour it is the person alone, to be drawn over
 * [backdrop] of the config. Without a matte, or to keep the background, the photo as it is.
 * Blocking: call it off the UI thread.
 */
fun ImageBitmap.withBackground(matte: Matte?, config: BackgroundConfig): ImageBitmap = BackgroundPainter().paint(this, matte, config)

/**
 * Paints photos with their background changed, remembering the last photo's [Cutout] so that
 * changing only the background's settings, as the editor does, redoes just the background. A new
 * touch-up cuts the person out again. Blocking, and safe to use from several threads at once.
 */
class BackgroundPainter {
    private class Last(val image: ImageBitmap, val matte: Matte, val touchUps: List<MaskStroke>, val cutout: Cutout)

    @Volatile
    private var last: Last? = null

    /** [image] with its background changed as [config] says; see [withBackground]. */
    fun paint(image: ImageBitmap, matte: Matte?, config: BackgroundConfig): ImageBitmap {
        if (matte == null || config.mode == BackgroundMode.KEEP) return image
        val width = image.width
        val height = image.height
        val touchUps = config.touchUps
        val cutout = last?.takeIf { it.image === image && it.matte === matte && it.touchUps == touchUps }?.cutout
            ?: timed("Cut out the person ${width}x$height") {
                Cutout(IntArray(width * height).also { image.readPixels(it) }, width, height, matte.touchedUp(touchUps, width.toDouble() / height))
            }.also { last = Last(image, matte, touchUps, it) }
        val changed = timed("Background ${config.mode.name.lowercase()} ${width}x$height") {
            if (config.mode == BackgroundMode.REPLACE) Background.person(cutout) else Background.apply(cutout, config)
        }
        return imageBitmapOf(changed, width, height)
    }
}

/** What shows behind a photo where it is clear: the plain colour it was given, or white. */
fun backdrop(config: BackgroundConfig): Color =
    if (config.mode == BackgroundMode.REPLACE) Color(config.replaceColorArgb.toInt()) else Color.White

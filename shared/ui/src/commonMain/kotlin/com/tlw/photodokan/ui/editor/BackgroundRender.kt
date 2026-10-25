package com.tlw.photodokan.ui.editor

import androidx.compose.ui.graphics.ImageBitmap
import com.tlw.photodokan.core.matte.Background
import com.tlw.photodokan.core.matte.Cutout
import com.tlw.photodokan.core.matte.Matte
import com.tlw.photodokan.core.model.BackgroundConfig
import com.tlw.photodokan.core.model.BackgroundMode
import com.tlw.photodokan.ui.platform.imageBitmapOf
import com.tlw.photodokan.ui.platform.timed
import kotlin.concurrent.Volatile

/**
 * This photo with its background changed as [config] says, behind the person in [matte]: the same
 * for the editor, final.jpg and print. Without a matte, or to keep the background, the photo as it
 * is. Blocking: call it off the UI thread.
 */
fun ImageBitmap.withBackground(matte: Matte?, config: BackgroundConfig): ImageBitmap = BackgroundPainter().paint(this, matte, config)

/**
 * Paints photos with their background changed, remembering the last photo's [Cutout] so that
 * changing only the background's settings, as the editor does, redoes just the background.
 * Blocking, and safe to use from several threads at once.
 */
class BackgroundPainter {
    private class Last(val image: ImageBitmap, val matte: Matte, val cutout: Cutout)

    @Volatile
    private var last: Last? = null

    /** [image] with its background changed as [config] says; see [withBackground]. */
    fun paint(image: ImageBitmap, matte: Matte?, config: BackgroundConfig): ImageBitmap {
        if (matte == null || config.mode == BackgroundMode.KEEP) return image
        val width = image.width
        val height = image.height
        val cutout = last?.takeIf { it.image === image && it.matte === matte }?.cutout
            ?: timed("Cut out the person ${width}x$height") {
                Cutout(IntArray(width * height).also { image.readPixels(it) }, width, height, matte)
            }.also { last = Last(image, matte, it) }
        val changed = timed("Background ${config.mode.name.lowercase()} ${width}x$height") { Background.apply(cutout, config) }
        return imageBitmapOf(changed, width, height)
    }
}

package com.tlw.photodokan.ui.editor

import androidx.compose.ui.graphics.ImageBitmap
import com.tlw.photodokan.core.matte.Background
import com.tlw.photodokan.core.matte.Matte
import com.tlw.photodokan.core.model.BackgroundConfig
import com.tlw.photodokan.core.model.BackgroundMode
import com.tlw.photodokan.ui.platform.imageBitmapOf
import com.tlw.photodokan.ui.platform.timed

/**
 * This photo with its background changed as [config] says, behind the person in [matte]: the same
 * for the editor, final.jpg and print. Without a matte, or to keep the background, the photo as it
 * is. Blocking: call it off the UI thread.
 */
fun ImageBitmap.withBackground(matte: Matte?, config: BackgroundConfig): ImageBitmap {
    if (matte == null || config.mode == BackgroundMode.KEEP) return this
    val argb = IntArray(width * height).also { readPixels(it) }
    val changed = timed("Background ${config.mode.name.lowercase()} ${width}x$height") { Background.apply(argb, width, height, matte, config) }
    return imageBitmapOf(changed, width, height)
}

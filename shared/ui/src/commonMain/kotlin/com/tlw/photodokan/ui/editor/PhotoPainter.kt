package com.tlw.photodokan.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.tlw.photodokan.core.imaging.StudioLook
import com.tlw.photodokan.core.matte.Background
import com.tlw.photodokan.core.matte.Cutout
import com.tlw.photodokan.core.matte.Matte
import com.tlw.photodokan.core.matte.touchedUp
import com.tlw.photodokan.core.model.BackgroundConfig
import com.tlw.photodokan.core.model.BackgroundMode
import com.tlw.photodokan.core.model.EditRecipe
import com.tlw.photodokan.core.model.MaskStroke
import com.tlw.photodokan.core.model.Portrait
import com.tlw.photodokan.ui.platform.imageBitmapOf
import com.tlw.photodokan.ui.platform.timed
import kotlin.concurrent.Volatile

/**
 * The part of a recipe that changes the photo's pixels, rather than how it is cropped or drawn:
 * its background, the studio look and fair skin. When only the rest changes, the painted photo is
 * reused.
 */
data class Painting(val background: BackgroundConfig, val enhance: Double, val portrait: Portrait?, val fairness: Double = 0.0) {
    /** The look or fair skin changes the photo's pixels. */
    val looks: Boolean get() = enhance > 0 || (fairness > 0 && portrait != null)

    /** The photo looks different from how it was shot. */
    val changesPhoto: Boolean get() = background.mode != BackgroundMode.KEEP || looks

    /** The person must be cut out first. */
    val needsMatte: Boolean get() = background.mode != BackgroundMode.KEEP
}

val EditRecipe.painting: Painting get() = Painting(background, enhance, portrait, fairness)

/**
 * This photo painted as [painting] says, behind and on the person in [matte]: the same for the
 * editor, final.jpg and print. For a plain colour it is the person alone, to be drawn over the
 * [backdrop]. Blocking: call it off the UI thread.
 */
fun ImageBitmap.painted(matte: Matte?, painting: Painting): ImageBitmap = PhotoPainter().paint(this, matte, painting)

/**
 * Paints photos with their background changed and the studio look, remembering the last photo's
 * [Cutout] so that changing only the settings, as the editor does, redoes just what they change.
 * A new touch-up cuts the person out again. Blocking, and safe to use from several threads at once.
 */
class PhotoPainter {
    private class Last(val image: ImageBitmap, val matte: Matte, val touchUps: List<MaskStroke>, val cutout: Cutout)

    @Volatile
    private var last: Last? = null

    /** [image] painted as [painting] says; see [painted]. Without a [matte] the background stays as shot. */
    fun paint(image: ImageBitmap, matte: Matte?, painting: Painting): ImageBitmap {
        val config = painting.background
        val cutBackground = matte != null && config.mode != BackgroundMode.KEEP
        if (!cutBackground && !painting.looks) return image
        val width = image.width
        val height = image.height

        val cutout = if (cutBackground) cutout(image, matte!!, config.touchUps) else null
        val pixels = when {
            cutout == null -> IntArray(width * height).also { image.readPixels(it) }
            else -> timed("Background ${config.mode.name.lowercase()} ${width}x$height") {
                if (config.mode == BackgroundMode.REPLACE) Background.person(cutout) else Background.apply(cutout, config)
            }
        }
        val looked = if (!painting.looks) pixels else timed("Studio look ${width}x$height") {
            // With the background as shot, print has no matte to go by, so neither does the editor.
            StudioLook.apply(pixels, width, height, painting.enhance, painting.portrait, person = cutout?.alpha, fairness = painting.fairness)
        }
        return imageBitmapOf(looked, width, height)
    }

    private fun cutout(image: ImageBitmap, matte: Matte, touchUps: List<MaskStroke>): Cutout {
        last?.takeIf { it.image === image && it.matte === matte && it.touchUps == touchUps }?.let { return it.cutout }
        val width = image.width
        val height = image.height
        return timed("Cut out the person ${width}x$height") {
            Cutout(IntArray(width * height).also { image.readPixels(it) }, width, height, matte.touchedUp(touchUps, width.toDouble() / height))
        }.also { last = Last(image, matte, touchUps, it) }
    }
}

/** What shows behind a photo where it is clear: the plain colour it was given, or white. */
fun backdrop(config: BackgroundConfig): Color =
    if (config.mode == BackgroundMode.REPLACE) Color(config.replaceColorArgb.toInt()) else Color.White

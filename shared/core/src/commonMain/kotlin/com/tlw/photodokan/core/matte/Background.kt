package com.tlw.photodokan.core.matte

import com.tlw.photodokan.core.model.BackgroundConfig
import com.tlw.photodokan.core.model.BackgroundMode

/**
 * Changes the background of a photo behind the person in its [Matte]: blurred softly (the studio's
 * backdrop kept, its creases and seams gone) or replaced by a solid colour. The same maths for
 * the editor, final.jpg and print, at whatever size the photo is decoded.
 *
 * The matte's edges are first fitted to the photo ([MatteEdges]), and along them the old backdrop's
 * colour is taken out of the hair before the new background goes in, so no rim of the old backdrop
 * shows around the person.
 */
object Background {
    /** The blur radius at full strength, as a share of the photo's height. */
    const val MAX_BLUR = 0.04

    /**
     * [argb], a [width] × [height] photo row by row, with its background changed as [config] says,
     * behind the person in [matte] with the config's touch-ups painted on. KEEP gives the photo back
     * unchanged.
     */
    fun apply(argb: IntArray, width: Int, height: Int, matte: Matte, config: BackgroundConfig): IntArray {
        require(width > 0 && height > 0 && argb.size == width * height) { "Need $width × $height pixels, had ${argb.size}" }
        if (changesNothing(config, height)) return argb.copyOf()
        return apply(Cutout(argb, width, height, matte.touchedUp(config.touchUps, width.toDouble() / height)), config)
    }

    /**
     * The [cutout]'s photo with its background changed as [config] says: quicker than starting
     * from the matte when only the background's settings change, as they do in the editor. The
     * cutout must already have [config]'s touch-ups painted on (see [touchedUp]).
     */
    fun apply(cutout: Cutout, config: BackgroundConfig): IntArray {
        val argb = cutout.argb
        val width = cutout.width
        val height = cutout.height
        if (changesNothing(config, height)) return argb.copyOf()

        val alpha = cutout.alpha
        val out = IntArray(width * height)
        val blurred = if (config.mode == BackgroundMode.BLUR) NearbyColour(argb, width, height, alpha, blurRadius(config, height)).Rows() else null
        val oldBackdrop = cutout.oldBackdrop.Rows()
        val ownColour = cutout.ownColour.Rows()
        val colour = config.replaceColorArgb.toInt()
        val back = IntArray(width)
        val old = IntArray(width)
        val own = IntArray(width)
        for (y in 0 until height) {
            val row = y * width
            // Rows that are all person need no background.
            var any = false
            for (x in 0 until width) if (alpha[row + x] != PERSON) { any = true; break }
            if (!any) {
                for (x in 0 until width) out[row + x] = argb[row + x] or OPAQUE
                continue
            }
            if (blurred != null) blurred.read(y, back) else back.fill(colour)
            oldBackdrop.read(y, old)
            ownColour.read(y, own)
            for (x in 0 until width) {
                val a = alpha[row + x].toInt() and 0xff
                out[row + x] = when {
                    a == 255 -> argb[row + x] or OPAQUE
                    a == 0 -> back[x] or OPAQUE
                    old[x] == NearbyColour.UNKNOWN || own[x] == NearbyColour.UNKNOWN -> mix(argb[row + x], back[x], a)
                    else -> swap(argb[row + x], own[x], old[x], back[x], a)
                }
            }
        }
        return out
    }

    private fun blurRadius(config: BackgroundConfig, height: Int) = config.blurStrength * MAX_BLUR * height

    private fun changesNothing(config: BackgroundConfig, height: Int) =
        config.mode == BackgroundMode.KEEP || (config.mode == BackgroundMode.BLUR && blurRadius(config, height) < 0.5)

    private const val PERSON: Byte = -1 // 255
    private const val OPAQUE = 0xff shl 24

    /** [person] over [back], [a] of 255 the person. */
    private fun mix(person: Int, back: Int, a: Int): Int {
        val b = 255 - a
        fun channel(shift: Int) = ((((person shr shift) and 0xff) * a + ((back shr shift) and 0xff) * b + 127) / 255)
        return OPAQUE or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /**
     * An edge pixel [pixel], [a] of 255 the person, with the backdrop's share replaced by [new]:
     * the person's colour there is the [own] colour of the person nearby, corrected by whatever of
     * the pixel that and the [old] backdrop nearby don't explain. The correction is scaled down
     * where the pixel is mostly backdrop, so a wrong guess about the backdrop can't paint a ghost
     * into the new one (approximate fast foreground colour estimation, Forte and Pitié).
     */
    private fun swap(pixel: Int, own: Int, old: Int, new: Int, a: Int): Int {
        val f = a / 255f
        fun channel(shift: Int): Int {
            val i = (pixel shr shift) and 0xff
            val person = (own shr shift) and 0xff
            val rest = i - f * person - (1 - f) * ((old shr shift) and 0xff)
            val front = (person + f * rest).coerceIn(0f, 255f)
            return (f * front + (1 - f) * ((new shr shift) and 0xff) + 0.5f).toInt().coerceIn(0, 255)
        }
        return OPAQUE or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}

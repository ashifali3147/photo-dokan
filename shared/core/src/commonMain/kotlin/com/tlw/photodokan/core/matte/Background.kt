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
    fun apply(cutout: Cutout, config: BackgroundConfig): IntArray = when {
        changesNothing(config, cutout.height) -> cutout.argb.copyOf()
        config.mode == BackgroundMode.REPLACE -> over(person(cutout), config.replaceColorArgb.toInt())
        else -> layOut(cutout, NearbyColour(cutout.argb, cutout.width, cutout.height, cutout.alpha, blurRadius(config, cutout.height)).Rows())
    }

    /**
     * The person alone, cut out of the [cutout]'s photo: each pixel's colour is the person's own,
     * with the old backdrop's share taken out along the edges, and its alpha is how much of it is
     * the person. Drawn over a plain colour it gives the same as [apply]; drawn with the light and
     * colour sliders over that colour, the colour itself stays exactly as chosen.
     */
    fun person(cutout: Cutout): IntArray = layOut(cutout, blurred = null)

    /** [layer], a [person], laid over [colour]. */
    fun over(layer: IntArray, colour: Int): IntArray = IntArray(layer.size) { i ->
        val a = (layer[i] ushr 24) and 0xff
        when (a) {
            255 -> layer[i]
            0 -> colour or OPAQUE
            else -> blend(layer[i], colour, a)
        }
    }

    /**
     * Each pixel of the [cutout]'s photo as the person's own colour, then either laid over the
     * [blurred] backdrop or, without one, kept apart with how much of it is the person as alpha.
     */
    private fun layOut(cutout: Cutout, blurred: NearbyColour.Rows?): IntArray {
        val argb = cutout.argb
        val width = cutout.width
        val alpha = cutout.alpha
        val out = IntArray(argb.size)
        val oldBackdrop = cutout.oldBackdrop.Rows()
        val ownColour = cutout.ownColour.Rows()
        val back = IntArray(width)
        val old = IntArray(width)
        val own = IntArray(width)
        for (y in 0 until cutout.height) {
            val row = y * width
            // Rows that are all person need no background.
            var any = false
            for (x in 0 until width) if (alpha[row + x] != PERSON) { any = true; break }
            if (!any) {
                for (x in 0 until width) out[row + x] = argb[row + x] or OPAQUE
                continue
            }
            blurred?.read(y, back)
            oldBackdrop.read(y, old)
            ownColour.read(y, own)
            for (x in 0 until width) {
                val a = alpha[row + x].toInt() and 0xff
                val pixel = argb[row + x]
                val known = old[x] != NearbyColour.UNKNOWN && own[x] != NearbyColour.UNKNOWN
                // The person's own colour here: as shot where it is all person or nothing is known
                // about either side; with the old backdrop taken out along the edges.
                val front = when {
                    a == 255 || !known && a > 0 -> pixel
                    a == 0 -> if (own[x] != NearbyColour.UNKNOWN) own[x] else pixel
                    else -> front(pixel, own[x], old[x], a)
                }
                out[row + x] = when {
                    blurred == null -> (a shl 24) or (front and 0xffffff)
                    a == 255 -> pixel or OPAQUE
                    a == 0 -> back[x] or OPAQUE
                    else -> blend(front, back[x], a)
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
    private fun blend(person: Int, back: Int, a: Int): Int {
        val b = 255 - a
        fun channel(shift: Int) = ((((person shr shift) and 0xff) * a + ((back shr shift) and 0xff) * b + 127) / 255)
        return OPAQUE or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /**
     * The person's own colour in an edge pixel [pixel], [a] of 255 the person: the [own] colour of
     * the person nearby, corrected by whatever of the pixel that and the [old] backdrop nearby don't
     * explain. The correction is scaled down where the pixel is mostly backdrop, so a wrong guess
     * about the backdrop can't paint a ghost into the new one (approximate fast foreground colour
     * estimation, Forte and Pitié).
     */
    private fun front(pixel: Int, own: Int, old: Int, a: Int): Int {
        val f = a / 255f
        fun channel(shift: Int): Int {
            val i = (pixel shr shift) and 0xff
            val person = (own shr shift) and 0xff
            val rest = i - f * person - (1 - f) * ((old shr shift) and 0xff)
            return (person + f * rest + 0.5f).toInt().coerceIn(0, 255)
        }
        return OPAQUE or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}

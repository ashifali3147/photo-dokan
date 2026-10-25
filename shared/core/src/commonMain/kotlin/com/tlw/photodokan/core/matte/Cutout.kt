package com.tlw.photodokan.core.matte

/**
 * The person in a photo, ready to go over a new background: the [matte] fitted to the photo's own
 * edges, and the colours on either side of those edges. The same whatever the background, so the
 * editor works it out once per photo and only the background is redone as its settings change.
 *
 * @param argb the [width] × [height] photo row by row; kept, not copied, so it mustn't change.
 */
class Cutout(internal val argb: IntArray, val width: Int, val height: Int, matte: Matte) {
    init {
        require(width > 0 && height > 0 && argb.size == width * height) { "Need $width × $height pixels, had ${argb.size}" }
    }

    /** How much of each pixel is the person, 0–255. */
    internal val alpha = MatteEdges.fit(argb, width, height, matte)

    /** The old backdrop and the person's own colours just around each edge, to tell them apart there. */
    internal val oldBackdrop = NearbyColour(argb, width, height, alpha, EDGE * height)
    internal val ownColour = NearbyColour(argb, width, height, alpha, EDGE * height, person = true)

    private companion object {
        /** How far around an edge to look for the colours on either side, as a share of the photo's height. */
        const val EDGE = 0.01
    }
}

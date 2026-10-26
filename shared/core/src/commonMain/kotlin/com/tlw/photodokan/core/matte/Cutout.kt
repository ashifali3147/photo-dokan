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

    /** How much of each pixel is the person, 0–255, fitted to the photo; shared, so not to be changed. */
    val alpha: ByteArray = MatteEdges.fit(argb, width, height, matte)

    /** The old backdrop and the person's own colours just around each edge, to tell them apart there. */
    internal val oldBackdrop = NearbyColour(argb, width, height, alpha, EDGE * height)
    internal val ownColour = NearbyColour(argb, width, height, alpha, EDGE * height, person = true)

    /**
     * For taking the backdrop's light off the person near the edges ([Despill]): the person's
     * colour further in, the backdrop's further out, and how near each point is to the edge.
     * Only a new plain background needs them, so they are worked out when first asked for.
     */
    internal val inside by lazy { NearbyColour(argb, width, height, alpha, SPILL * height, person = true) }
    internal val outside by lazy { NearbyColour(argb, width, height, alpha, SPILL * height) }
    internal val nearEdge by lazy { EdgeNearness(alpha, width, height, SPILL / 2 * height) }

    private companion object {
        /** How far around an edge to look for the colours on either side, as a share of the photo's height. */
        const val EDGE = 0.01

        /** How far in from the edge the backdrop's light reaches the person, as a share of the photo's height. */
        const val SPILL = 0.03
    }
}

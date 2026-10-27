package com.tlw.photodokan.ui.components

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.tlw.photodokan.core.imaging.Shrink
import com.tlw.photodokan.ui.platform.imageBitmapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.ui.theme.StudioTheme
import kotlin.math.max

/**
 * The window behind a screen: warm paper-grey in light, deep ink in dark, with the theme's
 * [ambient][com.tlw.photodokan.ui.theme.StudioColors.ambient] light falling from the top centre.
 */
@Composable
fun StudioBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = StudioTheme.colors
    Box(
        modifier.background(c.base).drawBehind {
            if (c.ambient.alpha > 0f) {
                drawRect(
                    Brush.radialGradient(
                        listOf(c.ambient, Color.Transparent),
                        center = Offset(size.width / 2, 0f),
                        radius = max(size.width, size.height * 0.6f),
                    ),
                )
            }
        },
        content = content,
    )
}

/** A white card with a thin border; the unit every screen is built from. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val c = StudioTheme.colors
    Box(modifier.clip(shape).background(c.surface).border(1.dp, c.line, shape).edgeLight(), content = content)
}

/**
 * Where a photo is judged: a neutral mid grey, the same in both themes so skin and backdrop look
 * the same in both, with a very soft light falling on the middle.
 *
 * The content draws any frosted glass itself (see [rememberFrost]), behind the photo, so nothing
 * here ever lies over the photo being judged; only a lit rim runs round the edge.
 */
@Composable
fun GreyStage(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    colour: Color = StudioTheme.colors.stage,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = StudioTheme.colors
    val grey = colour
    Box(
        modifier
            // At night a faint neutral light around the stage lifts it off the dark; the grey
            // itself never changes, so skin is judged the same in both themes.
            .then(if (c.isDark) Modifier.glow(Color.White.copy(alpha = 0.18f), shape, radius = 20.dp) else Modifier)
            .clip(shape)
            .drawBehind {
                val centre = Offset(size.width / 2, size.height * 0.4f)
                drawRect(
                    Brush.radialGradient(
                        listOf(lerp(grey, Color.White, 0.08f), grey, lerp(grey, Color.Black, 0.12f)),
                        center = centre,
                        radius = max(size.width, size.height) * 0.7f,
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
        // A hairline of light along the stage's edge, as on the rim of a lit table.
        Box(Modifier.matchParentSize().border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.05f))), shape))
    }
}

/**
 * A tiny black-and-white copy of [image] for frosted glass behind it: soft by being so small when
 * stretched back to size, with no colour of its own, so skin is still judged against a neutral
 * surround. Made off the UI thread each time [image] changes; the last one stays until the next.
 */
@Composable
fun rememberFrost(image: ImageBitmap?, backdrop: Color, colour: Boolean = false): ImageBitmap? {
    var frost by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(image, backdrop, colour) {
        if (image != null) frost = withContext(Dispatchers.Default) { frostedCopy(image, backdrop.toArgb(), colour) }
    }
    return frost
}

/** The frosted copy is this many pixels on its longer side: small enough to be soft. */
private const val FROST_SIDE = 40

/**
 * The clear pixels round the frosted copy, and how many of its own pixels fade towards them, so
 * the photo's edge melts into the glass even where the device can't blur. Drawing it, these
 * [FROST_PAD] pixels lie outside the photo on each side.
 */
const val FROST_PAD = 3
private const val FROST_FEATHER = 2.5

private fun frostedCopy(image: ImageBitmap, backdrop: Int, colour: Boolean): ImageBitmap {
    // Where the photo is clear (the person alone, for a plain background) the backdrop shows, as in the editor.
    val pixels = IntArray(image.width * image.height).also { image.readPixels(it) }
    for (i in pixels.indices) pixels[i] = over(pixels[i], backdrop)
    val factor = Shrink.factorFor(image.width, image.height, FROST_SIDE).coerceAtMost(minOf(image.width, image.height))
    val small = Shrink.box(pixels, image.width, image.height, factor)
    val w = small.width + 2 * FROST_PAD
    val h = small.height + 2 * FROST_PAD
    val out = IntArray(w * h)
    for (y in 0 until small.height) {
        for (x in 0 until small.width) {
            val p = small.argb[y * small.width + x]
            // Rec. 709 luma: no hue is left, only light and dark.
            val luma = (0.2126 * ((p shr 16) and 0xff) + 0.7152 * ((p shr 8) and 0xff) + 0.0722 * (p and 0xff)).roundToInt()
            val edge = minOf(minOf(x, small.width - 1 - x), minOf(y, small.height - 1 - y)) + 0.5
            val alpha = (255 * minOf(1.0, edge / FROST_FEATHER)).roundToInt()
            out[(y + FROST_PAD) * w + x + FROST_PAD] = (alpha shl 24) or if (colour) p and 0xffffff else (luma shl 16) or (luma shl 8) or luma
        }
    }
    return imageBitmapOf(out, w, h)
}

/** [top] laid over the opaque [bottom] by its alpha, opaque. */
private fun over(top: Int, bottom: Int): Int {
    val a = top ushr 24
    if (a == 255) return top
    fun mix(shift: Int) = (((top shr shift) and 0xff) * a + ((bottom shr shift) and 0xff) * (255 - a) + 127) / 255
    return (0xff shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
}

/** The glow around the selected photo: a ring that leaves a gap, then a soft halo. */
fun Modifier.selectionGlow(selected: Boolean, shape: Shape = RoundedCornerShape(14.dp)): Modifier {
    if (!selected) return this
    return composed {
        val c = StudioTheme.colors
        this.shadow(16.dp, shape, ambientColor = c.accentGlow, spotColor = c.accentGlow)
            .border(3.dp, c.accent, shape)
    }
}

/**
 * A soft fade into [colour] along the bottom while [state] can scroll further, so a row cut off
 * at the edge reads as "more below" rather than as broken.
 */
fun Modifier.scrollFade(state: ScrollState, colour: Color, height: Dp = 28.dp): Modifier = drawWithContent {
    drawContent()
    if (state.canScrollForward) {
        val h = height.toPx()
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, colour), startY = size.height - h, endY = size.height),
            topLeft = Offset(0f, size.height - h),
            size = Size(size.width, h),
        )
    }
}

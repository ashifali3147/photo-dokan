package com.tlw.photodokan.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.ui.platform.blur
import com.tlw.photodokan.ui.theme.StudioTheme

/** Shrinks a little while it is pressed, so a tap is felt even before anything else changes. */
internal fun Modifier.pressScale(source: MutableInteractionSource): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, tween(PRESS_MILLIS), label = "press")
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

private const val PRESSED_SCALE = 0.97f
private const val PRESS_MILLIS = 120

/** How long things fade or slide in, and the pause between neighbours in a grid. */
object Motion {
    const val ENTER_MILLIS = 350
    const val STAGGER_MILLIS = 35
}

/** Fades and rises into place when first shown, one after another by [index] (the first dozen only). */
fun Modifier.riseIn(index: Int): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(Motion.ENTER_MILLIS, delayMillis = minOf(index, 12) * Motion.STAGGER_MILLIS, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 10.dp.toPx()
    }
}

/** A spring with a little bounce, for things that settle into place: a selected tool, a sliding switch. */
fun <T> settle() = spring<T>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)

/**
 * A soft coloured light spilling out around [shape], [radius] wide at full [amount] (0..1),
 * scaled by the theme's glow strength: fuller at night, quieter by day. It is the shape itself
 * drawn blurred behind the content, so it reads as light on a dark ground, which a shadow can't.
 */
fun Modifier.glow(colour: Color, shape: Shape, radius: Dp = 18.dp, amount: Float = 1f): Modifier = composed {
    val strength = StudioTheme.colors.glowStrength * amount.coerceIn(0f, 1f)
    if (strength <= 0.01f) return@composed this
    val paint = remember { Paint() }
    drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        val r = radius.toPx()
        drawIntoCanvas { canvas ->
            // A bright core close to the edge and a wide soft halo, pooled a little below, as light
            // from a lamp falls: two layers read as a glow where one reads as a blur.
            canvas.save()
            canvas.translate(0f, r * 0.18f)
            paint.color = colour.copy(alpha = (colour.alpha * strength * 0.75f).coerceAtMost(1f))
            paint.blur(r)
            canvas.drawOutline(outline, paint)
            canvas.restore()
            paint.color = colour.copy(alpha = (colour.alpha * strength * 0.9f).coerceAtMost(1f))
            paint.blur(r * 0.35f)
            canvas.drawOutline(outline, paint)
        }
    }
}

/** A hairline of light along the top edge, as on a raised surface lit from above. */
fun Modifier.edgeLight(): Modifier = composed {
    val light = StudioTheme.colors.edgeLight
    drawWithContent {
        drawContent()
        if (light.alpha > 0f) {
            drawLine(
                Brush.horizontalGradient(listOf(Color.Transparent, light, light, Color.Transparent)),
                Offset(size.height * 0.25f, 0.5f),
                Offset(size.width - size.height * 0.25f, 0.5f),
                1.dp.toPx(),
            )
        }
    }
}

/**
 * A band of light that sweeps once across, left to right, whenever [key] changes (and when first
 * shown): it says "this is new" without anything moving for good. Nothing sweeps while [key] is null.
 */
fun Modifier.sheen(key: Any?): Modifier = composed {
    val colour = StudioTheme.colors.sheen
    val sweep = remember { Animatable(1f) }
    LaunchedEffect(key) {
        if (key == null) return@LaunchedEffect
        sweep.snapTo(0f)
        sweep.animateTo(1f, tween(SHEEN_MILLIS, delayMillis = 120, easing = FastOutSlowInEasing))
    }
    drawWithContent {
        drawContent()
        val t = sweep.value
        if (t < 1f) {
            val band = size.width * 0.35f
            val x = -band + (size.width + 2 * band) * t
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, colour, Color.Transparent),
                    start = Offset(x - band / 2, 0f),
                    end = Offset(x + band / 2, size.height),
                ),
            )
        }
    }
}

private const val SHEEN_MILLIS = 900

package com.tlw.photodokan.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.ui.theme.StudioTheme

/**
 * A slider in the studio's look: a thin track, the chosen part in the accent, and a round thumb.
 * With [centred], the track fills from the middle out, for values that go both ways around a
 * neutral zero (brightness, warmth, straighten), so "no change" reads as an empty track rather
 * than half full. Otherwise it fills from the start, for strengths and sizes.
 *
 * With a [trackBrush], the track is a wider strip painted with it instead, showing what each place
 * on the slider gives (every hue, dark to light), and the thumb is filled with [thumbColour].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    centred: Boolean = false,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    trackBrush: Brush? = null,
    thumbColour: Color? = null,
) {
    val c = StudioTheme.colors
    val source = remember { MutableInteractionSource() }
    // Held, the thumb grows a little and its glow comes up, so the finger knows it has hold.
    val dragged by source.collectIsDraggedAsState()
    val pressed by source.collectIsPressedAsState()
    val held by animateFloatAsState(if (dragged || pressed) 1f else 0f, settle(), label = "held")
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        interactionSource = source,
        thumb = {
            Box(
                Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = 1f + 0.22f * held
                        scaleY = 1f + 0.22f * held
                    }
                    .then(if (enabled) Modifier.glow(thumbColour?.copy(alpha = 0.7f) ?: c.accentGlow, CircleShape, radius = 10.dp, amount = 0.5f + 0.5f * held) else Modifier)
                    .background(if (!enabled) c.textMuted else thumbColour ?: c.accent, CircleShape)
                    .border(3.dp, if (c.isDark) c.text else c.surface, CircleShape),
            )
        },
        track = { state ->
            val span = (state.valueRange.endInclusive - state.valueRange.start).takeIf { it > 0f } ?: 1f
            val fraction = ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f)
            val idle = c.textMuted.copy(alpha = 0.28f)
            val active = if (enabled) c.accent else c.textMuted
            Canvas(Modifier.fillMaxWidth().height(22.dp)) {
                val y = size.height / 2
                if (trackBrush != null) {
                    drawLine(trackBrush, Offset(0f, y), Offset(size.width, y), 10.dp.toPx(), StrokeCap.Round)
                    return@Canvas
                }
                val stroke = 4.dp.toPx()
                drawLine(idle, Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
                val from = if (centred) size.width / 2 else 0f
                val to = size.width * fraction
                if (to != from) {
                    if (enabled && c.isDark) {
                        // A soft light under the filled part, so the track itself seems lit.
                        drawLine(c.accent.copy(alpha = 0.22f), Offset(from, y), Offset(to, y), stroke * 3.2f, StrokeCap.Round)
                    }
                    drawLine(
                        if (enabled) Brush.horizontalGradient(listOf(c.accentBottom, c.accent), startX = minOf(from, to), endX = maxOf(from, to)) else SolidColor(active),
                        Offset(from, y), Offset(to, y), stroke, StrokeCap.Round,
                    )
                }
                if (centred) {
                    // The neutral point, so the operator can see where "no change" is.
                    val tick = 7.dp.toPx()
                    drawLine(c.textMuted, Offset(size.width / 2, y - tick), Offset(size.width / 2, y + tick), 1.5.dp.toPx())
                }
            }
        },
    )
}

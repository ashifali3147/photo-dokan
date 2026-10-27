package com.tlw.photodokan.ui.editor

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.ui.components.StudioSlider
import com.tlw.photodokan.ui.components.glow
import com.tlw.photodokan.ui.theme.StudioTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** How strongly coloured a picked colour is: soft, as a studio backdrop is. */
private const val PICK_SATURATION = 0.55

/**
 * Any colour, picked with two sliders: its colour round the wheel and how light it is. Each
 * slider's track shows the colours it runs through, and its thumb the colour picked. Moving them
 * shows live through [onPreview]; letting go calls [onCommit].
 */
@Composable
fun ColourPicker(argb: Long, onPreview: (Long) -> Unit, onCommit: () -> Unit, compact: Boolean = false) {
    val (hue, _, lightness) = hsl(argb)
    val picked = Color(argb.toInt())
    Row(sliderRow(compact), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Colour", Modifier.width(96.dp))
        StudioSlider(
            value = hue.toFloat(),
            onValueChange = { onPreview(fromHsl(it.roundToInt().toDouble(), PICK_SATURATION, lightness)) },
            onValueChangeFinished = onCommit,
            valueRange = 0f..359f,
            modifier = Modifier.weight(1f),
            trackBrush = Brush.horizontalGradient(List(7) { Color(fromHsl(it * 60.0 % 360, PICK_SATURATION, lightness).toInt()) }),
            thumbColour = picked,
        )
    }
    Row(sliderRow(compact), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Light", Modifier.width(96.dp))
        StudioSlider(
            value = lightness.toFloat(),
            onValueChange = { onPreview(fromHsl(hue, PICK_SATURATION, (it * 50).roundToInt() / 50.0)) },
            onValueChangeFinished = onCommit,
            valueRange = LIGHTEST.start..LIGHTEST.endInclusive,
            modifier = Modifier.weight(1f),
            trackBrush = Brush.horizontalGradient(List(5) { Color(fromHsl(hue, PICK_SATURATION, LIGHTEST.start + it * (LIGHTEST.endInclusive - LIGHTEST.start) / 4.0).toInt()) }),
            thumbColour = picked,
        )
    }
}

/** How dark to how light a picked colour can be. */
private val LIGHTEST = 0.1f..0.95f

/**
 * Colours to choose from, shown as the colours themselves: a round sample each, ringed in the
 * accent once chosen, with the chosen one's name underneath so it isn't told by colour alone. The
 * last sample is Custom: [customColour] inside a ring of every hue, which [onCustom] switches to.
 * [compact] (a phone) puts the name at the end of the samples' row instead, to save a line.
 */
@Composable
internal fun ColourChoices(choices: List<Pair<String, Long>>, selected: Long, customColour: Long, onPick: (Long) -> Unit, onCustom: () -> Unit, compact: Boolean = false) {
    val custom = choices.none { it.second == selected }
    val name = choices.firstOrNull { it.second == selected }?.first ?: "Custom"
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        for ((label, argb) in choices) ColourSample(label, Color(argb.toInt()), selected = argb == selected, onClick = { if (argb != selected) onPick(argb) })
        ColourSample("Custom", Color((if (custom) selected else customColour).toInt()), selected = custom, onClick = { if (!custom) onCustom() }, hues = true)
        if (compact) Text(name, style = MaterialTheme.typography.bodyMedium, color = StudioTheme.colors.textMuted, maxLines = 1)
    }
    if (!compact) Text(name, style = MaterialTheme.typography.bodyMedium, color = StudioTheme.colors.textMuted)
}

/** All the hues round a circle, for the Custom sample. */
private val HueRing = Brush.sweepGradient(List(7) { Color.hsl(it * 60f % 360f, 0.75f, 0.55f) })

/** A highlight on one side of a ring, clear elsewhere: turned, it is light running round the ring. */
private val Shine = Brush.sweepGradient(
    0f to Color.Transparent,
    0.62f to Color.Transparent,
    0.8f to Color.White.copy(alpha = 0.95f),
    0.86f to Color.White,
    0.92f to Color.White.copy(alpha = 0.35f),
    1f to Color.Transparent,
)

/**
 * One colour to choose, [SAMPLE] across inside a 44 dp target. Chosen, it has a ring in the accent
 * just outside it; [hues] puts a ring of every hue round the colour instead of a plain edge.
 */
@Composable
private fun ColourSample(name: String, colour: Color, selected: Boolean, onClick: () -> Unit, hues: Boolean = false) {
    val c = StudioTheme.colors
    Box(
        Modifier
            .size(44.dp)
            .then(if (hues) Modifier.huesGlow(colour) else Modifier)
            .clip(CircleShape)
            .border(2.dp, if (selected) c.accent else Color.Transparent, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = name
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        if (hues) {
            HueSample(colour)
        } else {
            Box(Modifier.size(SAMPLE).background(colour, CircleShape).border(1.dp, c.outline, CircleShape))
        }
    }
}

/**
 * The Custom sample: [colour] inside a ring of every hue that turns slowly, with a highlight
 * running round it a little faster, like light catching the edge of a turning glass, so it reads
 * as "make your own" and draws the eye without asking for it.
 */
@Composable
private fun HueSample(colour: Color) {
    val c = StudioTheme.colors
    val motion = rememberInfiniteTransition(label = "hues")
    val turn by motion.animateFloat(0f, 360f, infiniteRepeatable(tween(HUE_TURN_MILLIS, easing = LinearEasing)), label = "turn")
    // The highlight eases round, quick across the top and slow underneath, as a reflection does.
    val shine by motion.animateFloat(0f, 360f, infiniteRepeatable(tween(SHINE_TURN_MILLIS, easing = FastOutSlowInEasing)), label = "shine")
    Canvas(Modifier.size(SAMPLE)) {
        val ring = 4.dp.toPx()
        val radius = size.minDimension / 2
        rotate(turn) { drawCircle(HueRing, radius) }
        drawCircle(colour, radius - ring)
        drawCircle(c.outline, radius - ring, style = Stroke(1.dp.toPx()))
        rotate(shine) { drawCircle(Shine, radius - ring / 2, style = Stroke(ring * 0.9f)) }
        // A small bright point at the head of the highlight, as a glint.
        val angle = (shine - 50f) * PI.toFloat() / 180f
        val glint = Offset(center.x + cos(angle) * (radius - ring / 2), center.y + sin(angle) * (radius - ring / 2))
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), center = glint, radius = ring * 1.6f), ring * 1.6f, glint)
    }
}

/** A soft light round the Custom sample in its own colour, slowly brightening and fading. */
private fun Modifier.huesGlow(colour: Color): Modifier = composed {
    val motion = rememberInfiniteTransition(label = "huesGlow")
    val breath by motion.animateFloat(0.35f, 0.8f, infiniteRepeatable(tween(BREATH_MILLIS, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
    glow(colour.copy(alpha = 0.9f), CircleShape, radius = 12.dp, amount = breath)
}

private const val HUE_TURN_MILLIS = 9_000
private const val SHINE_TURN_MILLIS = 2_600
private const val BREATH_MILLIS = 1_600

private val SAMPLE = 34.dp

/** [argb]'s hue (0–360), saturation and lightness (0–1). */
internal fun hsl(argb: Long): Triple<Double, Double, Double> {
    val r = ((argb shr 16) and 0xff) / 255.0
    val g = ((argb shr 8) and 0xff) / 255.0
    val b = (argb and 0xff) / 255.0
    val top = max(r, max(g, b))
    val bottom = min(r, min(g, b))
    val l = (top + bottom) / 2
    val d = top - bottom
    if (d == 0.0) return Triple(0.0, 0.0, l)
    val s = d / (1 - abs(2 * l - 1))
    val h = when (top) {
        r -> 60 * (((g - b) / d).mod(6.0))
        g -> 60 * ((b - r) / d + 2)
        else -> 60 * ((r - g) / d + 4)
    }
    return Triple(h, s, l)
}

/** The opaque ARGB colour of [hue] (0–360), [saturation] and [lightness] (0–1). */
internal fun fromHsl(hue: Double, saturation: Double, lightness: Double): Long {
    val c = (1 - abs(2 * lightness - 1)) * saturation
    val x = c * (1 - abs((hue / 60).mod(2.0) - 1))
    val m = lightness - c / 2
    val (r, g, b) = when ((hue / 60).toInt().coerceIn(0, 5)) {
        0 -> Triple(c, x, 0.0)
        1 -> Triple(x, c, 0.0)
        2 -> Triple(0.0, c, x)
        3 -> Triple(0.0, x, c)
        4 -> Triple(x, 0.0, c)
        else -> Triple(c, 0.0, x)
    }
    fun channel(v: Double) = ((v + m) * 255).roundToInt().coerceIn(0, 255).toLong()
    return 0xFF000000 or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
}

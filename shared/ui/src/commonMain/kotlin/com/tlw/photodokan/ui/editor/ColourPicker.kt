package com.tlw.photodokan.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.ui.components.StudioSlider
import com.tlw.photodokan.ui.theme.StudioTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** How strongly coloured a picked colour is: soft, as a studio backdrop is. */
private const val PICK_SATURATION = 0.55

/**
 * Any colour, picked with two sliders: its colour round the wheel and how light it is. Moving
 * them shows live through [onPreview]; letting go calls [onCommit].
 */
@Composable
fun ColourPicker(argb: Long, onPreview: (Long) -> Unit, onCommit: () -> Unit) {
    val (hue, _, lightness) = hsl(argb)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Colour", Modifier.width(96.dp))
        StudioSlider(
            value = hue.toFloat(),
            onValueChange = { onPreview(fromHsl(it.roundToInt().toDouble(), PICK_SATURATION, lightness)) },
            onValueChangeFinished = onCommit,
            valueRange = 0f..359f,
            modifier = Modifier.weight(1f),
        )
        Swatch(Color(argb.toInt()))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Light", Modifier.width(96.dp))
        StudioSlider(
            value = lightness.toFloat(),
            onValueChange = { onPreview(fromHsl(hue, PICK_SATURATION, (it * 50).roundToInt() / 50.0)) },
            onValueChangeFinished = onCommit,
            valueRange = 0.1f..0.95f,
            modifier = Modifier.weight(1f),
        )
        // Keeps the two sliders the same length.
        Box(Modifier.size(18.dp))
    }
}

/** A small round sample of [colour]. */
@Composable
internal fun Swatch(colour: Color) =
    Box(Modifier.size(18.dp).background(colour, CircleShape).border(1.dp, StudioTheme.colors.outline, CircleShape))

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

package com.tlw.photodokan.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.model.BackgroundConfig
import com.tlw.photodokan.core.model.BackgroundMode

/** The plain colours a background can be replaced with, as the operator names them. */
private val COLOURS = listOf("White" to BackgroundConfig.WHITE, "Light blue" to BackgroundConfig.LIGHT_BLUE)

/**
 * The background behind the person: as shot, softly blurred (the studio's way) or a plain colour.
 * Choices are one undo step each through [onChange]; the softness slider shows live through
 * [onPreview] and becomes one step on [onCommit].
 */
@Composable
fun BackgroundControls(
    background: BackgroundConfig,
    pending: Boolean,
    onChange: (BackgroundConfig) -> Unit,
    onPreview: (BackgroundConfig) -> Unit,
    onCommit: () -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((mode, label) in listOf(BackgroundMode.BLUR to "Soft blur", BackgroundMode.REPLACE to "Plain colour", BackgroundMode.KEEP to "As shot")) {
            FilterChip(selected = background.mode == mode, onClick = { onChange(background.copy(mode = mode)) }, label = { Text(label) })
        }
    }
    when (background.mode) {
        BackgroundMode.BLUR -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Softness", Modifier.width(96.dp))
            Slider(
                value = background.blurStrength.toFloat(),
                onValueChange = { onPreview(background.copy(blurStrength = (it * 20).toInt() / 20.0)) },
                onValueChangeFinished = onCommit,
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
            )
        }
        BackgroundMode.REPLACE -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((name, argb) in COLOURS) {
                FilterChip(
                    selected = background.replaceColorArgb == argb,
                    onClick = { onChange(background.copy(replaceColorArgb = argb)) },
                    label = { Text(name) },
                    leadingIcon = { Swatch(Color(argb.toInt())) },
                )
            }
        }
        BackgroundMode.KEEP -> Unit
    }
    if (pending) {
        Text("Separating the person from the background…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Swatch(colour: Color) =
    Box(Modifier.size(18.dp).background(colour, CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))

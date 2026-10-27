package com.tlw.photodokan.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.model.BackgroundConfig
import com.tlw.photodokan.core.model.BackgroundMode
import com.tlw.photodokan.core.model.BorderConfig
import com.tlw.photodokan.core.model.BrushMode
import com.tlw.photodokan.ui.components.StudioSlider
import com.tlw.photodokan.ui.theme.StudioTheme
import com.tlw.photodokan.ui.components.QuietButton
import com.tlw.photodokan.ui.components.ChoiceRow
import com.tlw.photodokan.ui.components.ChoiceChip

/** The plain colours a background can be replaced with, as the operator names them. */
private val COLOURS = listOf(
    "White" to BackgroundConfig.WHITE,
    "Light blue" to BackgroundConfig.LIGHT_BLUE,
    "Sky blue" to BackgroundConfig.SKY_BLUE,
    "Light grey" to BackgroundConfig.LIGHT_GREY,
)

/** The brush sizes offered, as shares of the frame's height. */
const val SMALL_BRUSH = 0.02
const val LARGE_BRUSH = 0.12

/**
 * The Backdrop tab, in two parts. Background: behind the person, as shot, softly blurred (the
 * studio's way) or a plain colour, when [canChangeBackground]. Border: the thin line along each
 * photo's edge, its thickness and colour.
 *
 * Choices are one undo step each through [onChange] and [onBorder]; the softness slider and the
 * colour pickers show live through [onPreview] and [onBorderPreview] and become one step on
 * [onCommit]. [customColour] and [customBorderColour] are where the Custom choices start: the
 * last colours picked.
 *
 * Below the background, the touch-up brush for where the cut-out went wrong: [brush] is the one in
 * hand, or null to move the photo as usual, and [brushSize] its size as a share of the frame's height.
 */
@Composable
fun BackgroundControls(
    background: BackgroundConfig,
    pending: Boolean,
    onChange: (BackgroundConfig) -> Unit,
    onPreview: (BackgroundConfig) -> Unit,
    onCommit: () -> Unit,
    customColour: Long = BackgroundConfig.LIGHT_BLUE,
    brush: BrushMode? = null,
    brushSize: Double = SMALL_BRUSH,
    onBrush: (BrushMode?) -> Unit = {},
    onBrushSize: (Double) -> Unit = {},
    onClearTouchUps: () -> Unit = {},
    canChangeBackground: Boolean = true,
    border: BorderConfig = BorderConfig(),
    onBorder: (BorderConfig) -> Unit = {},
    onBorderPreview: (BorderConfig) -> Unit = {},
    customBorderColour: Long = BorderConfig.BLACK,
) {
    if (canChangeBackground) {
        Heading("Background")
        BackgroundChoices(background, pending, onChange, onPreview, onCommit, customColour, brush, brushSize, onBrush, onBrushSize, onClearTouchUps)
    }
    Heading("Border")
    BorderControls(border, onBorder, onBorderPreview, onCommit, customBorderColour)
}

@Composable
private fun BackgroundChoices(
    background: BackgroundConfig,
    pending: Boolean,
    onChange: (BackgroundConfig) -> Unit,
    onPreview: (BackgroundConfig) -> Unit,
    onCommit: () -> Unit,
    customColour: Long,
    brush: BrushMode?,
    brushSize: Double,
    onBrush: (BrushMode?) -> Unit,
    onBrushSize: (Double) -> Unit,
    onClearTouchUps: () -> Unit,
) {
    ChoiceRow {
        for ((mode, label) in listOf(BackgroundMode.BLUR to "Soft blur", BackgroundMode.REPLACE to "Plain colour", BackgroundMode.KEEP to "As shot")) {
            ChoiceChip(label, selected = background.mode == mode, onClick = { onChange(background.copy(mode = mode)) })
        }
    }
    when (background.mode) {
        BackgroundMode.BLUR -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Softness", Modifier.width(96.dp))
            StudioSlider(
                value = background.blurStrength.toFloat(),
                onValueChange = { onPreview(background.copy(blurStrength = (it * 20).toInt() / 20.0)) },
                onValueChangeFinished = onCommit,
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
            )
        }
        BackgroundMode.REPLACE -> {
            val custom = COLOURS.none { it.second == background.replaceColorArgb }
            ChoiceRow {
                for ((name, argb) in COLOURS) {
                    ChoiceChip(
                        name,
                        selected = background.replaceColorArgb == argb,
                        onClick = { onChange(background.copy(replaceColorArgb = argb)) },
                        leading = { Swatch(Color(argb.toInt())) },
                    )
                }
                ChoiceChip(
                    "Custom",
                    selected = custom,
                    onClick = { if (!custom) onChange(background.copy(replaceColorArgb = customColour)) },
                    leading = { Swatch(Color((if (custom) background.replaceColorArgb else customColour).toInt())) },
                )
            }
            if (custom) ColourPicker(background.replaceColorArgb, { onPreview(background.copy(replaceColorArgb = it)) }, onCommit)
        }
        BackgroundMode.KEEP -> Unit
    }
    if (background.mode != BackgroundMode.KEEP && !pending) {
        ChoiceRow {
            Text("Fix the edge", color = StudioTheme.colors.textMuted, style = MaterialTheme.typography.bodyMedium)
            for ((mode, label) in listOf(BrushMode.KEEP to "Keep", BrushMode.ERASE to "Erase")) {
                ChoiceChip(label, selected = brush == mode, onClick = { onBrush(if (brush == mode) null else mode) })
            }
            if (background.touchUps.isNotEmpty()) QuietButton(onClearTouchUps) { Text("Clear fixes") }
        }
        if (brush != null) {
            Text(
                if (brush == BrushMode.KEEP) "Paint over hair or shoulders that were cut off." else "Paint over background that was left behind.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Brush size", Modifier.width(96.dp))
                StudioSlider(
                    value = brushSize.toFloat(),
                    onValueChange = { onBrushSize(it.toDouble()) },
                    valueRange = SMALL_BRUSH.toFloat()..LARGE_BRUSH.toFloat(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    if (pending) {
        Text("Separating the person from the background…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A section's name in the tab, quiet above its choices. */
@Composable
private fun Heading(text: String) =
    Text(text, style = MaterialTheme.typography.labelLarge, color = StudioTheme.colors.textMuted, modifier = Modifier.padding(top = 4.dp))

/** The colours a border comes in, as the operator names them; black is the usual. */
private val BORDER_COLOURS = listOf("Black" to BorderConfig.BLACK, "Grey" to 0xFF808080, "White" to 0xFFFFFFFF)

/** A border's thicknesses, as the operator names them. */
private val BORDER_WIDTHS = listOf("Thin" to BorderConfig.THIN_MM, "Thick" to BorderConfig.THICK_MM)

/**
 * The thin line along each photo's edge: off, thin or thick, then its colour. Choices are one undo
 * step each through [onChange]; the colour picker shows live through [onPreview] until [onCommit].
 */
@Composable
private fun BorderControls(border: BorderConfig, onChange: (BorderConfig) -> Unit, onPreview: (BorderConfig) -> Unit, onCommit: () -> Unit, customColour: Long) {
    ChoiceRow {
        ChoiceChip("Off", selected = !border.show, onClick = { if (border.show) onChange(border.copy(show = false)) })
        for ((name, mm) in BORDER_WIDTHS) {
            ChoiceChip(name, selected = border.show && border.widthMm == mm, onClick = { onChange(border.copy(show = true, widthMm = mm)) })
        }
    }
    if (!border.show) return
    val custom = BORDER_COLOURS.none { it.second == border.colorArgb }
    ChoiceRow {
        for ((name, argb) in BORDER_COLOURS) {
            ChoiceChip(name, selected = border.colorArgb == argb, onClick = { onChange(border.copy(colorArgb = argb)) }, leading = { Swatch(Color(argb.toInt())) })
        }
        ChoiceChip(
            "Custom",
            selected = custom,
            onClick = { if (!custom) onChange(border.copy(colorArgb = customColour)) },
            leading = { Swatch(Color((if (custom) border.colorArgb else customColour).toInt())) },
        )
    }
    if (custom) ColourPicker(border.colorArgb, { onPreview(border.copy(colorArgb = it)) }, onCommit)
}

package com.tlw.photodokan.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.tlw.photodokan.ui.components.SegmentedChoice

/** The plain colours a background can be replaced with, as the operator names them. */
private val COLOURS = listOf(
    "White" to BackgroundConfig.WHITE,
    "Light blue" to BackgroundConfig.LIGHT_BLUE,
    "Sky blue" to BackgroundConfig.SKY_BLUE,
    "Light grey" to BackgroundConfig.LIGHT_GREY,
)

/** The two halves of the Backdrop tab, shown one at a time on a phone. */
enum class BackdropPart { Background, Border }

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
 *
 * With a [part] (a phone, short of height) only that half shows, under a switch between the two
 * through [onPart], and the slider rows are short, so nothing is hidden below a scroll.
 */
@Composable
fun BackgroundControls(
    background: BackgroundConfig,
    pending: Boolean,
    onChange: (BackgroundConfig) -> Unit,
    onPreview: (BackgroundConfig) -> Unit,
    onCommit: () -> Unit,
    customColour: Long = FIRST_CUSTOM_BACKDROP,
    brush: BrushMode? = null,
    brushSize: Double = SMALL_BRUSH,
    onBrush: (BrushMode?) -> Unit = {},
    onBrushSize: (Double) -> Unit = {},
    onClearTouchUps: () -> Unit = {},
    canChangeBackground: Boolean = true,
    border: BorderConfig = BorderConfig(),
    onBorder: (BorderConfig) -> Unit = {},
    onBorderPreview: (BorderConfig) -> Unit = {},
    customBorderColour: Long = FIRST_CUSTOM_BORDER,
    part: BackdropPart? = null,
    onPart: (BackdropPart) -> Unit = {},
) {
    if (part != null) {
        // Without a background to change, only the border is left, so there is nothing to switch.
        val shown = if (canChangeBackground) part else BackdropPart.Border
        if (canChangeBackground) {
            SegmentedChoice(listOf(BackdropPart.Background to "Background", BackdropPart.Border to "Border"), shown, onPart, Modifier.fillMaxWidth())
        }
        when (shown) {
            BackdropPart.Background -> {
                BackgroundChoices(background, onChange, onPreview, onCommit, customColour, compact = true)
                EdgeFix(background, pending, brush, brushSize, onBrush, onBrushSize, onClearTouchUps, compact = true)
            }
            BackdropPart.Border -> BorderControls(border, onBorder, onBorderPreview, onCommit, customBorderColour, compact = true)
        }
        return
    }
    if (canChangeBackground) {
        Heading("Background")
        BackgroundChoices(background, onChange, onPreview, onCommit, customColour)
    }
    Heading("Border")
    BorderControls(border, onBorder, onBorderPreview, onCommit, customBorderColour)
    // Fixing the cut-out is needed now and then, so it comes after the choices made for every photo.
    if (canChangeBackground) EdgeFix(background, pending, brush, brushSize, onBrush, onBrushSize, onClearTouchUps)
}

@Composable
private fun BackgroundChoices(
    background: BackgroundConfig,
    onChange: (BackgroundConfig) -> Unit,
    onPreview: (BackgroundConfig) -> Unit,
    onCommit: () -> Unit,
    customColour: Long,
    compact: Boolean = false,
) {
    ChoiceRow {
        for ((mode, label) in listOf(BackgroundMode.BLUR to "Soft blur", BackgroundMode.REPLACE to "Plain colour", BackgroundMode.KEEP to "As shot")) {
            ChoiceChip(label, selected = background.mode == mode, onClick = { onChange(background.copy(mode = mode)) })
        }
    }
    when (background.mode) {
        BackgroundMode.BLUR -> Row(sliderRow(compact), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
            ColourChoices(COLOURS, background.replaceColorArgb, customColour, { onChange(background.copy(replaceColorArgb = it)) }, { onChange(background.copy(replaceColorArgb = customColour)) }, compact)
            if (COLOURS.none { it.second == background.replaceColorArgb }) {
                ColourPicker(background.replaceColorArgb, { onPreview(background.copy(replaceColorArgb = it)) }, onCommit, compact)
            }
        }
        BackgroundMode.KEEP -> Unit
    }
}

/** The touch-up brush for where the cut-out went wrong, or a note while the person is still being cut out. */
@Composable
private fun EdgeFix(
    background: BackgroundConfig,
    pending: Boolean,
    brush: BrushMode?,
    brushSize: Double,
    onBrush: (BrushMode?) -> Unit,
    onBrushSize: (Double) -> Unit,
    onClearTouchUps: () -> Unit,
    compact: Boolean = false,
) {
    if (background.mode != BackgroundMode.KEEP && !pending) {
        ChoiceRow {
            // On a phone the brush is named for what it is, since "the edge" alone says little.
            Text(if (compact) "Touch-up brush" else "Fix the edge", color = StudioTheme.colors.textMuted, style = MaterialTheme.typography.bodyMedium)
            for ((mode, label) in listOf(BrushMode.KEEP to if (compact) "Restore" else "Keep", BrushMode.ERASE to "Erase")) {
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
            Row(sliderRow(compact), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
        // On a phone the note takes the brush row's height, so the photo doesn't move when it goes.
        Box(if (compact) Modifier.heightIn(min = 44.dp) else Modifier, contentAlignment = Alignment.CenterStart) {
            Text("Separating the person from the background…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
private fun BorderControls(border: BorderConfig, onChange: (BorderConfig) -> Unit, onPreview: (BorderConfig) -> Unit, onCommit: () -> Unit, customColour: Long, compact: Boolean = false) {
    ChoiceRow {
        ChoiceChip("Off", selected = !border.show, onClick = { if (border.show) onChange(border.copy(show = false)) })
        for ((name, mm) in BORDER_WIDTHS) {
            ChoiceChip(name, selected = border.show && border.widthMm == mm, onClick = { onChange(border.copy(show = true, widthMm = mm)) })
        }
    }
    if (!border.show) return
    val custom = BORDER_COLOURS.none { it.second == border.colorArgb }
    ColourChoices(BORDER_COLOURS, border.colorArgb, customColour, { onChange(border.copy(colorArgb = it)) }, { onChange(border.copy(colorArgb = customColour)) }, compact)
    if (custom) ColourPicker(border.colorArgb, { onPreview(border.copy(colorArgb = it)) }, onCommit, compact)
}

package com.tlw.photodokan.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.model.EditRecipe
import com.tlw.photodokan.ui.components.ChoiceChip
import com.tlw.photodokan.ui.components.ChoiceRow
import com.tlw.photodokan.ui.components.QuietButton
import com.tlw.photodokan.ui.components.SegmentedChoice
import com.tlw.photodokan.ui.theme.StudioTheme
import kotlin.math.roundToInt
import com.tlw.photodokan.ui.components.StudioSlider

/** The recipe's light and colour sliders, named the way the operator thinks of them. */
enum class Adjustment(val label: String) {
    Brightness("Brightness"),
    Contrast("Contrast"),
    Warmth("Warmth"),
    Tint("Tint"),
    Colour("Saturation");

    fun valueIn(recipe: EditRecipe): Double = when (this) {
        Brightness -> recipe.exposure
        Contrast -> recipe.contrast
        Warmth -> recipe.warmth
        Tint -> recipe.tint
        Colour -> recipe.saturation
    }

    fun setIn(recipe: EditRecipe, value: Double): EditRecipe {
        val v = value.coerceIn(-1.0, 1.0)
        return when (this) {
            Brightness -> recipe.copy(exposure = v)
            Contrast -> recipe.copy(contrast = v)
            Warmth -> recipe.copy(warmth = v)
            Tint -> recipe.copy(tint = v)
            Colour -> recipe.copy(saturation = v)
        }
    }
}

/** The colour sliders, which do nothing to a black and white photo. */
private val COLOUR_ONLY = setOf(Adjustment.Warmth, Adjustment.Tint, Adjustment.Colour)

/** A slider row's height on a phone, where five of them must fit without scrolling. */
private val COMPACT_ROW = 40.dp

/** A row holding a slider: shorter when [compact]. */
internal fun sliderRow(compact: Boolean): Modifier = if (compact) Modifier.height(COMPACT_ROW) else Modifier

/**
 * The Look tab: one slider per [Adjustment]. Dragging updates the photo live; letting go calls
 * [onCommit], so a whole drag is one undo step. [onAuto], when given, sets the light and colour
 * from the face. Colour or black and white comes first, so it is seen before the sliders.
 *
 * [compact] (a phone) puts Auto and Reset on the same line as Colour or B&W, where they are seen
 * without scrolling, keeps the slider rows short and hides the colour sliders in black and white.
 */
@Composable
fun LookControls(
    recipe: EditRecipe,
    onPreview: (Adjustment, Double) -> Unit,
    onCommit: () -> Unit,
    onReset: () -> Unit,
    onAuto: (() -> Unit)? = null,
    onBlackAndWhite: (Boolean) -> Unit = {},
    compact: Boolean = false,
) {
    if (compact) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            SegmentedChoice(listOf(false to "Colour", true to "B&W"), recipe.blackAndWhite, { if (it != recipe.blackAndWhite) onBlackAndWhite(it) }, Modifier.weight(1f))
            onAuto?.let { QuietButton(onClick = it, padding = 14.dp) { Text("Auto") } }
            QuietButton(onClick = onReset, padding = 14.dp) { Text("Reset") }
        }
    } else {
        ChoiceRow {
            ChoiceChip("Colour", selected = !recipe.blackAndWhite, onClick = { if (recipe.blackAndWhite) onBlackAndWhite(false) })
            ChoiceChip("Black & white", selected = recipe.blackAndWhite, onClick = { if (!recipe.blackAndWhite) onBlackAndWhite(true) })
        }
    }
    for (adjustment in Adjustment.entries) {
        if (compact && recipe.blackAndWhite && adjustment in COLOUR_ONLY) continue
        val value = adjustment.valueIn(recipe)
        Row(sliderRow(compact), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(adjustment.label, Modifier.width(96.dp))
            StudioSlider(
                value = value.toFloat(),
                onValueChange = { onPreview(adjustment, it.toDouble()) },
                onValueChangeFinished = onCommit,
                valueRange = -1f..1f,
                centred = true,
                modifier = Modifier.weight(1f),
            )
            Text(formatAmount(value), Modifier.width(44.dp), style = StudioTheme.type.numberSmall)
        }
    }
    if (!compact) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            onAuto?.let { QuietButton(onClick = it) { Text("Auto") } }
            QuietButton(onClick = onReset) { Text("Reset") }
        }
    }
}

/**
 * The Retouch tab: the studio look's Enhance slider, and Fair skin and Smooth skin when the photo
 * has a face. Letting go of a slider calls [onCommit]. [compact] keeps the rows short on a phone.
 */
@Composable
fun RetouchControls(
    recipe: EditRecipe,
    onCommit: () -> Unit,
    onEnhance: (Double) -> Unit,
    onFairness: ((Double) -> Unit)? = null,
    onRetouch: ((Double) -> Unit)? = null,
    compact: Boolean = false,
) {
    StrengthSlider("Enhance", recipe.enhance, onEnhance, onCommit, compact)
    if (onFairness != null) StrengthSlider("Fair skin", recipe.fairness, onFairness, onCommit, compact)
    if (onRetouch != null) StrengthSlider("Smooth skin", recipe.retouch, onRetouch, onCommit, compact)
}

/** A 0–100 slider, in steps of 5, for a strength 0..1. */
@Composable
private fun StrengthSlider(label: String, value: Double, onChange: (Double) -> Unit, onCommit: () -> Unit, compact: Boolean) {
    Row(sliderRow(compact), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(96.dp))
        StudioSlider(
            value = value.toFloat(),
            onValueChange = { onChange((it * 20).roundToInt() / 20.0) },
            onValueChangeFinished = onCommit,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f),
        )
        Text("${(value * 100).roundToInt()}", Modifier.width(44.dp), style = StudioTheme.type.numberSmall)
    }
}

/** −1..1 shown as −100..+100. */
internal fun formatAmount(value: Double): String {
    val n = (value * 100).roundToInt()
    return when {
        n > 0 -> "+$n"
        n < 0 -> "−${-n}"
        else -> "0"
    }
}

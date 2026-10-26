package com.tlw.photodokan.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.model.EditRecipe
import com.tlw.photodokan.ui.components.QuietButton
import com.tlw.photodokan.ui.theme.StudioTheme
import kotlin.math.roundToInt

/** The recipe's light and colour sliders, named the way the operator thinks of them. */
enum class Adjustment(val label: String) {
    Brightness("Brightness"),
    Contrast("Contrast"),
    Warmth("Warmth"),
    Tint("Tint"),
    Colour("Colour");

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

/**
 * The Look tab: one slider per [Adjustment]. Dragging updates the photo live; letting go calls
 * [onCommit], so a whole drag is one undo step. [onAuto], when given, sets the light and colour
 * from the face.
 */
@Composable
fun LookControls(
    recipe: EditRecipe,
    onPreview: (Adjustment, Double) -> Unit,
    onCommit: () -> Unit,
    onReset: () -> Unit,
    onAuto: (() -> Unit)? = null,
) {
    for (adjustment in Adjustment.entries) {
        val value = adjustment.valueIn(recipe)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(adjustment.label, Modifier.width(96.dp))
            Slider(
                value = value.toFloat(),
                onValueChange = { onPreview(adjustment, it.toDouble()) },
                onValueChangeFinished = onCommit,
                valueRange = -1f..1f,
                modifier = Modifier.weight(1f),
            )
            Text(formatAmount(value), Modifier.width(44.dp), style = StudioTheme.type.numberSmall)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        onAuto?.let { QuietButton(onClick = it) { Text("Auto") } }
        QuietButton(onClick = onReset) { Text("Reset") }
    }
}

/**
 * The Retouch tab: the studio look's Enhance slider, and Fair skin and Smooth skin when the photo
 * has a face. Letting go of a slider calls [onCommit].
 */
@Composable
fun RetouchControls(
    recipe: EditRecipe,
    onCommit: () -> Unit,
    onEnhance: (Double) -> Unit,
    onFairness: ((Double) -> Unit)? = null,
    onRetouch: ((Double) -> Unit)? = null,
) {
    StrengthSlider("Enhance", recipe.enhance, onEnhance, onCommit)
    if (onFairness != null) StrengthSlider("Fair skin", recipe.fairness, onFairness, onCommit)
    if (onRetouch != null) StrengthSlider("Smooth skin", recipe.retouch, onRetouch, onCommit)
}

/** A 0–100 slider, in steps of 5, for a strength 0..1. */
@Composable
private fun StrengthSlider(label: String, value: Double, onChange: (Double) -> Unit, onCommit: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(96.dp))
        Slider(
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

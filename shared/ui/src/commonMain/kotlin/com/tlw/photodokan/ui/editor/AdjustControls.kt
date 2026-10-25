package com.tlw.photodokan.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.model.EditRecipe
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
 * The studio look's Enhance slider, then one slider per [Adjustment]. Dragging updates the photo
 * live through [onEnhance] or [onPreview]; letting go calls [onCommit], so a whole drag is one undo
 * step. [onAuto], when given, sets the light and colour from the face.
 */
@Composable
fun AdjustControls(
    recipe: EditRecipe,
    onPreview: (Adjustment, Double) -> Unit,
    onCommit: () -> Unit,
    onReset: () -> Unit,
    onAuto: (() -> Unit)? = null,
    onEnhance: (Double) -> Unit = {},
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Enhance", Modifier.width(96.dp))
        Slider(
            value = recipe.enhance.toFloat(),
            onValueChange = { onEnhance((it * 20).roundToInt() / 20.0) },
            onValueChangeFinished = onCommit,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f),
        )
        Text("${(recipe.enhance * 100).roundToInt()}", Modifier.width(44.dp))
    }
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
            Text(formatAmount(value), Modifier.width(44.dp))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        onAuto?.let { TextButton(onClick = it) { Text("Auto") } }
        TextButton(onClick = onReset) { Text("Reset light and colour") }
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

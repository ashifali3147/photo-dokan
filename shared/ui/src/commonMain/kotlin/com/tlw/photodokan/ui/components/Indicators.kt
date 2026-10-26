package com.tlw.photodokan.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tlw.photodokan.ui.theme.StudioTheme

/**
 * A small light that says how something stands: Cyanotype for ready, Fixer for done, Caution for
 * a problem, muted for not started. Lit dots [glow]; a muted one should not.
 */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 7.dp, glow: Boolean = true) {
    Canvas(modifier.size(size * 2)) {
        val r = size.toPx() / 2
        if (glow) drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.5f), Color.Transparent), center, r * 2), r * 2)
        drawCircle(color, r)
    }
}

/**
 * One adjustment with its value, e.g. "Fair skin 40". The selected one glows in Cyanotype and
 * drives the slider under it, so only one slider shows at a time.
 */
@Composable
fun ValueChip(
    label: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StudioTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .defaultMinSize(minHeight = 52.dp)
            .then(if (selected) Modifier.shadow(10.dp, shape, ambientColor = c.cyanotype, spotColor = c.cyanotype) else Modifier)
            .clip(shape)
            .background(if (selected) c.cyanotypeWash else c.surfaceHigh)
            .border(1.dp, if (selected) c.cyanotype else Color.Transparent, shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val color = if (selected) c.cyanotype else c.text
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, textAlign = TextAlign.Center, maxLines = 1)
        Text(value, style = StudioTheme.type.number.copy(fontSize = 15.sp), color = color)
    }
}

/** A customer's number as it is printed on the edge of film: ▸0012. */
@Composable
fun FrameNumber(label: String, modifier: Modifier = Modifier, color: Color = StudioTheme.colors.text) {
    Text(
        frameNumber(label),
        modifier.semantics { contentDescription = "Number $label" },
        style = StudioTheme.type.number,
        color = color,
    )
}

/** "0012" → "▸0012". */
fun frameNumber(label: String): String = "▸$label"

/** How many copies: − ×5 +, never below [range]'s first or above its last. */
@Composable
fun CopyStepper(copies: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, range: IntRange = 1..40) {
    val c = StudioTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(modifier.height(52.dp).border(1.dp, c.line, shape), verticalAlignment = Alignment.CenterVertically) {
        StepperButton("−", "Fewer copies", enabled = copies > range.first) { onChange(copies - 1) }
        Text(
            "×$copies",
            Modifier.width(44.dp).semantics { contentDescription = "$copies copies" },
            style = StudioTheme.type.number,
            color = c.text,
            textAlign = TextAlign.Center,
        )
        StepperButton("+", "More copies", enabled = copies < range.last) { onChange(copies + 1) }
    }
}

@Composable
private fun StepperButton(symbol: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = StudioTheme.colors
    Text(
        symbol,
        Modifier
            .size(width = 44.dp, height = 50.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(top = 12.dp),
        style = MaterialTheme.typography.titleLarge,
        color = if (enabled) c.text else c.line,
        textAlign = TextAlign.Center,
    )
}

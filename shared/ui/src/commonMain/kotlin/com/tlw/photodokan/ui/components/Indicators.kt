package com.tlw.photodokan.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.tlw.photodokan.ui.theme.StudioTheme

/**
 * A small light that says how something stands: ok for done or ready, warn for a problem, muted
 * for not started. Lit dots [glow]; a muted one should not.
 */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp, glow: Boolean = true) {
    Canvas(modifier.size(size * 2)) {
        val r = size.toPx() / 2
        if (glow) drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.5f), Color.Transparent), center, r * 2), r * 2)
        drawCircle(color, r)
    }
}

/**
 * One adjustment with its value, e.g. "Fair skin 40". The selected one is washed in the accent and
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
            .defaultMinSize(minHeight = 56.dp)
            .selectionGlow(selected, shape)
            .clip(shape)
            .background(if (selected) c.accentWash else c.surface)
            .border(1.dp, if (selected) c.accent else c.outline, shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val color = if (selected) c.accentText else c.text
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, textAlign = TextAlign.Center, maxLines = 1)
        Text(value, style = StudioTheme.type.numberSmall, color = color)
    }
}

/** A customer's number as it is filed and printed: 0012. */
@Composable
fun CustomerNumber(
    label: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    color: Color = StudioTheme.colors.text,
) {
    Text(
        label,
        modifier.semantics { contentDescription = "Number $label" },
        style = if (large) StudioTheme.type.numberLarge else StudioTheme.type.number,
        color = color,
    )
}

/** How many copies: − 5 +, never below [range]'s first or above its last. */
@Composable
fun CopyStepper(copies: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, range: IntRange = 1..40) {
    val c = StudioTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(modifier.height(56.dp).border(1.dp, c.outline, shape), verticalAlignment = Alignment.CenterVertically) {
        StepperButton("−", "Fewer copies", enabled = copies > range.first) { onChange(copies - 1) }
        Text(
            "$copies",
            Modifier.width(40.dp).semantics { contentDescription = "$copies copies" },
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
            .size(width = 48.dp, height = 54.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(top = 12.dp),
        style = MaterialTheme.typography.titleLarge,
        color = if (enabled) c.text else c.line,
        textAlign = TextAlign.Center,
    )
}

/**
 * The usual copy counts as large buttons; the chosen one is washed in the accent with a glow. A
 * count that isn't one of [choices] (after − or +) shows as one more chosen button, so the number
 * is always on screen exactly once.
 */
@Composable
fun CopyChoices(
    copies: Int,
    choices: List<Int>,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (n in if (copies in choices) choices else (choices + copies).sorted()) {
            val chosen = copies == n
            val c = StudioTheme.colors
            val shape = RoundedCornerShape(12.dp)
            Text(
                "$n",
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .selectionGlow(chosen, shape)
                    .clip(shape)
                    .background(if (chosen) c.accentWash else c.surface)
                    .border(1.dp, if (chosen) c.accent else c.outline, shape)
                    .clickable(role = Role.RadioButton) { onChange(n) }
                    .semantics { this.selected = chosen; contentDescription = "$n copies" }
                    .padding(top = 14.dp),
                style = StudioTheme.type.number,
                color = if (chosen) c.accentText else c.text,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** One tab in a [ToolDock]; a phone, short of height, passes a lower [height]. */
@Composable
fun RowScope.DockTab(label: String, selected: Boolean, onClick: () -> Unit, height: Dp = 60.dp, icon: @Composable () -> Unit) {
    val c = StudioTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .weight(1f)
            .height(height)
            .then(if (selected) Modifier.shadow(10.dp, shape, ambientColor = c.accentGlow, spotColor = c.accentGlow) else Modifier)
            .clip(shape)
            .background(if (selected) (if (c.isDark) c.accentWash else c.surface) else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides if (selected) c.accentText else c.textMuted,
        ) {
            icon()
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** The editor's tools in a recessed track: Crop, Look, Retouch, Backdrop. Any order, one at a time. */
@Composable
fun ToolDock(modifier: Modifier = Modifier, tabs: @Composable RowScope.() -> Unit) {
    val c = StudioTheme.colors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.well).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = tabs,
    )
}

/** A short message after something happened: green with a tick when it went well, warn with "!" when it did not. */
@Composable
fun Notice(text: String, isError: Boolean, modifier: Modifier = Modifier) {
    val c = StudioTheme.colors
    val wash = if (isError) c.warnWash else c.okWash
    val colour = if (isError) c.warn else c.ok
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(wash).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (isError) "!" else "✓", color = colour, style = StudioTheme.type.numberSmall)
        Text(text, color = colour, style = MaterialTheme.typography.bodyMedium)
    }
}

/** One copy fewer or more, beside [CopyChoices], which shows the count. */
@Composable
fun CopyNudge(copies: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, range: IntRange = 1..40) {
    val c = StudioTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(modifier.height(56.dp).border(1.dp, c.outline, shape), verticalAlignment = Alignment.CenterVertically) {
        StepperButton("−", "Fewer copies", enabled = copies > range.first) { onChange(copies - 1) }
        Box(Modifier.width(1.dp).height(28.dp).background(c.line))
        StepperButton("+", "More copies", enabled = copies < range.last) { onChange(copies + 1) }
    }
}

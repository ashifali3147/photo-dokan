package com.tlw.photodokan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.ui.theme.StudioTheme

/**
 * One of a few choices, e.g. a photo size or a backdrop: a white chip with a thin outline, washed
 * in the accent once chosen. [leading] draws before the label, e.g. a colour swatch.
 */
@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val c = StudioTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .defaultMinSize(minHeight = 44.dp)
            .pressScale(source)
            .clip(shape)
            .background(if (selected) c.accentWash else c.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.outline, shape)
            .clickable(interactionSource = source, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) c.accentText else c.text, maxLines = 1)
    }
}

/** A row of chips or buttons that scrolls sideways rather than wrapping, so it costs one line of height. */
@Composable
fun ChoiceRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Two or three ways of seeing the same tool, e.g. Background or Border, in a recessed track like
 * the tool dock. It reads as "which part" rather than as one more choice to make.
 */
@Composable
fun <T> SegmentedChoice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = StudioTheme.colors
    Row(
        modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(c.well).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for ((value, label) in options) {
            val chosen = value == selected
            Text(
                label,
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (chosen) (if (c.isDark) c.accentWash else c.surface) else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(value) }
                    .semantics { this.selected = chosen }
                    .wrapContentHeight(),
                style = MaterialTheme.typography.labelLarge,
                color = if (chosen) c.accentText else c.textMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

package com.tlw.photodokan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
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
    // Choosing lights the chip up over a moment rather than all at once.
    val on by animateFloatAsState(if (selected) 1f else 0f, tween(200), label = "chosen")
    Row(
        modifier
            .defaultMinSize(minHeight = 44.dp)
            .pressScale(source)
            .glow(c.accentGlow, shape, radius = 14.dp, amount = on * 0.7f)
            .clip(shape)
            .background(lerp(c.surface, c.accentWash, on))
            .border((1f + on).dp, lerp(c.outline, c.accent, on), shape)
            .clickable(interactionSource = source, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Text(label, style = MaterialTheme.typography.labelLarge, color = lerp(c.text, c.accentText, on), maxLines = 1)
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
 * the tool dock. It reads as "which part" rather than as one more choice to make. The lit part
 * slides to the one chosen.
 */
@Composable
fun <T> SegmentedChoice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = StudioTheme.colors
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val position by animateFloatAsState(index.toFloat(), settle(), label = "segment")
    val pill = RoundedCornerShape(9.dp)
    BoxWithConstraints(modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(c.well).padding(3.dp)) {
        val gap = 3.dp
        val width = (maxWidth - gap * (options.size - 1)) / options.size
        Box(
            Modifier
                .offset(x = (width + gap) * position)
                .width(width)
                .fillMaxHeight()
                .glow(c.accentGlow, pill, radius = 10.dp, amount = 0.7f)
                .clip(pill)
                .background(if (c.isDark) c.accentWash else c.surface)
                .border(1.dp, c.accent.copy(alpha = if (c.isDark) 0.55f else 0.25f), pill)
                .edgeLight(),
        )
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            options.forEachIndexed { i, (value, label) ->
                val chosen = i == index
                val near = (1f - kotlin.math.abs(position - i)).coerceIn(0f, 1f)
                Text(
                    label,
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(pill)
                        .clickable(role = Role.Tab) { onSelect(value) }
                        .semantics { this.selected = chosen }
                        .wrapContentHeight(),
                    style = MaterialTheme.typography.labelLarge,
                    color = lerp(c.textMuted, c.accentText, near),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

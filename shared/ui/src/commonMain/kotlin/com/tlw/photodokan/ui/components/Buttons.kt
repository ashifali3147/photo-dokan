package com.tlw.photodokan.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.ui.theme.StudioTheme

private val ButtonShape = RoundedCornerShape(14.dp)

/**
 * The one physical action on a screen, shoot or print: a lit safelight. Use at most one per
 * screen, so it is always obvious what to press.
 */
@Composable
fun SafelightButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val c = StudioTheme.colors
    StudioButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        fill = Brush.verticalGradient(listOf(c.safelightTop, c.safelight, c.safelightBottom)),
        contentColor = c.onSafelight,
        highlight = Color.White.copy(alpha = 0.28f),
        glow = c.safelightTop,
        content = content,
    )
}

/** Moves on without printing ("Next", "Add to sheet"): bright, but not the safelight. */
@Composable
fun GoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val c = StudioTheme.colors
    StudioButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        fill = Brush.verticalGradient(listOf(c.goTop, c.goBottom)),
        contentColor = c.onGo,
        highlight = Color.White.copy(alpha = if (c.isDark) 0.8f else 0.15f),
        glow = null,
        content = content,
    )
}

/** Everything else a screen offers: an outline in the colour of the text. */
@Composable
fun QuietButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val c = StudioTheme.colors
    StudioButton(
        onClick = onClick,
        modifier = modifier.border(BorderStroke(1.dp, c.line), ButtonShape),
        enabled = enabled,
        fill = SolidColor(Color.Transparent),
        contentColor = c.text,
        highlight = null,
        glow = null,
        minHeight = 44.dp,
        textStyle = MaterialTheme.typography.labelLarge,
        content = content,
    )
}

/** A round button floating over a photo or the window, e.g. Back; [label] is read out instead of the icon. */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val c = StudioTheme.colors
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(c.glass)
            .border(1.dp, c.line, CircleShape)
            .topHighlight(c.edgeHighlight)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides c.text) { icon() }
    }
}

@Composable
private fun StudioButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    fill: Brush,
    contentColor: Color,
    highlight: Color?,
    glow: Color?,
    minHeight: Dp = 52.dp,
    shape: Shape = ButtonShape,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    content: @Composable RowScope.() -> Unit,
) {
    val c = StudioTheme.colors
    val lit = enabled && glow != null
    Row(
        modifier
            .defaultMinSize(minHeight = minHeight)
            .then(if (lit) Modifier.shadow(14.dp, shape, ambientColor = glow!!, spotColor = glow) else Modifier)
            .clip(shape)
            .background(if (enabled) fill else SolidColor(c.surfaceHigh))
            .then(if (enabled && highlight != null) Modifier.topHighlight(highlight) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (enabled) contentColor else c.textMuted) {
            ProvideTextStyle(textStyle) { content() }
        }
    }
}

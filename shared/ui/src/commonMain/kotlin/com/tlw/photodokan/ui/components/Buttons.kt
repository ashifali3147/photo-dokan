package com.tlw.photodokan.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
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

/** What a [PrimaryButton] says by its colour: the main action (accent) or that something is done (ok). */
enum class ButtonTone { Accent, Ok }

/**
 * The one main action on a screen: Print, Add to sheet, Save. It is lit very slightly from above
 * and has a soft glow, so it is always obvious what to press. Use at most one per screen.
 * [ButtonTone.Ok] turns it green once the action is already done ("On the sheet").
 */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: ButtonTone = ButtonTone.Accent,
    content: @Composable RowScope.() -> Unit,
) {
    val c = StudioTheme.colors
    val top = if (tone == ButtonTone.Ok) c.okTop else c.accentTop
    val bottom = if (tone == ButtonTone.Ok) c.okBottom else c.accentBottom
    StudioButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        fill = Brush.verticalGradient(listOf(top, bottom)),
        contentColor = if (tone == ButtonTone.Ok) c.onOk else c.onAccent,
        glow = if (tone == ButtonTone.Ok) c.okGlow else c.accentGlow,
        content = content,
    )
}

/** Everything else a screen offers: a white button with a thin outline. */
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
        modifier = modifier.border(BorderStroke(1.dp, c.outline), ButtonShape),
        enabled = enabled,
        fill = SolidColor(c.surface),
        contentColor = c.text,
        glow = null,
        minHeight = 48.dp,
        textStyle = MaterialTheme.typography.labelLarge,
        content = content,
    )
}

/** A round button floating over a photo or the camera, e.g. Close; [label] is read out instead of the icon. */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val c = StudioTheme.colors
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(48.dp)
            .pressScale(source)
            .clip(CircleShape)
            .background(c.glass)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
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
    glow: Color?,
    minHeight: Dp = 56.dp,
    shape: Shape = ButtonShape,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    content: @Composable RowScope.() -> Unit,
) {
    val c = StudioTheme.colors
    val source = remember { MutableInteractionSource() }
    val lit = enabled && glow != null
    Row(
        modifier
            .pressScale(source)
            .defaultMinSize(minHeight = minHeight)
            .then(if (lit) Modifier.shadow(14.dp, shape, ambientColor = glow!!, spotColor = glow) else Modifier)
            .clip(shape)
            .background(if (enabled) fill else SolidColor(c.well))
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (enabled) contentColor else c.textMuted) {
            ProvideTextStyle(textStyle) { content() }
        }
    }
}

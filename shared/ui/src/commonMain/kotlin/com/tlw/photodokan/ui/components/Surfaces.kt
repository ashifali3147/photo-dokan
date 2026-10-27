package com.tlw.photodokan.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.ui.theme.StudioTheme
import kotlin.math.max

/**
 * The window behind a screen: warm paper-grey in light, deep ink in dark, with the theme's
 * [ambient][com.tlw.photodokan.ui.theme.StudioColors.ambient] light falling from the top centre.
 */
@Composable
fun StudioBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = StudioTheme.colors
    Box(
        modifier.background(c.base).drawBehind {
            if (c.ambient.alpha > 0f) {
                drawRect(
                    Brush.radialGradient(
                        listOf(c.ambient, Color.Transparent),
                        center = Offset(size.width / 2, 0f),
                        radius = max(size.width, size.height * 0.6f),
                    ),
                )
            }
        },
        content = content,
    )
}

/** A white card with a thin border; the unit every screen is built from. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val c = StudioTheme.colors
    Box(modifier.clip(shape).background(c.surface).border(1.dp, c.line, shape).edgeLight(), content = content)
}

/**
 * Where a photo is judged: a neutral mid grey, the same in both themes so skin and backdrop look
 * the same in both, with a very soft light falling on the middle.
 */
@Composable
fun GreyStage(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val c = StudioTheme.colors
    val grey = c.stage
    Box(
        modifier
            // At night a faint neutral light around the stage lifts it off the dark; the grey
            // itself never changes, so skin is judged the same in both themes.
            .then(if (c.isDark) Modifier.glow(Color.White.copy(alpha = 0.18f), shape, radius = 20.dp) else Modifier)
            .clip(shape)
            .drawBehind {
                val centre = Offset(size.width / 2, size.height * 0.4f)
                drawRect(
                    Brush.radialGradient(
                        listOf(lerp(grey, Color.White, 0.08f), grey, lerp(grey, Color.Black, 0.12f)),
                        center = centre,
                        radius = max(size.width, size.height) * 0.7f,
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
        // A hairline of light along the stage's edge, as on the rim of a lit table.
        Box(Modifier.matchParentSize().border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f))), shape))
    }
}

/** The glow around the selected photo: a ring that leaves a gap, then a soft halo. */
fun Modifier.selectionGlow(selected: Boolean, shape: Shape = RoundedCornerShape(14.dp)): Modifier {
    if (!selected) return this
    return composed {
        val c = StudioTheme.colors
        this.shadow(16.dp, shape, ambientColor = c.accentGlow, spotColor = c.accentGlow)
            .border(3.dp, c.accent, shape)
    }
}

/**
 * A soft fade into [colour] along the bottom while [state] can scroll further, so a row cut off
 * at the edge reads as "more below" rather than as broken.
 */
fun Modifier.scrollFade(state: ScrollState, colour: Color, height: Dp = 28.dp): Modifier = drawWithContent {
    drawContent()
    if (state.canScrollForward) {
        val h = height.toPx()
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, colour), startY = size.height - h, endY = size.height),
            topLeft = Offset(0f, size.height - h),
            size = Size(size.width, h),
        )
    }
}

package com.tlw.photodokan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.ui.theme.StudioTheme
import kotlin.math.max

/** A one-pixel line along the top edge, as if light from above catches it. Draw inside a clip. */
fun Modifier.topHighlight(color: Color): Modifier = drawWithContent {
    drawContent()
    val y = 0.5.dp.toPx()
    drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
}

/**
 * The window behind a screen. In the dark theme a faint safelight spills in from the top, as in a
 * darkroom; in the light theme it is plain paper.
 */
@Composable
fun StudioBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = StudioTheme.colors
    Box(
        modifier
            .background(c.base)
            .then(
                if (c.isDark) Modifier.drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            listOf(c.safelight.copy(alpha = 0.11f), Color.Transparent),
                            center = Offset(size.width / 2, -size.height * 0.08f),
                            radius = max(size.width * 0.5f, size.height * 0.45f),
                        ),
                    )
                } else Modifier,
            ),
        content = content,
    )
}

/** A card or panel, lit from above: a soft top-to-bottom shade, a border, and a highlight on its top edge. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val c = StudioTheme.colors
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(c.panelTop, c.panelBottom)))
            .border(1.dp, c.line, shape)
            .topHighlight(c.edgeHighlight),
        content = content,
    )
}

/**
 * Where a photo is judged: the 18% grey card photographers meter against, the same in both
 * themes so skin and backdrop look the same in both, with a soft light falling on the middle as
 * from a studio softbox.
 */
@Composable
fun GreyCardStage(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val grey = StudioTheme.colors.greyCard
    Box(
        modifier
            .clip(shape)
            .drawBehind {
                val centre = Offset(size.width / 2, size.height * 0.42f)
                val reach = max(size.width, size.height) * 0.7f
                drawRect(
                    Brush.radialGradient(
                        listOf(lerp(grey, Color.White, 0.06f), grey, lerp(grey, Color.Black, 0.14f)),
                        center = centre,
                        radius = reach,
                    ),
                )
                drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.2f), Color.Transparent), center = centre, radius = reach * 0.6f))
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

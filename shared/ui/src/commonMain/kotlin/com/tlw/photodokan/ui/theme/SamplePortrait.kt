package com.tlw.photodokan.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * A stand-in for a customer's photo, for showing a look on (e.g. the photo backgrounds in
 * Settings): a head-and-shoulders silhouette on the studio's blue backdrop, like the guide on a
 * passport form, a little wider than a print. No face, so it reads as a sample and nobody's photo.
 */
internal fun samplePortrait(side: Int = 360): ImageBitmap {
    val image = ImageBitmap(side, side)
    val s = side.toFloat()
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(s, s)) {
        // The backdrop, lighter at the top, with the studio light falling behind the head.
        drawRect(Brush.verticalGradient(listOf(Color(0xFFB4D0EC), Color(0xFF7FA6D2))))
        drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), center = Offset(s * 0.5f, s * 0.34f), radius = s * 0.42f))

        val figure = Path().apply {
            addOval(Rect(s * 0.355f, s * 0.15f, s * 0.645f, s * 0.52f))
            // Neck and shoulders as one shape, falling away to the bottom edge.
            moveTo(s * 0.43f, s * 0.48f)
            cubicTo(s * 0.43f, s * 0.58f, s * 0.4f, s * 0.62f, s * 0.3f, s * 0.645f)
            cubicTo(s * 0.16f, s * 0.68f, s * 0.08f, s * 0.74f, s * 0.06f, s * 0.84f)
            lineTo(s * 0.02f, s)
            lineTo(s * 0.98f, s)
            lineTo(s * 0.94f, s * 0.84f)
            cubicTo(s * 0.92f, s * 0.74f, s * 0.84f, s * 0.68f, s * 0.7f, s * 0.645f)
            cubicTo(s * 0.6f, s * 0.62f, s * 0.57f, s * 0.58f, s * 0.57f, s * 0.48f)
            close()
        }
        drawPath(figure, Brush.verticalGradient(listOf(Color(0xFF3A475A), Color(0xFF232B37)), startY = s * 0.15f, endY = s))
        // A soft light on the upper left of the head and shoulders, so it reads as a figure, not a hole.
        drawPath(figure, Brush.radialGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent), center = Offset(s * 0.4f, s * 0.26f), radius = s * 0.5f))
    }
    return image
}

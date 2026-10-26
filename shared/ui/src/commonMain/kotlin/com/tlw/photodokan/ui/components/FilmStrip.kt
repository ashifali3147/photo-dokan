package com.tlw.photodokan.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.ui.theme.StudioTheme

/**
 * A strip of film for a contact sheet: frames go in [content], between two rows of sprocket
 * holes. Dark in both themes, as film is, so photos look the same on it in either.
 */
@Composable
fun FilmStrip(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = StudioTheme.colors
    Column(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.film)
            .padding(horizontal = 8.dp),
    ) {
        Sprockets()
        content()
        Sprockets()
    }
}

@Composable
private fun Sprockets() {
    val hole = StudioTheme.colors.filmHole
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        val w = 9.dp.toPx()
        val h = 6.dp.toPx()
        val pitch = 18.dp.toPx()
        val count = (size.width / pitch).toInt().coerceAtLeast(1)
        // Spread evenly so the row ends where it starts.
        val gap = (size.width - count * w) / count
        for (i in 0 until count) {
            drawRoundRect(
                hole,
                topLeft = Offset(gap / 2 + i * (w + gap), (size.height - h) / 2),
                size = Size(w, h),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
    }
}

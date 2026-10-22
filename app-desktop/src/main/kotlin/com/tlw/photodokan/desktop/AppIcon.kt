package com.tlw.photodokan.desktop

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter

/** Window and tray icon: a photo sheet with a grid of slots on the studio blue. Drawn, so it is sharp at any size. */
object AppIcon : Painter() {
    private val Blue = Color(0xFF2457A6)
    private const val COLUMNS = 3
    private const val ROWS = 4

    override val intrinsicSize = Size(256f, 256f)

    override fun DrawScope.onDraw() {
        val s = size.minDimension
        drawRoundRect(Blue, size = Size(s, s), cornerRadius = CornerRadius(s * 0.2f))

        val sheet = Size(s * 0.54f, s * 0.7f)
        val sheetTopLeft = Offset((s - sheet.width) / 2, (s - sheet.height) / 2)
        drawRect(Color.White, sheetTopLeft, sheet)

        val pad = s * 0.05f
        val gap = s * 0.035f
        val slot = Size(
            (sheet.width - 2 * pad - (COLUMNS - 1) * gap) / COLUMNS,
            (sheet.height - 2 * pad - (ROWS - 1) * gap) / ROWS,
        )
        for (row in 0 until ROWS) for (column in 0 until COLUMNS) {
            val topLeft = sheetTopLeft + Offset(pad + column * (slot.width + gap), pad + row * (slot.height + gap))
            drawRect(Blue.copy(alpha = if (row < 2) 1f else 0.25f), topLeft, slot)
        }
    }
}

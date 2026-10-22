package com.tlw.photodokan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.tlw.photodokan.core.packing.PackedSheet
import com.tlw.photodokan.core.model.SheetTemplate

private val OrderColors = listOf(
    Color(0xFF2457A6),
    Color(0xFF14808A),
    Color(0xFFB06A00),
    Color(0xFF7B4BB5),
    Color(0xFF3F7D20),
)
private val PaperColor = Color(0xFFFFFFFF)
private val LineColor = Color(0xFFB8C2CF)
private val UsedColor = Color(0xFFD5DAE1)

/** Draws a sheet to scale: printed slots per customer, already-used slots, and free slots. */
@Composable
fun SheetPreview(template: SheetTemplate, sheet: PackedSheet, modifier: Modifier = Modifier) {
    val orderIds = sheet.placements.map { it.orderId }.distinct()
    val bySlot = sheet.placements.associateBy { it.slotIndex }

    Canvas(modifier.aspectRatio((template.paper.width / template.paper.height).toFloat())) {
        val scale = (size.width / template.paper.width).toFloat()
        drawRect(PaperColor)
        drawRect(LineColor, style = Stroke(width = 1.5f))

        for (index in 0 until template.capacity) {
            val r = template.slotRect(index)
            val topLeft = Offset((r.x * scale).toFloat(), (r.y * scale).toFloat())
            val slotSize = Size((r.width * scale).toFloat(), (r.height * scale).toFloat())
            val placement = bySlot[index]
            when {
                placement != null -> {
                    val color = OrderColors[orderIds.indexOf(placement.orderId) % OrderColors.size]
                    drawRect(color.copy(alpha = 0.22f), topLeft, slotSize)
                    drawRect(color, topLeft, slotSize, style = Stroke(width = 1.5f))
                }
                index in sheet.previouslyUsedSlots -> drawRect(UsedColor, topLeft, slotSize)
                else -> drawRect(LineColor, topLeft, slotSize, style = Stroke(width = 1f))
            }
        }
    }
}

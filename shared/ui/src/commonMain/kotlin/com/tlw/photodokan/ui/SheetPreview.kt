package com.tlw.photodokan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.tlw.photodokan.core.packing.PackedSheet
import com.tlw.photodokan.core.model.SheetTemplate
import kotlin.math.roundToInt

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

/** The colour that marks the [index]th order, on the sheet and next to it in lists. */
fun orderColor(index: Int): Color = OrderColors[index.mod(OrderColors.size)]

/**
 * Draws a sheet to scale: printed slots per customer, already-used slots, and free slots.
 * A slot shows its order's photo from [photos] (by order id) when there is one, framed in the
 * order's colour from [colorOf].
 */
@Composable
fun SheetPreview(
    template: SheetTemplate,
    sheet: PackedSheet,
    modifier: Modifier = Modifier,
    photos: Map<String, ImageBitmap> = emptyMap(),
    colorOf: ((orderId: String) -> Color)? = null,
) {
    val orderIds = sheet.placements.map { it.orderId }.distinct()
    val color = colorOf ?: { id -> orderColor(orderIds.indexOf(id)) }
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
                    val c = color(placement.orderId)
                    val photo = photos[placement.orderId]
                    if (photo != null) drawPhoto(photo, topLeft, slotSize) else drawRect(c.copy(alpha = 0.22f), topLeft, slotSize)
                    drawRect(c, topLeft, slotSize, style = Stroke(width = 1.5f))
                }
                index in sheet.previouslyUsedSlots -> drawRect(UsedColor, topLeft, slotSize)
                else -> drawRect(LineColor, topLeft, slotSize, style = Stroke(width = 1f))
            }
        }
    }
}

/** Fills the slot with the photo, trimming its centre if the shapes differ slightly. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPhoto(photo: ImageBitmap, topLeft: Offset, slot: Size) {
    val slotAspect = slot.width / slot.height
    val photoAspect = photo.width.toFloat() / photo.height
    val (srcWidth, srcHeight) = if (photoAspect > slotAspect) {
        (photo.height * slotAspect).roundToInt() to photo.height
    } else {
        photo.width to (photo.width / slotAspect).roundToInt()
    }
    drawImage(
        image = photo,
        srcOffset = IntOffset((photo.width - srcWidth) / 2, (photo.height - srcHeight) / 2),
        srcSize = IntSize(srcWidth.coerceAtLeast(1), srcHeight.coerceAtLeast(1)),
        dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
        dstSize = IntSize(slot.width.roundToInt().coerceAtLeast(1), slot.height.roundToInt().coerceAtLeast(1)),
    )
}

package com.tlw.photodokan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.model.SheetTemplate
import com.tlw.photodokan.core.packing.PackedSheet
import com.tlw.photodokan.ui.theme.StudioTheme
import kotlin.math.roundToInt

/**
 * Draws a sheet to scale from the template's millimetres: white paper, slots already printed in
 * warm grey, free slots as quiet fills, slots left blank as a dashed outline, and each copy to
 * print as its customer's photo from [photos] (by order id), or an accent wash while the photo is
 * still loading. The paper is white in both themes, as the real sheet is.
 *
 * With [onSlotTap], every slot not yet printed can be tapped (to leave it blank, or fill it
 * again) and is read out by [describe] for a screen reader.
 */
@Composable
fun SheetPreview(
    template: SheetTemplate,
    sheet: PackedSheet,
    modifier: Modifier = Modifier,
    photos: Map<String, ImageBitmap> = emptyMap(),
    onSlotTap: ((slot: Int) -> Unit)? = null,
    describe: (slot: Int) -> String = { "Slot ${it + 1}" },
) {
    val c = StudioTheme.colors
    val bySlot = sheet.placements.associateBy { it.slotIndex }
    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))

    BoxWithConstraints(modifier.aspectRatio((template.paper.width / template.paper.height).toFloat())) {
        Canvas(Modifier.fillMaxSize()) {
            val scale = (size.width / template.paper.width).toFloat()
            drawRect(c.paper)

            for (index in 0 until template.capacity) {
                val r = template.slotRect(index)
                val topLeft = Offset((r.x * scale).toFloat(), (r.y * scale).toFloat())
                val slotSize = Size((r.width * scale).toFloat(), (r.height * scale).toFloat())
                val placement = bySlot[index]
                when {
                    placement != null -> {
                        val photo = photos[placement.orderId]
                        if (photo != null) drawPhoto(photo, topLeft, slotSize) else drawRect(c.accentWash, topLeft, slotSize)
                        drawRect(c.accent, topLeft, slotSize, style = Stroke(width = 1.5f))
                    }
                    index in sheet.previouslyUsedSlots -> drawRect(c.slotPrinted, topLeft, slotSize)
                    index in sheet.blankSlots -> {
                        val inset = 1.5f
                        drawRect(
                            c.slotBlank,
                            topLeft + Offset(inset, inset),
                            Size(slotSize.width - 2 * inset, slotSize.height - 2 * inset),
                            style = Stroke(width = 1.5f, pathEffect = dash),
                        )
                    }
                    else -> drawRect(c.slotFree, topLeft, slotSize)
                }
            }
        }

        if (onSlotTap != null) {
            // One tappable box over each slot not yet printed, so taps and screen readers both reach it.
            val scale = maxWidth.value / template.paper.width.toFloat()
            for (index in 0 until template.capacity) {
                if (index in sheet.previouslyUsedSlots) continue
                val r = template.slotRect(index)
                Box(
                    Modifier
                        .offset((r.x * scale).toFloat().dp, (r.y * scale).toFloat().dp)
                        .size((r.width * scale).toFloat().dp, (r.height * scale).toFloat().dp)
                        .clickable(role = Role.Button) { onSlotTap(index) }
                        .semantics { contentDescription = describe(index) },
                )
            }
        }
    }
}

/** Fills the slot with the photo, trimming its centre if the shapes differ slightly. */
private fun DrawScope.drawPhoto(photo: ImageBitmap, topLeft: Offset, slot: Size) {
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

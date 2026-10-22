package com.tlw.photodokan.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.imaging.ColorAdjustment
import com.tlw.photodokan.core.imaging.CropGeometry
import com.tlw.photodokan.core.model.CropConfig
import com.tlw.photodokan.core.model.FaceGuide
import com.tlw.photodokan.core.model.PhotoSizePreset
import kotlin.math.pow
import kotlin.math.roundToInt

/** How one photo-size crop changes, given the photo's size and the frame's aspect. */
typealias CropChange = (imageWidth: Int, imageHeight: Int, aspect: Double, crop: CropConfig) -> CropConfig

/**
 * A fixed frame in the photo size's shape with the photo underneath: drag to move the photo,
 * pinch or scroll to zoom. What shows in the frame is exactly what is saved and printed.
 * Changes show live through [onCropChange]; [onCommit] marks the end of a drag or pinch, so each
 * gesture is one undo step.
 */
@Composable
fun CropEditor(
    image: ImageBitmap,
    preset: PhotoSizePreset,
    crop: CropConfig,
    onCropChange: (CropChange) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
    colorMatrix: FloatArray = ColorAdjustment.IDENTITY,
) {
    val change by rememberUpdatedState(onCropChange)
    val commit by rememberUpdatedState(onCommit)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .aspectRatio(preset.aspect.toFloat())
                .clipToBounds() // the photo extends past the frame; only the framed part shows
                .semantics { contentDescription = "Crop frame" }
                .pointerInput(image) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (pan != Offset.Zero) {
                            change { w, h, a, c -> CropGeometry.pan(w, h, a, c, pan.x.toDouble() / size.width, pan.y.toDouble() / size.height) }
                        }
                        if (zoom != 1f) change { w, h, a, c -> CropGeometry.zoom(w, h, a, c, zoom.toDouble()) }
                    }
                }
                .pointerInput(image) {
                    // When every finger or button is up, the gesture is over.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                        } while (event.changes.any { it.pressed })
                        commit()
                    }
                }
                .pointerInput(image) {
                    // Mouse wheel zoom on the desktop: each notch is its own undo step.
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val scroll = event.changes.first().scrollDelta.y
                                change { w, h, a, c -> CropGeometry.zoom(w, h, a, c, 1.1.pow(-scroll.toDouble())) }
                                commit()
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                },
        ) {
            drawCrop(image, CropGeometry.rect(image.width, image.height, preset.aspect, crop), colorMatrix)
            drawFaceGuide(preset.guide)
        }
    }
}

/** Dashed lines for the top of the head and the chin, and an oval for the face between them. */
private fun DrawScope.drawFaceGuide(guide: FaceGuide) {
    val top = size.height * guide.headTop.toFloat()
    val chin = size.height * guide.chin.toFloat()
    val faceHeight = chin - top
    val faceWidth = faceHeight * 0.72f
    val dashes = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
    for ((color, width) in listOf(Color.Black.copy(alpha = 0.5f) to 3f, Color.White to 1.5f)) {
        drawLine(color, Offset(0f, top), Offset(size.width, top), strokeWidth = width, pathEffect = dashes)
        drawLine(color, Offset(0f, chin), Offset(size.width, chin), strokeWidth = width, pathEffect = dashes)
        drawOval(color, Offset((size.width - faceWidth) / 2, top), Size(faceWidth, faceHeight), style = Stroke(width, pathEffect = dashes))
    }
}

/** Photo size, zoom, straighten and reset, for when dragging and pinching isn't handy. */
@Composable
fun CropControls(
    presets: List<PhotoSizePreset>,
    preset: PhotoSizePreset,
    crop: CropConfig,
    onPreset: (PhotoSizePreset) -> Unit,
    onCropChange: (CropChange) -> Unit,
    onCommit: () -> Unit,
    onReset: () -> Unit,
) {
    fun step(change: CropChange) {
        onCropChange(change)
        onCommit()
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (p in presets) {
            FilterChip(selected = p.id == preset.id, onClick = { onPreset(p) }, label = { Text(p.name) })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Zoom")
        OutlinedButton(onClick = { step { w, h, a, c -> CropGeometry.zoom(w, h, a, c, 1 / 1.2) } }) { Text("−") }
        OutlinedButton(onClick = { step { w, h, a, c -> CropGeometry.zoom(w, h, a, c, 1.2) } }) { Text("+") }
        TextButton(onClick = onReset) { Text("Reset") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Straighten ${formatDegrees(crop.rotationDegrees)}", Modifier.width(120.dp))
        Slider(
            value = crop.rotationDegrees.toFloat(),
            onValueChange = { degrees -> onCropChange { w, h, a, c -> CropGeometry.rotate(w, h, a, c, (degrees * 2).roundToInt() / 2.0) } },
            onValueChangeFinished = onCommit,
            valueRange = -CropGeometry.MAX_ROTATION.toFloat()..CropGeometry.MAX_ROTATION.toFloat(),
            modifier = Modifier.weight(1f),
        )
    }
}

/** To the nearest half degree, with a real minus sign: 2°, −0.5°. */
internal fun formatDegrees(d: Double): String {
    val r = (d * 2).roundToInt() / 2.0
    val text = if (r % 1.0 == 0.0) r.toInt().toString() else r.toString()
    return "$text°".replace('-', '−')
}

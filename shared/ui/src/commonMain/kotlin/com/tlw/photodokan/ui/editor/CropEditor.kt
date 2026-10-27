package com.tlw.photodokan.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.imaging.ColorAdjustment
import com.tlw.photodokan.ui.components.FROST_PAD
import com.tlw.photodokan.core.imaging.CropGeometry
import com.tlw.photodokan.core.imaging.CropRect
import com.tlw.photodokan.core.model.BrushMode
import com.tlw.photodokan.core.model.CropConfig
import com.tlw.photodokan.core.model.FaceGuide
import com.tlw.photodokan.core.model.MaskPoint
import com.tlw.photodokan.core.model.MaskStroke
import com.tlw.photodokan.core.model.PhotoSizePreset
import kotlin.math.pow
import kotlin.math.roundToInt
import com.tlw.photodokan.ui.components.StudioSlider
import com.tlw.photodokan.ui.components.QuietButton
import com.tlw.photodokan.ui.components.ChoiceRow
import com.tlw.photodokan.ui.components.ChoiceChip

/** How one photo-size crop changes, given the photo's size and the frame's aspect. */
typealias CropChange = (imageWidth: Int, imageHeight: Int, aspect: Double, crop: CropConfig) -> CropConfig

/**
 * The touch-up brush the editor paints with: what it does, and its size as a share of the frame's
 * height. Each stroke, finished, goes to [onStroke].
 */
class TouchUpBrush(val mode: BrushMode, val size: Double, val onStroke: (MaskStroke) -> Unit)

/**
 * A fixed frame in the photo size's shape with the photo underneath: drag to move the photo,
 * pinch or scroll to zoom. What shows in the frame is exactly what is saved and printed.
 * Changes show live through [onCropChange]; [onCommit] marks the end of a drag or pinch, so each
 * gesture is one undo step. With a [brush], dragging paints touch-ups on the cut-out instead.
 * [showGuide] draws where the head and chin go; off, the face is seen without lines over it.
 * A [frost] (a tiny black-and-white copy of [image]) is drawn behind the frame exactly where the rest of the photo
 * lies, blurred and veiled in [frostVeil], so the print seems to lie on frosted glass over itself.
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
    backdrop: Color = Color.White,
    border: FrameLine? = null,
    brush: TouchUpBrush? = null,
    showGuide: Boolean = true,
    frost: ImageBitmap? = null,
    frostVeil: Color = Color.Gray,
    frostVeilAlpha: Float = FROST_VEIL,
    glass: Boolean = frost != null,
) {
    val change by rememberUpdatedState(onCropChange)
    val commit by rememberUpdatedState(onCommit)
    val rect = CropGeometry.rect(image.width, image.height, preset.aspect, crop)
    // The stroke being painted, in the frame's pixels, until the finger lifts.
    var painting by remember { mutableStateOf<List<Offset>>(emptyList()) }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (glass) {
            Canvas(Modifier.matchParentSize().blur(FROST_BLUR, BlurredEdgeTreatment.Unbounded)) {
                // Where the frame sits: as large as fits, in the middle, as the Canvas below.
                val aspect = preset.aspect.toFloat()
                val frameWidth = minOf(size.width, size.height * aspect)
                val k = frameWidth / rect.width.toFloat()
                withTransform({
                    translate(size.width / 2, size.height / 2)
                    scale(k, k, pivot = Offset.Zero)
                    rotate(rect.rotationDegrees.toFloat(), pivot = Offset.Zero)
                    translate(-rect.centerX.toFloat(), -rect.centerY.toFloat())
                }) {
                    if (frost == null) return@withTransform
                    // The copy has clear pixels round it (FROST_PAD), which lie just outside the photo.
                    val sx = image.width.toFloat() / (frost.width - 2 * FROST_PAD)
                    val sy = image.height.toFloat() / (frost.height - 2 * FROST_PAD)
                    drawImage(
                        frost,
                        dstOffset = IntOffset((-FROST_PAD * sx).roundToInt(), (-FROST_PAD * sy).roundToInt()),
                        dstSize = IntSize((frost.width * sx).roundToInt(), (frost.height * sy).roundToInt()),
                        filterQuality = FilterQuality.Low,
                    )
                }
                // The veil, well past the edges: the photo's light shows through, but the stage
                // stays near its grey.
                val reach = maxOf(size.width, size.height)
                drawRect(frostVeil.copy(alpha = frostVeilAlpha), topLeft = Offset(-reach, -reach), size = Size(size.width + 2 * reach, size.height + 2 * reach))
                // Light on the glass: a broad sheen from the top left and a little shade at the
                // foot. Behind the frame, so nothing lies over the photo being judged.
                drawRect(
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), start = Offset(-reach / 4, -reach / 4), end = Offset(size.width * 0.6f, size.height * 0.45f)),
                    topLeft = Offset(-reach, -reach), size = Size(size.width + 2 * reach, size.height + 2 * reach),
                )
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.14f)), startY = size.height * 0.65f, endY = size.height + reach / 4),
                    topLeft = Offset(-reach, size.height * 0.65f), size = Size(size.width + 2 * reach, size.height * 0.35f + reach),
                )
            }
        }
        Canvas(
            Modifier
                .aspectRatio(preset.aspect.toFloat())
                // The print lifted a little off the stage, as a photo lies on glass.
                .shadow(14.dp, RectangleShape, clip = false)
                .clipToBounds() // the photo extends past the frame; only the framed part shows
                .semantics { contentDescription = "Crop frame" }
                .then(if (brush != null) Modifier.paintStrokes(brush, image, rect) { painting = it } else Modifier.moveAndZoom(image, { change(it) }, { commit() })),
        ) {
            drawCrop(image, rect, colorMatrix, backdrop, border)
            if (showGuide) drawFaceGuide(preset.guide)
            if (brush != null && painting.isNotEmpty()) drawStroke(painting, brush)
        }
    }
}

/** How soft the frosted glass is where the device can blur, on top of the copy's own softness. */
private val FROST_BLUR = 20.dp

/** How much of the stage's grey lies over the photo behind the glass. */
private const val FROST_VEIL = 0.45f

/** Drag to move the photo, pinch or scroll to zoom; each gesture ends with [commit]. */
private fun Modifier.moveAndZoom(image: ImageBitmap, change: (CropChange) -> Unit, commit: () -> Unit): Modifier =
    this
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
        }

/**
 * Drag to paint with [brush]: [onPainting] follows the stroke so it can be drawn, and when the
 * finger lifts the stroke goes to the brush, in fractions of the upright photo.
 */
private fun Modifier.paintStrokes(brush: TouchUpBrush, image: ImageBitmap, rect: CropRect, onPainting: (List<Offset>) -> Unit): Modifier =
    pointerInput(brush.mode, brush.size, image, rect) {
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            val radius = (brush.size * size.height).toFloat()
            val points = mutableListOf(down.position)
            onPainting(points.toList())
            do {
                val event = awaitPointerEvent()
                for (moved in event.changes) {
                    // A point every third of the brush is enough to follow any stroke.
                    if ((moved.position - points.last()).getDistance() >= radius / 3) {
                        points += moved.position
                        onPainting(points.toList())
                    }
                    moved.consume()
                }
            } while (event.changes.any { it.pressed })
            onPainting(emptyList())
            val photo = points.map { point ->
                val (x, y) = CropGeometry.sourcePoint(rect, size.width.toDouble(), size.height.toDouble(), point.x.toDouble(), point.y.toDouble())
                MaskPoint((x / image.width).coerceIn(0.0, 1.0), (y / image.height).coerceIn(0.0, 1.0))
            }
            brush.onStroke(MaskStroke(brush.mode, brush.size * rect.height / image.height, photo))
        }
    }

/** The stroke being painted, as wide as the brush: light for keeping, dark red for erasing. */
private fun DrawScope.drawStroke(points: List<Offset>, brush: TouchUpBrush) {
    val colour = if (brush.mode == BrushMode.KEEP) Color.White.copy(alpha = 0.45f) else Color(0xFFB3261E).copy(alpha = 0.45f)
    val width = (brush.size * size.height * 2).toFloat()
    if (points.size == 1) {
        drawCircle(colour, width / 2, points[0])
    } else {
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (point in points.drop(1)) lineTo(point.x, point.y)
        }
        drawPath(path, colour, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
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

/**
 * Photo size, zoom, straighten, fit to the face and reset, for when dragging and pinching isn't
 * handy. [compact] (a phone) puts the four buttons side by side across the width, so none is cut off.
 */
@Composable
fun CropControls(
    presets: List<PhotoSizePreset>,
    preset: PhotoSizePreset,
    crop: CropConfig,
    onPreset: (PhotoSizePreset) -> Unit,
    onCropChange: (CropChange) -> Unit,
    onCommit: () -> Unit,
    onReset: () -> Unit,
    onFitFace: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    fun step(change: CropChange) {
        onCropChange(change)
        onCommit()
    }
    ChoiceRow {
        for (p in presets) ChoiceChip(p.name, selected = p.id == preset.id, onClick = { onPreset(p) })
    }
    val actions: @Composable RowScope.() -> Unit = {
        val fill = if (compact) Modifier.weight(1f) else Modifier
        val padding = if (compact) 4.dp else 20.dp
        onFitFace?.let { QuietButton(it, fill, padding = padding) { Text("Fit face", maxLines = 1) } }
        QuietButton({ step { w, h, a, c -> CropGeometry.zoom(w, h, a, c, 1 / 1.2) } }, fill, padding = padding) { Text("Zoom out", maxLines = 1) }
        QuietButton({ step { w, h, a, c -> CropGeometry.zoom(w, h, a, c, 1.2) } }, fill, padding = padding) { Text("Zoom in", maxLines = 1) }
        QuietButton(onReset, fill, padding = padding) { Text("Reset", maxLines = 1) }
    }
    if (compact) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), content = actions) else ChoiceRow(content = actions)
    Row(sliderRow(compact), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Straighten ${formatDegrees(crop.rotationDegrees)}", Modifier.width(120.dp))
        StudioSlider(
            value = crop.rotationDegrees.toFloat(),
            onValueChange = { degrees -> onCropChange { w, h, a, c -> CropGeometry.rotate(w, h, a, c, (degrees * 2).roundToInt() / 2.0) } },
            onValueChangeFinished = onCommit,
            valueRange = -CropGeometry.MAX_ROTATION.toFloat()..CropGeometry.MAX_ROTATION.toFloat(),
            centred = true,
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

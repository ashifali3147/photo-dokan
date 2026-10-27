package com.tlw.photodokan.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.tlw.photodokan.core.imaging.ColorAdjustment
import com.tlw.photodokan.core.imaging.CropRect
import com.tlw.photodokan.core.model.BorderConfig
import kotlin.math.max

/**
 * Draws the [rect] part of [image] to fill this scope, the photo turned clockwise by the crop's
 * rotation and its colours passed through [colorMatrix] (see ColorAdjustment). Where the image is
 * clear (a person cut out for a plain background) the [backdrop] shows, exactly as chosen: the
 * light and colour sliders change the person, not the background. A [border] goes along the edge,
 * over the photo. The editor and [renderCrop] both use it, so what the operator sees is what is
 * saved.
 */
fun DrawScope.drawCrop(
    image: ImageBitmap,
    rect: CropRect,
    colorMatrix: FloatArray = ColorAdjustment.IDENTITY,
    backdrop: Color = Color.White,
    border: FrameLine? = null,
) {
    val filter = if (colorMatrix.contentEquals(ColorAdjustment.IDENTITY)) null else ColorFilter.colorMatrix(ColorMatrix(colorMatrix))
    val k = (size.width / rect.width).toFloat()
    drawRect(backdrop)
    withTransform({
        translate(size.width / 2, size.height / 2)
        scale(k, k, pivot = Offset.Zero)
        rotate(rect.rotationDegrees.toFloat(), pivot = Offset.Zero)
        translate(-rect.centerX.toFloat(), -rect.centerY.toFloat())
    }) {
        drawImage(
            image,
            srcSize = IntSize(image.width, image.height),
            dstSize = IntSize(image.width, image.height),
            colorFilter = filter,
            filterQuality = FilterQuality.High,
        )
    }
    if (border != null) {
        // At least a pixel, so a thin border still shows on a small preview.
        val w = max(1f, (border.share * size.width).toFloat())
        drawRect(border.colour, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), style = Stroke(w))
    }
}

/** A line along a photo's edge: its [colour], and its width as a share of the photo's width. */
class FrameLine(val colour: Color, val share: Double)

/** [border] on a photo [widthMm] wide, or null when it is off. */
fun frameLine(border: BorderConfig, widthMm: Double): FrameLine? =
    if (border.show) FrameLine(Color(border.colorArgb.toInt()), border.widthMm / widthMm) else null

/** Renders a crop into a new [width] × [height] image, the same on every platform. */
fun renderCrop(
    image: ImageBitmap,
    rect: CropRect,
    width: Int,
    height: Int,
    colorMatrix: FloatArray = ColorAdjustment.IDENTITY,
    backdrop: Color = Color.White,
    border: FrameLine? = null,
): ImageBitmap {
    val out = ImageBitmap(width, height)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(out), Size(width.toFloat(), height.toFloat())) {
        drawCrop(image, rect, colorMatrix, backdrop, border)
    }
    return out
}

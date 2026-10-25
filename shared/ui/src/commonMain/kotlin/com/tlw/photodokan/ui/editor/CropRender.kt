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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.tlw.photodokan.core.imaging.ColorAdjustment
import com.tlw.photodokan.core.imaging.CropRect

/**
 * Draws the [rect] part of [image] to fill this scope, the photo turned clockwise by the crop's
 * rotation and its colours passed through [colorMatrix] (see ColorAdjustment). Where the image is
 * clear (a person cut out for a plain background) the [backdrop] shows, exactly as chosen: the
 * light and colour sliders change the person, not the background. The editor and [renderCrop]
 * both use it, so what the operator sees is what is saved.
 */
fun DrawScope.drawCrop(image: ImageBitmap, rect: CropRect, colorMatrix: FloatArray = ColorAdjustment.IDENTITY, backdrop: Color = Color.White) {
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
}

/** Renders a crop into a new [width] × [height] image, the same on every platform. */
fun renderCrop(
    image: ImageBitmap,
    rect: CropRect,
    width: Int,
    height: Int,
    colorMatrix: FloatArray = ColorAdjustment.IDENTITY,
    backdrop: Color = Color.White,
): ImageBitmap {
    val out = ImageBitmap(width, height)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(out), Size(width.toFloat(), height.toFloat())) {
        drawCrop(image, rect, colorMatrix, backdrop)
    }
    return out
}

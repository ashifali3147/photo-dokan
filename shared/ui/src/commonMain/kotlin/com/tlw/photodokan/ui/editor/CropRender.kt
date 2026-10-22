package com.tlw.photodokan.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.tlw.photodokan.core.imaging.CropRect

/**
 * Draws the [rect] part of [image] to fill this scope, the photo turned clockwise by the crop's
 * rotation. The editor and [renderCrop] both use it, so what the operator sees is what is saved.
 */
fun DrawScope.drawCrop(image: ImageBitmap, rect: CropRect) {
    val k = (size.width / rect.width).toFloat()
    drawRect(Color.White)
    withTransform({
        translate(size.width / 2, size.height / 2)
        scale(k, k, pivot = Offset.Zero)
        rotate(rect.rotationDegrees.toFloat(), pivot = Offset.Zero)
        translate(-rect.centerX.toFloat(), -rect.centerY.toFloat())
    }) {
        drawImage(image, srcSize = IntSize(image.width, image.height), dstSize = IntSize(image.width, image.height), filterQuality = FilterQuality.High)
    }
}

/** Renders a crop into a new [width] × [height] image, the same on every platform. */
fun renderCrop(image: ImageBitmap, rect: CropRect, width: Int, height: Int): ImageBitmap {
    val out = ImageBitmap(width, height)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(out), Size(width.toFloat(), height.toFloat())) {
        drawCrop(image, rect)
    }
    return out
}

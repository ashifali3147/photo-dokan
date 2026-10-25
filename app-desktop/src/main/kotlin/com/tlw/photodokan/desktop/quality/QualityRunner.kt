package com.tlw.photodokan.desktop.quality

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import com.tlw.photodokan.core.face.Face
import com.tlw.photodokan.core.face.FaceCrop
import com.tlw.photodokan.core.geometry.mmToPixels
import com.tlw.photodokan.core.imaging.ColorAdjustment
import com.tlw.photodokan.core.imaging.CropGeometry
import com.tlw.photodokan.core.imaging.CropRect
import com.tlw.photodokan.core.matte.Matte
import com.tlw.photodokan.core.model.BackgroundConfig
import com.tlw.photodokan.core.model.BackgroundMode
import com.tlw.photodokan.core.model.EditRecipe
import com.tlw.photodokan.core.model.PhotoSizePreset
import com.tlw.photodokan.core.quality.CutoutStats
import com.tlw.photodokan.core.quality.Findings
import com.tlw.photodokan.core.quality.Problem
import com.tlw.photodokan.core.quality.QualityChecks
import com.tlw.photodokan.ui.editor.backdrop
import com.tlw.photodokan.ui.editor.firstEdit
import com.tlw.photodokan.ui.editor.painted
import com.tlw.photodokan.ui.editor.painting
import com.tlw.photodokan.ui.editor.renderCrop
import com.tlw.photodokan.ui.photo.decodePreview
import com.tlw.photodokan.vision.FaceFinder
import com.tlw.photodokan.vision.PortraitMatter
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.roundToInt

/**
 * One photo after the automatic steps: what was found, the problems that makes, and pictures of
 * each step for the report. [error] says why a photo couldn't be checked.
 */
class PhotoResult(
    val file: File,
    val findings: Findings?,
    val problems: List<Problem>,
    val pictures: Pictures? = null,
    val error: String? = null,
)

/** The photo with the crop drawn on, the cut-out, and the crop as it opens and on white. */
class Pictures(val original: BufferedImage, val cutout: BufferedImage?, val opened: BufferedImage, val onWhite: BufferedImage?)

/**
 * Runs a photo through what the editor does to a new photo ([firstEdit], the cut-out and the
 * background) and checks the result with [QualityChecks]: the same steps, the same models.
 */
class QualityRunner(
    private val faces: FaceFinder,
    private val matter: PortraitMatter,
    private val preset: PhotoSizePreset = PhotoSizePreset.DEFAULTS.first(),
) {
    fun check(file: File): PhotoResult {
        val bytes = try {
            file.readBytes()
        } catch (e: Exception) {
            return PhotoResult(file, null, listOf(Problem.UNREADABLE), error = e.message)
        }
        val started = System.nanoTime()
        val preview = decodePreview(bytes) ?: return PhotoResult(file, null, listOf(Problem.UNREADABLE))
        return try {
            val image = preview.image
            val argb = IntArray(image.width * image.height).also { image.readPixels(it) }
            val found = faces.find(argb, image.width, image.height).sortedByDescending { it.area }
            val first = firstEdit(preview, preset, found)
            val matte = matter.matte(argb, image.width, image.height)
            val opened = render(image, matte, first.recipe)
            val millis = (System.nanoTime() - started) / 1_000_000

            val rect = CropGeometry.rect(image.width, image.height, preset.aspect, first.recipe.crop)
            val face = FaceCrop.main(found)
            val findings = Findings(
                faces = found,
                headFits = first.fit?.fits,
                tiltDegrees = first.fit?.crop?.rotationDegrees,
                cropPixels = first.fit?.let { rect.height * preview.originalHeight / image.height },
                neededPixels = mmToPixels(preset.size.height, PRINT_DPI),
                light = first.light,
                cutout = face?.let { CutoutStats.of(matte, it, image.width, image.height) },
                millis = millis,
            )
            val white = first.recipe.copy(background = BackgroundConfig(mode = BackgroundMode.REPLACE))
            val pictures = Pictures(
                original = thumbnail(image.toAwtImage()).also { drawCrop(it, rect, image.width, found) },
                cutout = thumbnail(matteImage(matte)),
                opened = thumbnail(opened),
                onWhite = thumbnail(render(image, matte, white)),
            )
            PhotoResult(file, findings, QualityChecks.problems(findings), pictures)
        } catch (e: Exception) {
            PhotoResult(file, null, listOf(Problem.FAILED), error = "${e::class.simpleName}: ${e.message}")
        }
    }

    /** The crop of [image] as final.jpg is made: background, then light and colour. */
    private fun render(image: ImageBitmap, matte: Matte, recipe: EditRecipe): BufferedImage {
        val shown = image.painted(matte, recipe.painting)
        val rect = CropGeometry.rect(image.width, image.height, preset.aspect, recipe.crop)
        val height = THUMBNAIL * 2
        val width = (height * preset.aspect).roundToInt()
        return renderCrop(shown, rect, width, height, ColorAdjustment.matrix(recipe), backdrop(recipe.background)).toAwtImage()
    }

    /** The crop's frame, and a box round each face, drawn on a [thumbnail] of a photo [width] wide. */
    private fun drawCrop(thumbnail: BufferedImage, rect: CropRect, width: Int, found: List<Face>) {
        val k = thumbnail.width.toDouble() / width
        val g = thumbnail.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.stroke = BasicStroke(2f)
        g.color = Color(255, 200, 0)
        for (face in found) g.draw(Rectangle2D.Double(face.x * k, face.y * k, face.width * k, face.height * k))
        g.color = Color(0, 200, 255)
        // The frame turns with the crop: drawn upright, then turned about its centre.
        g.transform(AffineTransform.getRotateInstance(-Math.toRadians(rect.rotationDegrees), rect.centerX * k, rect.centerY * k))
        g.draw(Rectangle2D.Double((rect.centerX - rect.width / 2) * k, (rect.centerY - rect.height / 2) * k, rect.width * k, rect.height * k))
        g.dispose()
    }

    private fun matteImage(matte: Matte) = BufferedImage(matte.width, matte.height, BufferedImage.TYPE_BYTE_GRAY).apply {
        raster.setDataElements(0, 0, matte.width, matte.height, matte.alpha)
    }

    /** [image] scaled to the report's picture height. */
    private fun thumbnail(image: BufferedImage): BufferedImage {
        val height = THUMBNAIL * 2 // sharp on high-density screens
        val width = maxOf(1, (image.width * height.toDouble() / image.height).roundToInt())
        return BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).apply {
            createGraphics().apply {
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                drawImage(image, 0, 0, width, height, null)
                dispose()
            }
        }
    }

    companion object {
        /** The report's pictures are this many points high. */
        const val THUMBNAIL = 180
        const val PRINT_DPI = 300.0

        /** The photos in [folder] and its subfolders, in name order, leaving out [skip]. */
        fun photosIn(folder: File, skip: File? = null): List<File> =
            folder.walkTopDown()
                .onEnter { skip == null || it.canonicalFile != skip.canonicalFile }
                .filter { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png") }
                .sortedBy { it.relativeTo(folder).path.lowercase() }
                .toList()
    }
}

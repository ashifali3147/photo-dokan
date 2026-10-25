package com.tlw.photodokan.ui.editor

import com.tlw.photodokan.core.face.Face
import com.tlw.photodokan.core.face.FaceCrop
import com.tlw.photodokan.core.face.FaceFit
import com.tlw.photodokan.core.imaging.AutoLight
import com.tlw.photodokan.core.model.CropConfig
import com.tlw.photodokan.core.model.EditRecipe
import com.tlw.photodokan.core.model.PhotoSizePreset
import com.tlw.photodokan.ui.photo.PhotoPreview

/**
 * How a new photo opens in the editor: cropped to the customer's face on [preset]'s guide and lit
 * from its skin, or whole and as shot without a face. The quality report runs the same, so it
 * judges what the operator sees.
 *
 * @param faces the faces found on [preview], or null when they couldn't be looked for.
 */
fun firstEdit(preview: PhotoPreview, preset: PhotoSizePreset, faces: List<Face>?): FirstEdit {
    val fit = faces?.let { faceFit(preview, preset, it) }
    val light = faces?.let { faceLight(preview, it) }
    val recipe = EditRecipe(presetId = preset.id, crop = fit?.crop ?: CropConfig()).let { light?.applyTo(it) ?: it }
    return FirstEdit(recipe, fit, light)
}

/** A new photo's [recipe], with the crop [fit] and [light] it was made from, if any. */
class FirstEdit(val recipe: EditRecipe, val fit: FaceFit?, val light: AutoLight.Suggestion?)

/** The crop that puts the customer's face among [faces] on [preset]'s guide, or null without one. */
fun faceFit(preview: PhotoPreview, preset: PhotoSizePreset, faces: List<Face>): FaceFit? =
    FaceCrop.main(faces)?.let { FaceCrop.fit(preview.image.width, preview.image.height, preset, it) }

/** Light and colour for the customer's face among [faces], or null without one clear enough to judge. */
fun faceLight(preview: PhotoPreview, faces: List<Face>): AutoLight.Suggestion? {
    val face = FaceCrop.main(faces) ?: return null
    val image = preview.image
    val argb = IntArray(image.width * image.height).also { image.readPixels(it) }
    return AutoLight.suggest(argb, image.width, image.height, face)
}

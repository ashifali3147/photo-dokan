package com.tlw.photodokan.ui.editor

import com.tlw.photodokan.core.face.Face
import com.tlw.photodokan.core.face.FaceCrop
import com.tlw.photodokan.core.face.FaceFit
import com.tlw.photodokan.core.imaging.AutoLight
import com.tlw.photodokan.core.imaging.StudioLook
import com.tlw.photodokan.core.model.CropConfig
import com.tlw.photodokan.core.model.EditRecipe
import com.tlw.photodokan.core.model.PhotoSizePreset
import com.tlw.photodokan.core.model.Portrait
import com.tlw.photodokan.ui.photo.PhotoPreview
import kotlin.math.min

/** How strong the studio look is on a new photo with a face, 0 to 1. */
const val FIRST_ENHANCE = 1.0

/**
 * How a new photo opens in the editor: cropped to the customer's face on [preset]'s guide, its
 * colour balanced from the skin and given the studio look; or whole and as shot without a face.
 * The quality report runs the same, so it judges what the operator sees.
 *
 * @param faces the faces found on [preview], or null when they couldn't be looked for.
 */
fun firstEdit(preview: PhotoPreview, preset: PhotoSizePreset, faces: List<Face>?): FirstEdit {
    val fit = faces?.let { faceFit(preview, preset, it) }
    val light = faces?.let { faceLight(preview, it) }
    val portrait = faces?.let { facePortrait(preview, it) }
    val recipe = studioLight(EditRecipe(presetId = preset.id, crop = fit?.crop ?: CropConfig()), light, portrait)
    return FirstEdit(recipe, fit, light)
}

/**
 * [recipe] with [light]'s colour balance and, with a [portrait], the studio look at full strength.
 * The look's curve lights the face, so then the brightness slider is only used to take down a
 * photo that is too bright.
 */
fun studioLight(recipe: EditRecipe, light: AutoLight.Suggestion?, portrait: Portrait?): EditRecipe {
    val balanced = light?.applyTo(recipe) ?: recipe
    if (portrait == null) return balanced
    return balanced.copy(exposure = min(0.0, balanced.exposure), enhance = FIRST_ENHANCE, portrait = portrait)
}

/** A new photo's [recipe], with the crop [fit] and [light] it was made from, if any. */
class FirstEdit(val recipe: EditRecipe, val fit: FaceFit?, val light: AutoLight.Suggestion?)

/** The crop that puts the customer's face among [faces] on [preset]'s guide, or null without one. */
fun faceFit(preview: PhotoPreview, preset: PhotoSizePreset, faces: List<Face>): FaceFit? =
    FaceCrop.main(faces)?.let { FaceCrop.fit(preview.image.width, preview.image.height, preset, it) }

/** The customer's face among [faces] measured for the studio look, or null without one clear enough. */
fun facePortrait(preview: PhotoPreview, faces: List<Face>): Portrait? {
    val face = FaceCrop.main(faces) ?: return null
    val image = preview.image
    val argb = IntArray(image.width * image.height).also { image.readPixels(it) }
    return StudioLook.measure(argb, image.width, image.height, face)
}

/** Light and colour for the customer's face among [faces], or null without one clear enough to judge. */
fun faceLight(preview: PhotoPreview, faces: List<Face>): AutoLight.Suggestion? {
    val face = FaceCrop.main(faces) ?: return null
    val image = preview.image
    val argb = IntArray(image.width * image.height).also { image.readPixels(it) }
    return AutoLight.suggest(argb, image.width, image.height, face)
}

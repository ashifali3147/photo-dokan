package com.tlw.photodokan.ui.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tlw.photodokan.core.geometry.mmToPixels
import com.tlw.photodokan.core.imaging.CropGeometry
import com.tlw.photodokan.core.library.Customer
import com.tlw.photodokan.core.library.PhotoLibrary
import com.tlw.photodokan.core.library.StudioDate
import com.tlw.photodokan.core.model.CropConfig
import com.tlw.photodokan.core.model.EditRecipe
import com.tlw.photodokan.core.model.PhotoSizePreset
import com.tlw.photodokan.ui.editor.renderCrop
import com.tlw.photodokan.ui.photo.PhotoPreview
import com.tlw.photodokan.ui.photo.PhotoSource
import com.tlw.photodokan.ui.photo.decodePreview
import com.tlw.photodokan.ui.platform.encodeJpeg
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * A photo imported for a new customer, not yet saved. It will be filed as customer [number];
 * name and phone are optional extras the operator rarely needs.
 */
class Draft(
    val fileName: String,
    val original: ByteArray,
    val number: Int,
    val name: String = "",
    val phone: String = "",
) {
    val numberLabel: String get() = number.toString().padStart(4, '0')
}

/** The photo open in the editor: a reduced upright copy of the original and the edits to it. */
data class EditSession(val preview: PhotoPreview, val recipe: EditRecipe, val changed: Boolean = false)

data class CustomersState(
    val today: StudioDate,
    val date: StudioDate = today,
    val query: String = "",
    /** The day's customers, or search results across all days while [query] is not blank. */
    val customers: List<Customer> = emptyList(),
    /** The saved customer open in the editor, or null for a [draft] or nothing. */
    val selected: Customer? = null,
    val draft: Draft? = null,
    val edit: EditSession? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
) {
    val searching: Boolean get() = query.isNotBlank()
}

/**
 * The Customers screen: import a photo, crop it to a photo size, file it as the day's next
 * customer, and find and re-edit customers by day or by number, name or phone. Library and image
 * work runs off the UI thread on [work].
 */
class CustomersViewModel(
    private val library: PhotoLibrary,
    private val clock: () -> StudioDate,
    val presets: List<PhotoSizePreset> = PhotoSizePreset.DEFAULTS,
    private val work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _state = MutableStateFlow(CustomersState(today = clock()))
    val state: StateFlow<CustomersState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun presetOf(recipe: EditRecipe): PhotoSizePreset = presets.firstOrNull { it.id == recipe.presetId } ?: presets.first()

    fun previousDay() = showDay(_state.value.date.previous())

    fun nextDay() {
        val next = _state.value.date.next()
        if (next <= _state.value.today) showDay(next)
    }

    /** Also picks up a new day when the app has been open past midnight. */
    fun showToday() = showDay(clock())

    private fun showDay(date: StudioDate) {
        _state.update { it.copy(today = maxOf(it.today, clock()), date = date, query = "", selected = null, draft = null, edit = null) }
        refresh()
    }

    fun search(query: String) {
        _state.update { it.copy(query = query) }
        refresh()
    }

    /** Opens a saved customer in the editor with their last edit. */
    fun select(customer: Customer) = launchWork {
        val preview = decodePreview(library.readOriginal(customer))
        if (preview == null) {
            error("The photo of customer ${customer.label} could not be opened.")
        } else {
            val recipe = library.readRecipe(customer) ?: EditRecipe(presetId = presets.first().id)
            _state.update { it.copy(selected = customer, draft = null, edit = EditSession(preview, recipe), message = null) }
        }
    }

    fun import(source: PhotoSource) = launchWork {
        val original = source.read()
        val preview = decodePreview(original)
        if (preview == null) {
            error("\"${source.name}\" is not a photo Photo Dokan can open. Choose a JPEG or PNG photo.")
        } else {
            val draft = Draft(source.name, original, number = library.nextNumber(clock()))
            val edit = EditSession(preview, EditRecipe(presetId = presets.first().id))
            _state.update { it.copy(draft = draft, selected = null, edit = edit, message = null) }
        }
    }

    fun editDraft(name: String = _state.value.draft?.name.orEmpty(), phone: String = _state.value.draft?.phone.orEmpty()) {
        _state.update { s -> s.copy(draft = s.draft?.let { Draft(it.fileName, it.original, it.number, name, phone) }) }
    }

    fun discardDraft() = _state.update { it.copy(draft = null, edit = null, message = null) }

    /** Back to the list from a saved customer. Unsaved changes to them are dropped. */
    fun closeCustomer() = _state.update { it.copy(selected = null, edit = null, message = null) }

    /** Changes the crop through [CropGeometry], so it always fits the photo and the photo size. */
    fun updateCrop(change: (imageWidth: Int, imageHeight: Int, aspect: Double, crop: CropConfig) -> CropConfig) = updateEdit { edit ->
        val image = edit.preview.image
        edit.recipe.copy(crop = change(image.width, image.height, presetOf(edit.recipe).aspect, edit.recipe.crop))
    }

    /** Switches photo size: the frame keeps its centre, zoom and angle, then refits. */
    fun setPreset(preset: PhotoSizePreset) = updateEdit { edit ->
        val image = edit.preview.image
        edit.recipe.copy(presetId = preset.id, crop = CropGeometry.clamp(image.width, image.height, preset.aspect, edit.recipe.crop))
    }

    fun resetCrop() = updateEdit { it.recipe.copy(crop = CropConfig()) }

    private fun updateEdit(change: (EditSession) -> EditRecipe) =
        _state.update { s -> s.copy(edit = s.edit?.let { it.copy(recipe = change(it), changed = true) }) }

    /** Files the draft as today's next customer: original, recipe and the cropped final image. */
    fun saveDraft() {
        val draft = _state.value.draft ?: return
        val edit = _state.value.edit ?: return
        val date = clock()
        launchWork {
            val customer = library.create(date, draft.name, draft.phone, draft.original)
            library.saveEdit(customer, edit.recipe, renderFinal(edit))
            _state.update {
                it.copy(
                    today = date, date = date, query = "", draft = null,
                    selected = customer, edit = edit.copy(changed = false),
                    message = "Saved as customer ${customer.label}.", messageIsError = false,
                )
            }
            loadList()
        }
    }

    /** Saves the edit of the open customer. The original is never touched. */
    fun saveChanges() {
        val customer = _state.value.selected ?: return
        val edit = _state.value.edit ?: return
        launchWork {
            library.saveEdit(customer, edit.recipe, renderFinal(edit))
            _state.update { it.copy(edit = edit.copy(changed = false), message = "Saved changes for customer ${customer.label}.", messageIsError = false) }
        }
    }

    /**
     * final.jpg: the crop at 300 dpi of the photo size, rendered from the preview copy. Printing
     * renders again from the full-resolution original.
     */
    private fun renderFinal(edit: EditSession): ByteArray {
        val preset = presetOf(edit.recipe)
        val image = edit.preview.image
        val rect = CropGeometry.rect(image.width, image.height, preset.aspect, edit.recipe.crop)
        val width = mmToPixels(preset.size.width, FINAL_DPI).roundToInt()
        val height = mmToPixels(preset.size.height, FINAL_DPI).roundToInt()
        return encodeJpeg(renderCrop(image, rect, width, height))
    }

    private fun refresh() = launchWork { loadList() }

    private suspend fun loadList() {
        val s = _state.value
        val customers = withContext(work) { if (s.searching) library.search(s.query) else library.customersOn(s.date) }
        _state.update { if (it.query == s.query && it.date == s.date) it.copy(customers = customers) else it }
    }

    private fun error(text: String) = _state.update { it.copy(message = text, messageIsError = true) }

    /** Runs [block] off the UI thread with the busy flag on; failures become a plain message. */
    private fun launchWork(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                withContext(work) { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error("Something went wrong: ${e.message ?: e::class.simpleName}")
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    companion object {
        const val FINAL_DPI = 300.0
    }
}

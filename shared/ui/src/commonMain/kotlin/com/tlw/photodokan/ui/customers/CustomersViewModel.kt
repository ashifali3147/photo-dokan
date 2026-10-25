package com.tlw.photodokan.ui.customers

import androidx.lifecycle.ViewModel
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.viewModelScope
import com.tlw.photodokan.core.face.Face
import com.tlw.photodokan.core.face.FaceCrop
import com.tlw.photodokan.core.face.FaceFit
import com.tlw.photodokan.core.geometry.mmToPixels
import com.tlw.photodokan.core.imaging.ColorAdjustment
import com.tlw.photodokan.core.imaging.CropGeometry
import com.tlw.photodokan.core.library.Customer
import com.tlw.photodokan.core.library.PhotoLibrary
import com.tlw.photodokan.core.library.StudioDate
import com.tlw.photodokan.core.matte.Matte
import com.tlw.photodokan.core.model.BackgroundConfig
import com.tlw.photodokan.core.model.BackgroundMode
import com.tlw.photodokan.core.model.CropConfig
import com.tlw.photodokan.core.model.EditHistory
import com.tlw.photodokan.core.model.EditRecipe
import com.tlw.photodokan.core.model.PhotoSizePreset
import com.tlw.photodokan.core.model.SheetTemplate
import com.tlw.photodokan.ui.editor.Adjustment
import com.tlw.photodokan.ui.editor.renderCrop
import com.tlw.photodokan.ui.editor.BackgroundPainter
import com.tlw.photodokan.ui.photo.PhotoPreview
import com.tlw.photodokan.ui.photo.PhotoSource
import com.tlw.photodokan.ui.photo.decodePreview
import com.tlw.photodokan.ui.print.PrintOrders
import com.tlw.photodokan.ui.platform.encodeJpeg
import com.tlw.photodokan.ui.platform.timed
import com.tlw.photodokan.vision.FaceFinder
import com.tlw.photodokan.vision.MattePng
import com.tlw.photodokan.vision.PortraitMatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

/**
 * The photo open in the editor: a reduced upright copy of the original and its edits. [live] is
 * what the screen shows, including a drag in progress; [history] holds finished steps for undo.
 */
data class EditSession(
    val preview: PhotoPreview,
    val history: EditHistory<EditRecipe>,
    val live: EditRecipe = history.present,
    /** The recipe as last saved; null for a new photo not yet saved. */
    val saved: EditRecipe? = null,
    /** The faces on [preview], once looked for. */
    val faces: List<Face>? = null,
    /** Which part of the photo is the person, once worked out; it takes a moment, so it comes later. */
    val matte: Matte? = null,
    /** [preview] with its background changed as [backgroundFor] says, once worked out. */
    val background: ImageBitmap? = null,
    val backgroundFor: BackgroundConfig? = null,
) {
    val recipe: EditRecipe get() = live

    /**
     * What the editor shows: the photo with the background as [live] says when that is ready,
     * else the last one worked out, else the photo as shot.
     */
    val shown: ImageBitmap
        get() = if (live.background.mode == BackgroundMode.KEEP || matte == null) preview.image else background ?: preview.image

    /** The background as chosen isn't shown yet: the person is still being separated, or it is being redrawn. */
    val backgroundPending: Boolean
        get() = live.background.mode != BackgroundMode.KEEP && (matte == null || backgroundFor != live.background)
    val changed: Boolean get() = live != saved
    val canUndo: Boolean get() = history.canUndo || live != history.present
    val canRedo: Boolean get() = history.canRedo && live == history.present

    companion object {
        fun start(preview: PhotoPreview, recipe: EditRecipe, saved: EditRecipe?, faces: List<Face>? = null) =
            EditSession(preview, EditHistory.start(recipe), saved = saved, faces = faces)
    }
}

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

    /** Leaving the editor now would lose a new photo or changes not saved yet. */
    val hasUnsavedWork: Boolean get() = draft != null || edit?.changed == true
}

/**
 * The Customers screen: import a photo, crop it to a photo size, file it as the day's next
 * customer, find and re-edit customers by day or by number, name or phone, and add them to the
 * print queue. With [faces], a new photo opens already cropped to the face on the photo size's
 * guide; with [matter], each photo's person is separated from the background in the background,
 * once per customer. Library and image work runs off the UI thread on [work].
 */
class CustomersViewModel(
    private val library: PhotoLibrary,
    private val clock: () -> StudioDate,
    private val orders: PrintOrders,
    private val template: SheetTemplate,
    val presets: List<PhotoSizePreset> = PhotoSizePreset.DEFAULTS,
    private val faces: FaceFinder? = null,
    private val matter: PortraitMatter? = null,
    private val work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _state = MutableStateFlow(CustomersState(today = clock()))
    val state: StateFlow<CustomersState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Photos can be cropped to the face automatically. */
    val canFitFace: Boolean get() = faces != null

    /** The background behind the person can be blurred or replaced. */
    val canChangeBackground: Boolean get() = matter != null

    private var backgroundJob: Job? = null

    /** Keeps the open photo's person cut out, so changing the background's settings is quick. */
    private val painter = BackgroundPainter()

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
        val original = timed("Read customer ${customer.label}") { library.readOriginal(customer) }
        val preview = timed("Open photo of ${original.size shr 10} KB") { decodePreview(original) }
        if (preview == null) {
            error("The photo of customer ${customer.label} could not be opened.")
        } else {
            val recipe = library.readRecipe(customer) ?: EditRecipe(presetId = presets.first().id)
            _state.update { it.copy(selected = customer, draft = null, edit = EditSession.start(preview, recipe, saved = recipe), message = null) }
            findMatte(preview, customer)
        }
    }

    fun import(source: PhotoSource) = launchWork {
        val original = try {
            source.read()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // E.g. a share whose app took back its permission, or a file on a removed card.
            return@launchWork error("Could not open \"${source.name}\". Choose it with Choose photo instead.")
        }
        val preview = timed("Open photo of ${original.size shr 10} KB") { decodePreview(original) }
        if (preview == null) {
            error("\"${source.name}\" is not a photo Photo Dokan can open. Choose a JPEG or PNG photo.")
        } else {
            val draft = Draft(source.name, original, number = library.nextNumber(clock()))
            val preset = presets.first()
            val found = findFaces(preview)
            val fit = found?.let { fitFace(preview, preset, it) }
            val recipe = EditRecipe(presetId = preset.id, crop = fit?.crop ?: CropConfig())
            val edit = EditSession.start(preview, recipe, saved = null, faces = found)
            _state.update { it.copy(draft = draft, selected = null, edit = edit, message = found?.let { faceMessage(fit) }, messageIsError = false) }
            findMatte(preview, customer = null)
        }
    }

    fun editDraft(name: String = _state.value.draft?.name.orEmpty(), phone: String = _state.value.draft?.phone.orEmpty()) {
        _state.update { s -> s.copy(draft = s.draft?.let { Draft(it.fileName, it.original, it.number, name, phone) }) }
    }

    fun discardDraft() = _state.update { it.copy(draft = null, edit = null, message = null) }

    /** Back to the list from a saved customer. Unsaved changes to them are dropped. */
    fun closeCustomer() = _state.update { it.copy(selected = null, edit = null, message = null) }

    /** Closes whatever the editor shows, a new photo or a saved customer, dropping unsaved work. */
    fun closeEditor() = if (_state.value.draft != null) discardDraft() else closeCustomer()

    /**
     * Changes the crop through [CropGeometry], so it always fits the photo and the photo size.
     * Shows live; call [commit] when the drag or slider is let go.
     */
    fun updateCrop(change: (imageWidth: Int, imageHeight: Int, aspect: Double, crop: CropConfig) -> CropConfig) = preview { edit ->
        val image = edit.preview.image
        edit.live.copy(crop = change(image.width, image.height, presetOf(edit.live).aspect, edit.live.crop))
    }

    /** Shows a light or colour change live; call [commit] when the slider is let go. */
    fun adjust(adjustment: Adjustment, value: Double) = preview { adjustment.setIn(it.live, value) }

    /**
     * Switches photo size: a crop fitted to the face is fitted again for the new size's guide;
     * any other keeps its centre, zoom and angle, then refits.
     */
    fun setPreset(preset: PhotoSizePreset) = step { edit ->
        val image = edit.preview.image
        val faces = edit.faces
        val fittedBefore = faces != null && fitFace(edit.preview, presetOf(edit.live), faces)?.crop == edit.live.crop
        val crop = (if (fittedBefore) fitFace(edit.preview, preset, faces!!)?.crop else null)
            ?: CropGeometry.clamp(image.width, image.height, preset.aspect, edit.live.crop)
        edit.live.copy(presetId = preset.id, crop = crop)
    }

    /** Crops to the face on the photo size's guide, with the eyes level, as one undo step. */
    fun fitFace() {
        val edit = _state.value.edit ?: return
        if (faces == null) return
        launchWork {
            val found = edit.faces ?: findFaces(edit.preview).orEmpty()
            val fit = fitFace(edit.preview, presetOf(edit.live), found)
            _state.update { s -> s.copy(edit = s.edit?.copy(faces = found), message = faceMessage(fit), messageIsError = false) }
            if (fit != null) step { it.live.copy(crop = fit.crop) }
        }
    }

    /**
     * Separates the person in [preview] from the background without holding up the editor: the
     * saved [customer]'s kept matte, or worked out and kept. A new photo's matte is kept when it
     * is saved. Without a matter, or when it fails, there is simply no matte.
     */
    private fun findMatte(preview: PhotoPreview, customer: Customer?) {
        val matter = matter ?: return
        viewModelScope.launch(work) {
            val kept = customer?.let { runCatching { library.readMatte(it)?.let(MattePng::decode) }.getOrNull() }
            val matte = kept ?: try {
                val image = preview.image
                val argb = IntArray(image.width * image.height).also { image.readPixels(it) }
                timed("Separate the person") { matter.matte(argb, image.width, image.height) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch
            }
            var owner: Customer? = null
            _state.update { s ->
                val edit = s.edit
                if (edit == null || edit.preview !== preview) return@update s // another photo is open now
                owner = s.selected
                s.copy(edit = edit.copy(matte = matte))
            }
            redrawBackground()
            // A new photo saved meanwhile is a customer now.
            if (kept == null) owner?.let { saveMatte(it, matte) }
        }
    }

    /**
     * The matte to save with [edit]: the one worked out already (perhaps while saving began), or,
     * when the background needs it and it isn't ready, worked out now.
     */
    private fun matteForSaving(edit: EditSession): Matte? {
        edit.matte?.let { return it }
        _state.value.edit?.takeIf { it.preview === edit.preview }?.matte?.let { return it }
        if (edit.live.background.mode == BackgroundMode.KEEP) return null
        val matter = matter ?: return null
        return runCatching {
            val image = edit.preview.image
            val argb = IntArray(image.width * image.height).also { image.readPixels(it) }
            timed("Separate the person") { matter.matte(argb, image.width, image.height) }
        }.getOrNull()
    }

    private fun saveMatte(customer: Customer, matte: Matte) {
        runCatching { library.saveMatte(customer, MattePng.encode(matte)) } // worked out again next time
    }

    /** The faces on the photo, or null without a face finder or when it fails. */
    private fun findFaces(preview: PhotoPreview): List<Face>? {
        val finder = faces ?: return null
        val image = preview.image
        return try {
            val argb = IntArray(image.width * image.height).also { image.readPixels(it) }
            timed("Find faces") { finder.find(argb, image.width, image.height) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // cropping by hand still works
        }
    }

    private fun fitFace(preview: PhotoPreview, preset: PhotoSizePreset, faces: List<Face>): FaceFit? =
        FaceCrop.main(faces)?.let { FaceCrop.fit(preview.image.width, preview.image.height, preset, it) }

    /** What the operator should know about the automatic crop; nothing when it simply worked. */
    private fun faceMessage(fit: FaceFit?): String? = when {
        fit == null -> "No face found. Move and zoom the photo to fit the guide."
        !fit.fits -> "The head is too close to the edge of the photo to fit the guide. Check the crop, or take the photo with more space around the head."
        else -> null
    }

    fun resetCrop() = step { it.live.copy(crop = CropConfig()) }

    fun resetAdjustments() = step { it.live.copy(exposure = 0.0, contrast = 0.0, warmth = 0.0, saturation = 0.0) }

    /** Shows a background change live, e.g. while the softness slider moves; call [commit] when let go. */
    fun previewBackground(background: BackgroundConfig) = preview { it.live.copy(background = background) }

    /** Changes the background as one undo step: keep, blur, or a plain colour. */
    fun setBackground(background: BackgroundConfig) = step { it.live.copy(background = background) }

    /** Ends a live change: it becomes one undo step. */
    fun commit() = _state.update { s -> s.copy(edit = s.edit?.let { it.copy(history = it.history.apply(it.live)) }) }

    /** Drops a change still being dragged, or steps back one finished change. */
    fun undo() {
        _state.update { s ->
            s.copy(edit = s.edit?.let {
                if (it.live != it.history.present) it.copy(live = it.history.present)
                else it.history.undo().let { h -> it.copy(history = h, live = h.present) }
            })
        }
        redrawBackground()
    }

    fun redo() {
        _state.update { s ->
            s.copy(edit = s.edit?.let { if (it.canRedo) it.history.redo().let { h -> it.copy(history = h, live = h.present) } else it })
        }
        redrawBackground()
    }

    /**
     * Queues the open customer for [copies] prints, or changes their copies if already queued.
     * The sheet prints what was saved, so unsaved changes must be saved first, and only photos
     * cropped to the sheet's slot size fit its slots.
     */
    fun addToPrint(copies: Int) {
        val customer = _state.value.selected ?: return
        val edit = _state.value.edit ?: return
        val preset = presetOf(edit.live)
        when {
            edit.changed -> error("Save the changes first, so the sheet prints the photo as it looks now.")
            preset.size != template.photo -> error(
                "Customer ${customer.label} is cropped for ${preset.name}, but the sheet prints ${sizeOf(template)} photos. " +
                    "Choose that size, save, then add to print.",
            )
            else -> {
                orders.update { it.add(customer, copies) }
                _state.update { it.copy(message = "Customer ${customer.label}: ${copiesText(copies)} in the print queue.", messageIsError = false) }
            }
        }
    }

    fun removeFromPrint() {
        val customer = _state.value.selected ?: return
        orders.update { it.remove(customer) }
        _state.update { it.copy(message = "Customer ${customer.label} taken out of the print queue.", messageIsError = false) }
    }

    private fun preview(change: (EditSession) -> EditRecipe) {
        _state.update { s -> s.copy(edit = s.edit?.let { it.copy(live = change(it)) }) }
        redrawBackground()
    }

    /**
     * Works out the photo with the background the open edit asks for, off the UI thread, unless
     * it is already shown. A newer change replaces one still being worked out.
     */
    private fun redrawBackground() {
        val edit = _state.value.edit ?: return
        val matte = edit.matte ?: return
        val wanted = edit.live.background
        if (wanted.mode == BackgroundMode.KEEP || wanted == edit.backgroundFor) return
        backgroundJob?.cancel()
        backgroundJob = viewModelScope.launch(work) {
            val image = try {
                painter.paint(edit.preview.image, matte, wanted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch // the photo shows as shot
            }
            _state.update { s ->
                val now = s.edit
                if (now == null || now.preview !== edit.preview) s else s.copy(edit = now.copy(background = image, backgroundFor = wanted))
            }
        }
    }

    private fun step(change: (EditSession) -> EditRecipe) {
        preview(change)
        commit()
    }

    /** Files the draft as today's next customer: original, recipe and the cropped final image. */
    fun saveDraft() {
        val draft = _state.value.draft ?: return
        commit()
        val edit = _state.value.edit ?: return
        val date = clock()
        launchWork {
            val customer = library.create(date, draft.name, draft.phone, draft.original)
            val matte = matteForSaving(edit)
            library.saveEdit(customer, edit.recipe, renderFinal(edit, matte))
            matte?.let { saveMatte(customer, it) }
            _state.update { s ->
                s.copy(
                    today = date, date = date, query = "", draft = null,
                    selected = customer, edit = s.edit?.let { it.copy(saved = edit.live, matte = it.matte ?: matte) },
                    message = "Saved as customer ${customer.label}.", messageIsError = false,
                )
            }
            loadList()
        }
    }

    /** Saves the edit of the open customer. The original is never touched. */
    fun saveChanges() {
        val customer = _state.value.selected ?: return
        commit()
        val edit = _state.value.edit ?: return
        launchWork {
            val matte = matteForSaving(edit)
            library.saveEdit(customer, edit.live, renderFinal(edit, matte))
            if (edit.matte == null) matte?.let { saveMatte(customer, it) }
            _state.update { s ->
                s.copy(
                    edit = s.edit?.let { it.copy(saved = edit.live, matte = it.matte ?: matte) },
                    message = "Saved changes for customer ${customer.label}.", messageIsError = false,
                )
            }
        }
    }

    /**
     * final.jpg: the crop at 300 dpi of the photo size, rendered from the preview copy with its
     * background as the editor shows it. Printing renders again from the full-resolution original.
     */
    private fun renderFinal(edit: EditSession, matte: Matte?): ByteArray {
        val preset = presetOf(edit.live)
        val wanted = edit.live.background
        val image = when {
            wanted.mode == BackgroundMode.KEEP -> edit.preview.image
            edit.backgroundFor == wanted && edit.background != null -> edit.background
            else -> painter.paint(edit.preview.image, matte, wanted)
        }
        val rect = CropGeometry.rect(image.width, image.height, preset.aspect, edit.live.crop)
        val width = mmToPixels(preset.size.width, FINAL_DPI).roundToInt()
        val height = mmToPixels(preset.size.height, FINAL_DPI).roundToInt()
        return timed("Make final.jpg") { encodeJpeg(renderCrop(image, rect, width, height, ColorAdjustment.matrix(edit.live))) }
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

        private fun copiesText(n: Int) = if (n == 1) "1 copy" else "$n copies"

        private fun sizeOf(t: SheetTemplate) = "${t.photo.width.fmt()} × ${t.photo.height.fmt()} mm"

        private fun Double.fmt() = if (this % 1.0 == 0.0) toInt().toString() else toString()
    }
}

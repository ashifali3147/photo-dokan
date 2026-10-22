package com.tlw.photodokan.ui.customers

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tlw.photodokan.core.library.Customer
import com.tlw.photodokan.core.library.PhotoLibrary
import com.tlw.photodokan.core.library.StudioDate
import com.tlw.photodokan.core.model.EditRecipe
import com.tlw.photodokan.core.model.PhotoSizePreset
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

/**
 * A photo imported for a new customer, not yet saved. It will be filed as customer [number];
 * name and phone are optional extras the operator rarely needs.
 */
class Draft(
    val fileName: String,
    val original: ByteArray,
    val preview: PhotoPreview,
    val number: Int,
    val name: String = "",
    val phone: String = "",
) {
    val numberLabel: String get() = number.toString().padStart(4, '0')
}

data class CustomersState(
    val today: StudioDate,
    val date: StudioDate = today,
    val query: String = "",
    /** The day's customers, or search results across all days while [query] is not blank. */
    val customers: List<Customer> = emptyList(),
    val selected: Customer? = null,
    val selectedImage: ImageBitmap? = null,
    val draft: Draft? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
) {
    val searching: Boolean get() = query.isNotBlank()
}

/**
 * The Customers screen: import a photo, file it as the day's next customer, and find customers
 * by day or by number, name or phone. Library and image work runs off the UI thread on [work].
 */
class CustomersViewModel(
    private val library: PhotoLibrary,
    private val clock: () -> StudioDate,
    private val work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _state = MutableStateFlow(CustomersState(today = clock()))
    val state: StateFlow<CustomersState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun previousDay() = showDay(_state.value.date.previous())

    fun nextDay() {
        val next = _state.value.date.next()
        if (next <= _state.value.today) showDay(next)
    }

    /** Also picks up a new day when the app has been open past midnight. */
    fun showToday() = showDay(clock())

    private fun showDay(date: StudioDate) {
        _state.update { it.copy(today = maxOf(it.today, clock()), date = date, query = "", selected = null, selectedImage = null) }
        refresh()
    }

    fun search(query: String) {
        _state.update { it.copy(query = query) }
        refresh()
    }

    fun select(customer: Customer) = launchWork {
        val final = library.readFinal(customer)?.let { decodePreview(it)?.image }
        _state.update { it.copy(selected = customer, selectedImage = final, draft = null, message = null) }
    }

    fun import(source: PhotoSource) = launchWork {
        val original = source.read()
        val preview = decodePreview(original)
        if (preview == null) {
            error("\"${source.name}\" is not a photo Photo Dokan can open. Choose a JPEG or PNG photo.")
        } else {
            val draft = Draft(source.name, original, preview, number = library.nextNumber(clock()))
            _state.update { it.copy(draft = draft, selected = null, selectedImage = null, message = null) }
        }
    }

    fun editDraft(name: String = _state.value.draft?.name.orEmpty(), phone: String = _state.value.draft?.phone.orEmpty()) {
        _state.update { s -> s.copy(draft = s.draft?.let { Draft(it.fileName, it.original, it.preview, it.number, name, phone) }) }
    }

    fun discardDraft() = _state.update { it.copy(draft = null, message = null) }

    /** Files the draft as today's next customer: original, default recipe and final image. */
    fun saveDraft() {
        val draft = _state.value.draft ?: return
        val date = clock()
        launchWork {
            val customer = library.create(date, draft.name, draft.phone, draft.original)
            // Until the editor exists the final image is the upright photo with the default recipe.
            library.saveEdit(customer, EditRecipe(presetId = PhotoSizePreset.STUDIO_SLOT.id), encodeJpeg(draft.preview.image))
            _state.update {
                it.copy(
                    today = date, date = date, query = "", draft = null,
                    selected = customer, selectedImage = draft.preview.image,
                    message = "Saved as customer ${customer.label}.", messageIsError = false,
                )
            }
            loadList()
        }
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
}

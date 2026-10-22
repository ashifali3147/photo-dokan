package com.tlw.photodokan.ui.customers

import com.tlw.photodokan.core.library.StudioDate
import com.tlw.photodokan.core.model.PhotoSizePreset
import com.tlw.photodokan.ui.photo.PhotoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CustomersViewModelTest {

    private val library = InMemoryPhotoLibrary()
    private var now = StudioDate(2026, 10, 22)
    private lateinit var vm: CustomersViewModel

    private val jpeg = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB), "jpg", it) }.toByteArray()
    private fun photo(name: String = "IMG_1.jpg") = PhotoSource(name) { jpeg }

    @BeforeTest
    fun setUp() {
        // Everything runs immediately on the test thread.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vm = CustomersViewModel(library, { now }, work = Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val state get() = vm.state.value

    @Test
    fun savingFilesTodaysNextCustomerWithOriginalRecipeAndFinal() {
        vm.import(photo())
        assertNotNull(state.draft)
        vm.editDraft(name = "Ramesh", phone = "98765 43210")
        vm.saveDraft()

        val customer = library.customersOn(now).single()
        assertEquals(1, customer.number)
        assertEquals("Ramesh", customer.name)
        assertContentEquals(jpeg, library.readOriginal(customer))
        assertEquals(PhotoSizePreset.STUDIO_SLOT.id, library.readRecipe(customer)?.presetId)
        val final = assertNotNull(library.readFinal(customer))
        assertEquals(40, ImageIO.read(ByteArrayInputStream(final)).width, "final.jpg is a readable JPEG")

        assertNull(state.draft)
        assertEquals(customer, state.selected)
        assertEquals(listOf(customer), state.customers)
        assertEquals("Saved as customer 0001.", state.message)
    }

    @Test
    fun numberOnlyIsEnough() {
        vm.import(photo())
        assertEquals("0001", state.draft?.numberLabel, "the button shows the number before saving")
        vm.saveDraft()
        val customer = library.customersOn(now).single()
        assertNull(customer.name)
        assertNull(customer.phone)
    }

    @Test
    fun secondCustomerGetsTheNextNumber() {
        repeat(2) { vm.import(photo()); vm.saveDraft() }
        assertEquals(listOf(1, 2), state.customers.map { it.number })
        vm.import(photo())
        assertEquals(3, state.draft?.number)
    }

    @Test
    fun discardingLeavesTheLibraryEmpty() {
        vm.import(photo())
        vm.discardDraft()
        assertNull(state.draft)
        assertTrue(library.originals.isEmpty())
    }

    @Test
    fun notAPhotoShowsAPlainMessage() {
        vm.import(PhotoSource("notes.txt") { "hello".encodeToByteArray() })
        assertNull(state.draft)
        assertTrue(state.messageIsError)
        assertTrue(state.message!!.startsWith("\"notes.txt\" is not a photo"))
    }

    @Test
    fun daysStepBackButNotPastToday() {
        library.create(StudioDate(2026, 10, 21), "Yesterday", null, jpeg)
        vm.nextDay()
        assertEquals(now, state.date)

        vm.previousDay()
        assertEquals(StudioDate(2026, 10, 21), state.date)
        assertEquals(listOf("Yesterday"), state.customers.map { it.name })

        vm.showToday()
        assertEquals(now, state.date)
        assertTrue(state.customers.isEmpty())
    }

    @Test
    fun searchLooksAcrossAllDays() {
        library.create(StudioDate(2026, 9, 1), "Ramesh", null, jpeg)
        library.create(now, "Suresh", null, jpeg)
        vm.search("ram")
        assertEquals(listOf("Ramesh"), state.customers.map { it.name })
        vm.search("")
        assertEquals(listOf("Suresh"), state.customers.map { it.name }, "blank search shows the day again")
    }

    @Test
    fun savingAfterMidnightFilesUnderTheNewDay() {
        now = StudioDate(2026, 10, 23)
        vm.import(photo())
        vm.saveDraft()
        assertEquals(StudioDate(2026, 10, 23), library.originals.keys.single().date)
        assertEquals(StudioDate(2026, 10, 23), state.date)
    }

    @Test
    fun selectingACustomerShowsTheirFinalImage() {
        vm.import(photo())
        vm.saveDraft()
        val customer = state.selected!!
        vm.showToday()
        assertNull(state.selectedImage)
        vm.select(customer)
        assertEquals(40, state.selectedImage?.width)
    }
}

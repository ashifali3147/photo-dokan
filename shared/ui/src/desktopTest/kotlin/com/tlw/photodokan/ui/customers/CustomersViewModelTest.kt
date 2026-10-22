package com.tlw.photodokan.ui.customers

import com.tlw.photodokan.core.imaging.CropGeometry
import com.tlw.photodokan.core.library.StudioDate
import com.tlw.photodokan.core.model.CropConfig
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
        val final = ImageIO.read(ByteArrayInputStream(assertNotNull(library.readFinal(customer))))
        // final.jpg is the crop at 300 dpi of the studio slot, 36 × 34 mm.
        assertEquals(425, final.width)
        assertEquals(402, final.height)

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
    fun photoSizeAndCropAreSavedAndShapeTheFinal() {
        vm.import(photo())
        vm.setPreset(PhotoSizePreset.PASSPORT_35X45)
        vm.updateCrop { w, h, a, c -> CropGeometry.zoom(w, h, a, c, 2.0) }
        vm.saveDraft()

        val customer = library.customersOn(now).single()
        val recipe = assertNotNull(library.readRecipe(customer))
        assertEquals(PhotoSizePreset.PASSPORT_35X45.id, recipe.presetId)
        assertEquals(2.0, recipe.crop.scale)
        val final = ImageIO.read(ByteArrayInputStream(library.readFinal(customer)!!))
        assertEquals(413 to 531, final.width to final.height, "35 × 45 mm at 300 dpi")
    }

    @Test
    fun reopeningACustomerRestoresTheirEditAndSavesChanges() {
        vm.import(photo())
        vm.updateCrop { w, h, a, c -> CropGeometry.rotate(w, h, a, c, 4.0) }
        vm.saveDraft()
        val customer = state.selected!!
        vm.showToday()
        assertNull(state.edit)

        vm.select(customer)
        assertEquals(4.0, state.edit?.recipe?.crop?.rotationDegrees)
        assertEquals(false, state.edit?.changed)

        vm.updateCrop { w, h, a, c -> CropGeometry.rotate(w, h, a, c, -2.5) }
        assertEquals(true, state.edit?.changed)
        vm.saveChanges()

        assertEquals(-2.5, library.readRecipe(customer)?.crop?.rotationDegrees)
        assertEquals(false, state.edit?.changed)
        assertEquals("Saved changes for customer 0001.", state.message)
        assertContentEquals(jpeg, library.readOriginal(customer), "the original is never touched")
    }

    @Test
    fun cropEditsStayInsideThePhoto() {
        vm.import(photo())
        repeat(20) { vm.updateCrop { w, h, a, c -> CropGeometry.pan(w, h, a, c, 3.0, -3.0) } }
        vm.updateCrop { w, h, a, c -> CropGeometry.rotate(w, h, a, c, 30.0) }
        vm.setPreset(PhotoSizePreset.STAMP_20X25)
        val edit = state.edit!!
        val image = edit.preview.image
        val clamped = CropGeometry.clamp(image.width, image.height, vm.presetOf(edit.recipe).aspect, edit.recipe.crop)
        assertEquals(clamped, edit.recipe.crop, "every change leaves an already-fitting crop")
    }

    @Test
    fun resetGoesBackToTheFullFrame() {
        vm.import(photo())
        vm.updateCrop { w, h, a, c -> CropGeometry.zoom(w, h, a, c, 3.0) }
        vm.resetCrop()
        assertEquals(CropConfig(), state.edit?.recipe?.crop)
    }
}

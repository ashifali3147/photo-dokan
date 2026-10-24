package com.tlw.photodokan.ui.customers

import com.tlw.photodokan.core.face.Face
import com.tlw.photodokan.core.face.FaceCrop
import com.tlw.photodokan.core.face.PointPx
import com.tlw.photodokan.core.imaging.ColorAdjustment
import com.tlw.photodokan.core.imaging.CropGeometry
import com.tlw.photodokan.core.library.StudioDate
import com.tlw.photodokan.core.matte.Matte
import com.tlw.photodokan.core.model.CropConfig
import com.tlw.photodokan.core.model.PhotoSizePreset
import com.tlw.photodokan.core.model.SheetTemplate
import com.tlw.photodokan.ui.editor.Adjustment
import com.tlw.photodokan.ui.photo.PhotoSource
import com.tlw.photodokan.ui.print.InMemoryPrintQueueStore
import com.tlw.photodokan.ui.print.PrintOrders
import com.tlw.photodokan.vision.FaceFinder
import com.tlw.photodokan.vision.MattePng
import com.tlw.photodokan.vision.PortraitMatter
import com.tlw.photodokan.vision.VisionException
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CustomersViewModelTest {

    private val library = InMemoryPhotoLibrary()
    private val store = InMemoryPrintQueueStore()
    private val orders = PrintOrders(store)
    private var now = StudioDate(2026, 10, 22)
    private lateinit var vm: CustomersViewModel

    /** A plain mid-grey photo, so light and colour changes are easy to measure. */
    private val jpeg = ByteArrayOutputStream().also {
        val grey = BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB).apply { createGraphics().apply { color = java.awt.Color(128, 128, 128); fillRect(0, 0, 40, 20); dispose() } }
        ImageIO.write(grey, "jpg", it)
    }.toByteArray()
    private fun photo(name: String = "IMG_1.jpg") = PhotoSource(name) { jpeg }

    @BeforeTest
    fun setUp() {
        // Everything runs immediately on the test thread.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vm = CustomersViewModel(library, { now }, orders, SheetTemplate.STUDIO_A4_5X8, work = Dispatchers.Unconfined)
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
    fun brightnessIsSavedAndLightensTheFinal() {
        vm.import(photo())
        vm.adjust(Adjustment.Brightness, 0.4)
        vm.adjust(Adjustment.Brightness, 1.0)
        vm.commit()
        vm.saveDraft()

        val customer = library.customersOn(now).single()
        assertEquals(1.0, library.readRecipe(customer)?.exposure)
        val final = ImageIO.read(ByteArrayInputStream(library.readFinal(customer)!!))
        val red = java.awt.Color(final.getRGB(200, 200)).red
        assertTrue(red in 240..255, "one stop up doubles mid-grey 128 to white, was $red")
        assertContentEquals(jpeg, library.readOriginal(customer), "the original is never touched")
    }

    @Test
    fun aWholeSliderDragIsOneUndoStep() {
        vm.import(photo())
        for (v in listOf(0.1, 0.2, 0.3, 0.45)) vm.adjust(Adjustment.Warmth, v)
        vm.commit()
        vm.adjust(Adjustment.Contrast, 0.5)
        vm.commit()

        vm.undo()
        assertEquals(0.0, state.edit!!.recipe.contrast)
        assertEquals(0.45, state.edit!!.recipe.warmth)
        vm.undo()
        assertEquals(0.0, state.edit!!.recipe.warmth)
        assertEquals(false, state.edit!!.canUndo)

        vm.redo()
        vm.redo()
        assertEquals(0.45, state.edit!!.recipe.warmth)
        assertEquals(0.5, state.edit!!.recipe.contrast)
        assertEquals(false, state.edit!!.canRedo)
    }

    @Test
    fun undoFirstDropsAChangeStillBeingDragged() {
        vm.import(photo())
        vm.adjust(Adjustment.Colour, 0.3)
        vm.commit()
        vm.adjust(Adjustment.Colour, 0.9) // still dragging
        assertEquals(false, state.edit!!.canRedo)
        vm.undo()
        assertEquals(0.3, state.edit!!.recipe.saturation)
    }

    @Test
    fun undoingBackToTheSavedEditMeansNothingToSave() {
        vm.import(photo())
        vm.saveDraft()
        vm.adjust(Adjustment.Brightness, 0.2)
        vm.commit()
        assertEquals(true, state.edit!!.changed)
        vm.undo()
        assertEquals(false, state.edit!!.changed)
    }

    @Test
    fun resetLightAndColourIsOneStep() {
        vm.import(photo())
        vm.adjust(Adjustment.Brightness, 0.5); vm.commit()
        vm.adjust(Adjustment.Colour, -0.5); vm.commit()
        vm.resetAdjustments()
        assertTrue(ColorAdjustment.isNeutral(state.edit!!.recipe))
        vm.undo()
        assertEquals(-0.5, state.edit!!.recipe.saturation)
    }

    @Test
    fun resetGoesBackToTheFullFrame() {
        vm.import(photo())
        vm.updateCrop { w, h, a, c -> CropGeometry.zoom(w, h, a, c, 3.0) }
        vm.resetCrop()
        assertEquals(CropConfig(), state.edit?.recipe?.crop)
    }

    @Test
    fun savedCustomerGoesIntoThePrintQueue() {
        vm.import(photo())
        vm.saveDraft()
        vm.addToPrint(5)

        val order = orders.queue.value.orders.single()
        assertEquals(state.selected, order.customer)
        assertEquals(5, order.copies)
        assertEquals(orders.queue.value, store.saved, "the queue is saved straight away")
        assertEquals("Customer 0001: 5 copies in the print queue.", state.message)

        vm.addToPrint(10)
        assertEquals(listOf(10), orders.queue.value.orders.map { it.copies }, "adding again changes the copies")

        vm.removeFromPrint()
        assertTrue(orders.queue.value.orders.isEmpty())
    }

    @Test
    fun unsavedChangesMustBeSavedBeforePrinting() {
        vm.import(photo())
        vm.saveDraft()
        vm.adjust(Adjustment.Brightness, 0.4)
        vm.commit()
        vm.addToPrint(5)

        assertTrue(orders.queue.value.orders.isEmpty())
        assertTrue(state.messageIsError)
    }

    @Test
    fun onlyPhotosCroppedToTheSlotSizeFitTheSheet() {
        vm.import(photo())
        vm.setPreset(PhotoSizePreset.PASSPORT_35X45)
        vm.saveDraft()
        vm.addToPrint(5)

        assertTrue(orders.queue.value.orders.isEmpty())
        assertTrue(state.messageIsError)
        assertTrue(state.message!!.contains("Passport 35 × 45 mm"))
    }

    @Test
    fun aNewPhotoIsUnsavedWorkUntilSavedOrDiscarded() {
        assertFalse(state.hasUnsavedWork)
        vm.import(photo())
        assertTrue(state.hasUnsavedWork)
        vm.saveDraft()
        assertFalse(state.hasUnsavedWork, "saved, and nothing changed since")
    }

    @Test
    fun changesToASavedCustomerAreUnsavedWork() {
        vm.import(photo())
        vm.saveDraft()
        vm.adjust(Adjustment.Brightness, 0.5)
        vm.commit()
        assertTrue(state.hasUnsavedWork)
        vm.saveChanges()
        assertFalse(state.hasUnsavedWork)
    }

    @Test
    fun closingTheEditorDropsANewPhoto() {
        vm.import(photo())
        vm.closeEditor()
        assertNull(state.draft)
        assertNull(state.edit)
        assertTrue(library.customersOn(now).isEmpty())
    }

    @Test
    fun closingTheEditorReturnsFromASavedCustomerToTheList() {
        vm.import(photo())
        vm.saveDraft()
        vm.closeEditor()
        assertNull(state.selected)
        assertNull(state.edit)
        assertEquals(1, state.customers.size)
    }

    @Test
    fun aPhotoThatCannotBeReadSaysSo() {
        vm.import(PhotoSource("IMG_7.jpg") { throw SecurityException("Permission Denial: no access to content://media/19") })
        assertEquals("Could not open \"IMG_7.jpg\". Choose it with Choose photo instead.", state.message)
        assertTrue(state.messageIsError)
        assertNull(state.draft)
        assertFalse(state.busy)
    }

    // ---- Fitting the crop to the face ----

    /** A 400 × 600 portrait-shaped photo; the fake finder decides where the face is. */
    private val portrait = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(400, 600, BufferedImage.TYPE_INT_RGB), "jpg", it) }.toByteArray()

    /** Faces as the finder reports them on the 400 × 600 preview, or a failure. */
    private class FakeFinder(var faces: List<Face> = emptyList(), var failure: Exception? = null) : FaceFinder {
        var searches = 0
        override fun find(argb: IntArray, width: Int, height: Int): List<Face> {
            searches++
            assertEquals(400 * 600, argb.size, "the whole preview")
            failure?.let { throw it }
            return faces
        }
    }

    /** An upright face with its eyes at ([x], [eyeY]) and its mouth [d] pixels lower. */
    private fun face(x: Double = 200.0, eyeY: Double = 250.0, d: Double = 40.0) = Face(
        x = x - d, y = eyeY - d, width = 2 * d, height = 2.4 * d,
        eyeLeft = PointPx(x - 0.7 * d, eyeY), eyeRight = PointPx(x + 0.7 * d, eyeY), nose = PointPx(x, eyeY + 0.5 * d),
        mouthLeft = PointPx(x - 0.45 * d, eyeY + d), mouthRight = PointPx(x + 0.45 * d, eyeY + d), score = 0.95,
    )

    private val finder = FakeFinder()

    private fun withFinder() {
        vm = CustomersViewModel(library, { now }, orders, SheetTemplate.STUDIO_A4_5X8, faces = finder, work = Dispatchers.Unconfined)
    }

    private fun importPortrait() = vm.import(PhotoSource("IMG_2.jpg") { portrait })

    private fun fitted(preset: PhotoSizePreset, f: Face = face()) = FaceCrop.fit(400, 600, preset, f)!!.crop

    @Test
    fun aNewPhotoOpensCroppedToTheFace() {
        finder.faces = listOf(face(x = 120.0, d = 15.0), face())
        withFinder()
        assertTrue(vm.canFitFace)
        importPortrait()

        val recipe = assertNotNull(state.edit).recipe
        assertEquals(fitted(PhotoSizePreset.STUDIO_SLOT), recipe.crop, "the larger face, on the slot's guide")
        assertNull(state.message)
        assertEquals(1, finder.searches)
    }

    @Test
    fun aPhotoWithoutAFaceOpensWhole() {
        withFinder()
        importPortrait()
        assertEquals(CropConfig(), state.edit?.recipe?.crop)
        assertEquals("No face found. Move and zoom the photo to fit the guide.", state.message)
        assertFalse(state.messageIsError)
    }

    @Test
    fun aHeadAtTheEdgeIsCroppedAsNearAsItGoes() {
        finder.faces = listOf(face(eyeY = 60.0))
        withFinder()
        importPortrait()
        assertTrue(state.message!!.startsWith("The head is too close to the edge of the photo"))
        val crop = assertNotNull(state.edit).recipe.crop
        assertEquals(crop, CropGeometry.clamp(400, 600, PhotoSizePreset.STUDIO_SLOT.aspect, crop))
    }

    @Test
    fun aFinderThatFailsLeavesCroppingByHand() {
        finder.failure = VisionException("The face_detection_yunet_2023mar.onnx model could not be loaded. Reinstall Photo Dokan.")
        withFinder()
        importPortrait()
        assertNotNull(state.draft, "the photo still opens")
        assertEquals(CropConfig(), state.edit?.recipe?.crop)
    }

    @Test
    fun fitFaceUndoesHandWorkInOneStep() {
        finder.faces = listOf(face())
        withFinder()
        importPortrait()
        vm.updateCrop { w, h, a, c -> CropGeometry.pan(w, h, a, c, 0.2, 0.0) }
        vm.commit()
        val moved = state.edit!!.recipe.crop

        vm.fitFace()
        assertEquals(fitted(PhotoSizePreset.STUDIO_SLOT), state.edit!!.recipe.crop)
        assertEquals(1, finder.searches, "the faces found on opening are used again")
        vm.undo()
        assertEquals(moved, state.edit!!.recipe.crop)
    }

    @Test
    fun anotherPhotoSizeKeepsTheHeadOnItsGuide() {
        finder.faces = listOf(face())
        withFinder()
        importPortrait()
        vm.setPreset(PhotoSizePreset.PASSPORT_35X45)
        assertEquals(fitted(PhotoSizePreset.PASSPORT_35X45), state.edit!!.recipe.crop)
    }

    @Test
    fun aCropMovedByHandStaysPutForAnotherPhotoSize() {
        finder.faces = listOf(face())
        withFinder()
        importPortrait()
        vm.updateCrop { w, h, a, c -> CropGeometry.zoom(w, h, a, c, 0.8) }
        vm.commit()
        val byHand = state.edit!!.recipe.crop
        vm.setPreset(PhotoSizePreset.PASSPORT_35X45)
        assertEquals(CropGeometry.clamp(400, 600, PhotoSizePreset.PASSPORT_35X45.aspect, byHand), state.edit!!.recipe.crop)
    }

    @Test
    fun aSavedCustomerKeepsTheirCrop() {
        vm.import(PhotoSource("IMG_2.jpg") { portrait })
        vm.saveDraft()
        val customer = library.customersOn(now).single()
        finder.faces = listOf(face())
        withFinder()
        vm.select(customer)
        assertEquals(CropConfig(), state.edit?.recipe?.crop)
        assertEquals(0, finder.searches)
    }

    @Test
    fun withoutAFinderThereIsNoFitFace() {
        assertFalse(vm.canFitFace)
        importPortrait()
        vm.fitFace()
        assertEquals(CropConfig(), state.edit?.recipe?.crop)
        assertNull(state.message)
    }

    // ---- Separating the person from the background ----

    /** Says the right half of every photo is the person; [during] runs while it works. */
    private class FakeMatter(var failure: Exception? = null) : PortraitMatter {
        var calls = 0
        var during: () -> Unit = {}
        override fun matte(argb: IntArray, width: Int, height: Int): Matte {
            calls++
            during()
            failure?.let { throw it }
            return Matte(4, 2, byteArrayOf(0, 0, -1, -1, 0, 0, -1, -1))
        }
    }

    private val matter = FakeMatter()
    private val personOnTheRight = Matte(4, 2, byteArrayOf(0, 0, -1, -1, 0, 0, -1, -1))

    private fun withMatter() {
        vm = CustomersViewModel(library, { now }, orders, SheetTemplate.STUDIO_A4_5X8, matter = matter, work = Dispatchers.Unconfined)
    }

    private fun keptMatte() = library.mattes[library.customersOn(now).single()]?.let(MattePng::decode)

    @Test
    fun aNewPhotosMatteIsKeptOnceItIsSaved() {
        withMatter()
        vm.import(photo())
        assertEquals(personOnTheRight, state.edit?.matte)
        assertTrue(library.mattes.isEmpty(), "nothing filed before saving")
        vm.saveDraft()
        assertEquals(personOnTheRight, keptMatte())
    }

    @Test
    fun aCustomersMatteIsWorkedOutOnlyOnce() {
        vm.import(photo())
        vm.saveDraft()
        val customer = library.customersOn(now).single()
        withMatter()
        vm.select(customer)
        assertEquals(personOnTheRight, state.edit?.matte)
        assertEquals(personOnTheRight, keptMatte(), "kept for next time")
        vm.closeCustomer()
        vm.select(customer)
        assertEquals(personOnTheRight, state.edit?.matte)
        assertEquals(1, matter.calls)
    }

    @Test
    fun aDamagedKeptMatteIsWorkedOutAgain() {
        vm.import(photo())
        vm.saveDraft()
        val customer = library.customersOn(now).single()
        library.mattes[customer] = byteArrayOf(1, 2, 3)
        withMatter()
        vm.select(customer)
        assertEquals(personOnTheRight, state.edit?.matte)
        assertEquals(personOnTheRight, keptMatte())
    }

    @Test
    fun aPhotoSavedBeforeItsMatteIsReadyStillGetsIt() {
        // Real threads: the matte is still being worked out while the operator saves.
        val working = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        matter.during = { working.countDown(); release.await() }
        vm = CustomersViewModel(library, { now }, orders, SheetTemplate.STUDIO_A4_5X8, matter = matter, work = Dispatchers.Default)
        vm.import(photo())
        assertTrue(working.await(5, java.util.concurrent.TimeUnit.SECONDS))
        waitFor { state.draft != null && !state.busy }
        vm.saveDraft()
        waitFor { state.selected != null && !state.busy }
        assertTrue(library.mattes.isEmpty(), "no matte yet")

        release.countDown()
        waitFor { library.mattes.isNotEmpty() }
        assertEquals(personOnTheRight, keptMatte())
    }

    private fun waitFor(condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < until) { "timed out" }
            Thread.sleep(10)
        }
    }

    @Test
    fun aMatteForAPhotoNoLongerOpenIsDropped() {
        withMatter()
        matter.during = { vm.closeEditor() }
        vm.import(photo())
        assertNull(state.edit)
        assertTrue(library.mattes.isEmpty())
    }

    @Test
    fun aMatterThatFailsLeavesTheEditorAsItWas() {
        matter.failure = VisionException("The modnet_fp16.onnx model could not be loaded. Reinstall Photo Dokan.")
        withMatter()
        vm.import(photo())
        assertNotNull(state.edit)
        assertNull(state.edit?.matte)
        assertNull(state.message)
        vm.saveDraft()
        assertTrue(library.mattes.isEmpty())
    }
}

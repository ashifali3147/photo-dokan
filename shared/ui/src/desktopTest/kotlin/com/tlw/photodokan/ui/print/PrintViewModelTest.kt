package com.tlw.photodokan.ui.print

import com.tlw.photodokan.core.library.Customer
import com.tlw.photodokan.core.library.StudioDate
import com.tlw.photodokan.core.model.EditRecipe
import com.tlw.photodokan.core.model.PrintQueue
import com.tlw.photodokan.core.model.SheetState
import com.tlw.photodokan.core.model.SheetTemplate
import com.tlw.photodokan.print.CalibrationMeasurement
import com.tlw.photodokan.print.CalibrationSheet
import com.tlw.photodokan.print.PageItem
import com.tlw.photodokan.print.PhotoSettings
import com.tlw.photodokan.print.PrintCalibration
import com.tlw.photodokan.print.PrintDocument
import com.tlw.photodokan.print.PrintException
import com.tlw.photodokan.print.SheetPrinter
import com.tlw.photodokan.ui.customers.InMemoryPhotoLibrary
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PrintViewModelTest {

    private val template = SheetTemplate.STUDIO_A4_5X8 // 40 slots
    private val today = StudioDate(2026, 10, 22)
    private val library = InMemoryPhotoLibrary()
    private val store = InMemoryPrintQueueStore()
    private lateinit var orders: PrintOrders
    private lateinit var vm: PrintViewModel

    private val jpeg = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(36, 34, BufferedImage.TYPE_INT_RGB), "jpg", it) }.toByteArray()

    /** A saved customer with a final.jpg, as the Customers screen leaves them. */
    private fun customer(): Customer = library.create(today, null, null, jpeg).also {
        library.saveEdit(it, EditRecipe(presetId = "studio-slot"), jpeg)
    }

    /**
     * Records what it is asked to print, or fails with [failure]. Its settings window "saves"
     * when [operatorSaves] is set, and cancels otherwise. Like the Windows printer, it keeps a
     * size correction only once photo settings are saved.
     */
    private class FakePrinter(val dpi: Double = 100.0, var failure: String? = null) : SheetPrinter {
        val jobs = mutableListOf<Pair<PrintDocument, String>>()
        var settings = PhotoSettings.NotSaved
        var operatorSaves = true
        var settingsFailure: String? = null
        override fun printerName() = "EPSON L3360 Series"
        override fun photoDpi() = dpi
        override fun print(document: PrintDocument, jobName: String) {
            failure?.let { throw PrintException(it) }
            jobs += document to jobName
        }
        var savedCalibration = PrintCalibration.NONE
        override fun photoSettings() = settings
        override fun calibration() = savedCalibration.takeIf { settings == PhotoSettings.Saved }
        override fun saveCalibration(calibration: PrintCalibration) {
            if (settings != PhotoSettings.Saved) throw PrintException("Save the Photo settings first: the size correction goes with them.")
            savedCalibration = calibration
        }
        override fun choosePhotoSettings(): Boolean {
            settingsFailure?.let { throw PrintException(it) }
            if (operatorSaves) settings = PhotoSettings.Saved
            return operatorSaves
        }
    }

    private val printer = FakePrinter()

    private fun start(saved: PrintQueue = PrintQueue(), withPrinter: Boolean = true) {
        store.saved = saved
        orders = PrintOrders(store)
        vm = PrintViewModel(orders, library, template, printer.takeIf { withPrinter }, work = Dispatchers.Unconfined)
    }

    /** A saved customer whose original is a 720 × 680 photo, red on the left half and blue on the right. */
    private fun halfRedHalfBlue(): Customer {
        val photo = BufferedImage(720, 680, BufferedImage.TYPE_INT_RGB).apply {
            createGraphics().apply {
                color = java.awt.Color.RED; fillRect(0, 0, 360, 680)
                color = java.awt.Color.BLUE; fillRect(360, 0, 360, 680)
                dispose()
            }
        }
        val original = ByteArrayOutputStream().also { ImageIO.write(photo, "jpg", it) }.toByteArray()
        return library.create(today, null, null, original).also {
            // final.jpg is deliberately a tiny black image: printing must not use it.
            library.saveEdit(it, EditRecipe(presetId = "studio-slot"), jpeg)
        }
    }

    private fun images(document: PrintDocument) = document.pages.flatMap { p -> p.items.filterIsInstance<PageItem.Image>() }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val state get() = vm.state.value

    @Test
    fun emptyQueueStillShowsTheSheetInThePrinter() {
        start(PrintQueue(sheet = SheetState((0..9).toSet())))
        val sheet = state.sheets.single()
        assertTrue(sheet.placements.isEmpty())
        assertEquals((0..9).toSet(), sheet.previouslyUsedSlots)
    }

    @Test
    fun queuedCopiesFillTheFreeSlotsWithTheirPhotos() {
        val a = customer()
        val b = customer()
        start(PrintQueue(sheet = SheetState((0..4).toSet())).add(a, 4).add(b, 5))

        val sheet = state.sheets.single()
        assertTrue(sheet.reusesPartialSheet)
        assertEquals((5..13).toList(), sheet.placements.map { it.slotIndex })
        assertEquals(state.queue.orders.map { it.id }.toSet(), state.photos.keys)
    }

    @Test
    fun changesFromTheCustomersScreenShowUpHere() {
        start()
        val a = customer()
        orders.update { it.add(a, 5) }
        assertEquals(5, state.pack.copiesPlaced)
        assertEquals(setOf(state.queue.orders.single().id), state.photos.keys)
    }

    @Test
    fun copiesCanBeChangedAndOrdersRemoved() {
        start(PrintQueue().add(customer(), 5).add(customer(), 5))
        val first = state.queue.orders.first()

        vm.setCopies(first, 10)
        assertEquals(listOf(10, 5), state.queue.orders.map { it.copies })
        vm.setCopies(state.queue.orders.first(), 0)
        assertEquals(1, state.queue.orders.first().copies, "never fewer than one copy")

        vm.remove(state.queue.orders.first())
        assertEquals(1, state.queue.orders.size)
        assertEquals(state.queue, store.saved)
    }

    @Test
    fun overflowShowsSeveralSheets() {
        start(PrintQueue(sheet = SheetState((0 until 35).toSet())).add(customer(), 10))
        assertEquals(2, state.sheets.size)
        vm.showSheet(1)
        assertFalse(state.shownSheet.reusesPartialSheet)
        vm.showSheet(5)
        assertEquals(1, state.sheetIndex)
    }

    @Test
    fun markingPrintedEmptiesTheQueueAndKeepsUsedSlots() {
        start(PrintQueue().add(customer(), 4).add(customer(), 5))
        vm.markPrinted()

        assertTrue(state.queue.orders.isEmpty())
        assertEquals((0..8).toSet(), store.saved.sheet.usedSlots)
        assertEquals("Marked 9 photos as printed. 31 slots are left on this sheet.", state.message)
    }

    @Test
    fun aFullSheetAsksForANewOne() {
        start(PrintQueue(sheet = SheetState((0 until 35).toSet())).add(customer(), 5))
        vm.markPrinted()
        assertEquals(SheetState.FRESH, store.saved.sheet)
        assertEquals("Marked 5 photos as printed. Put a new sheet in the printer.", state.message)
    }

    @Test
    fun newSheetFreesEverySlotButKeepsTheQueue() {
        start(PrintQueue(sheet = SheetState(setOf(0, 1, 2))).add(customer(), 5))
        vm.newSheet()
        assertEquals(SheetState.FRESH, store.saved.sheet)
        assertEquals(1, state.queue.orders.size)
        assertEquals((0..4).toList(), state.shownSheet.placements.map { it.slotIndex })
    }

    @Test
    fun aCustomerWithoutAFinalPhotoStillGetsSlots() {
        val bare = library.create(today, null, null, jpeg)
        start(PrintQueue().add(bare, 5))
        assertEquals(5, state.pack.copiesPlaced)
        assertTrue(state.photos.isEmpty())
    }

    @Test
    fun printingRendersEveryPhotoFromTheOriginalAtThePrinterResolution() {
        start(PrintQueue().add(halfRedHalfBlue(), 4).add(customer(), 5))
        vm.print()

        val (document, jobName) = printer.jobs.single()
        assertEquals("Photo Dokan 0001, 0002", jobName)
        assertEquals(1, document.pages.size)
        val photos = images(document)
        assertEquals(9, photos.size)
        assertEquals((0..8).map { template.slotRect(it) }, photos.map { it.rect })

        // 36 × 34 mm at 100 dpi, rendered from the 720 × 680 original rather than the black final.jpg.
        val printed = ImageIO.read(ByteArrayInputStream(photos.first().jpeg))
        assertEquals(142, printed.width)
        assertEquals(134, printed.height)
        val left = java.awt.Color(printed.getRGB(20, 67))
        val right = java.awt.Color(printed.getRGB(121, 67))
        assertTrue(left.red > 200 && left.blue < 60, "left half is red, was $left")
        assertTrue(right.blue > 200 && right.red < 60, "right half is blue, was $right")
    }

    @Test
    fun aPrintedJobLeavesTheQueueAndUsesItsSlots() {
        start(PrintQueue().add(customer(), 4).add(customer(), 5))
        vm.print()

        assertTrue(state.queue.orders.isEmpty())
        assertEquals((0..8).toSet(), store.saved.sheet.usedSlots)
        assertEquals("Printed 9 photos. 31 slots are left on this sheet.", state.message)
        assertFalse(state.messageIsError)
        assertFalse(state.printing)
    }

    @Test
    fun aFreshSheetGetsCutLinesButAPartUsedOneDoesNot() {
        start(PrintQueue().add(customer(), 5))
        vm.print()
        assertTrue(printer.jobs.last().first.pages.single().items.any { it is PageItem.Line })

        orders.update { it.add(customer(), 5) }
        vm.print()
        assertTrue(printer.jobs.last().first.pages.single().items.none { it is PageItem.Line })
        assertEquals((5..9).map { template.slotRect(it) }, images(printer.jobs.last().first).map { it.rect })
    }

    @Test
    fun anOverflowingQueuePrintsOnePagePerSheet() {
        start(PrintQueue(sheet = SheetState((0 until 35).toSet())).add(customer(), 10))
        vm.print()
        assertEquals(2, printer.jobs.single().first.pages.size)
        assertEquals((0..4).toSet(), store.saved.sheet.usedSlots)
    }

    @Test
    fun aPrinterProblemKeepsTheQueueAndSaysWhy() {
        start(PrintQueue().add(customer(), 5))
        val before = store.saved
        printer.failure = "The printer is set to 216 × 279 mm paper, but this sheet needs 210 × 297 mm."
        vm.print()

        assertEquals(before, store.saved)
        assertEquals(1, state.queue.orders.size)
        assertTrue(state.messageIsError)
        assertEquals(printer.failure, state.message)
        assertFalse(state.printing)
    }

    @Test
    fun theScreenKnowsThePrinterByName() {
        start()
        assertTrue(vm.hasPrinter)
        vm.refresh()
        assertEquals("EPSON L3360 Series", state.printerName)
    }

    @Test
    fun theScreenKnowsWhetherPhotoSettingsAreSaved() {
        start()
        vm.refresh()
        assertEquals(PhotoSettings.NotSaved, state.photoSettings)

        printer.settings = PhotoSettings.Saved
        vm.refresh()
        assertEquals(PhotoSettings.Saved, state.photoSettings)
    }

    @Test
    fun choosingPhotoSettingsSavesThem() {
        start()
        vm.refresh()
        vm.choosePhotoSettings()

        assertEquals(PhotoSettings.Saved, state.photoSettings)
        assertEquals("Photo settings saved. Every print now uses them.", state.message)
        assertFalse(state.messageIsError)
        assertFalse(state.choosingSettings)
    }

    @Test
    fun cancellingTheSettingsWindowChangesNothing() {
        start()
        vm.refresh()
        printer.operatorSaves = false
        vm.choosePhotoSettings()

        assertEquals(PhotoSettings.NotSaved, state.photoSettings)
        assertNull(state.message)
        assertFalse(state.choosingSettings)
    }

    @Test
    fun aSettingsProblemSaysWhy() {
        start()
        printer.settingsFailure = "Could not open the printer \"EPSON L3360 Series\". Check it is switched on."
        vm.choosePhotoSettings()

        assertTrue(state.messageIsError)
        assertEquals(printer.settingsFailure, state.message)
        assertFalse(state.choosingSettings)
    }

    /** A printer with photo settings saved, and the Calibrate window open. */
    private fun startCalibrating(saved: PrintCalibration = PrintCalibration.NONE) {
        printer.settings = PhotoSettings.Saved
        printer.savedCalibration = saved
        start(PrintQueue().add(customer(), 5))
        vm.refresh()
        vm.openCalibration()
    }

    @Test
    fun calibrationIsOfferedOnceThePhotoSettingsAreSaved() {
        start()
        vm.refresh()
        assertNull(state.calibration)
        vm.openCalibration()
        assertFalse(state.calibrating, "nothing to calibrate without photo settings")

        vm.choosePhotoSettings()
        assertEquals(PrintCalibration.NONE, state.calibration)
        vm.openCalibration()
        assertTrue(state.calibrating)
    }

    @Test
    fun theCalibrationSheetPrintsWithoutTouchingTheQueue() {
        startCalibrating()
        val before = store.saved
        vm.printCalibrationSheet()

        val (document, jobName) = printer.jobs.single()
        assertEquals("Photo Dokan calibration sheet", jobName)
        assertEquals(CalibrationSheet.page().paper, document.pages.single().paper)
        assertTrue(document.pages.single().items.any { it is PageItem.Box && it.rect == CalibrationSheet.SQUARE })
        assertEquals(before, store.saved)
        assertTrue(state.calibrating)
        assertFalse(state.printing)
    }

    @Test
    fun measurementsBecomeTheSavedCorrection() {
        startCalibrating()
        vm.saveCalibration("102", "99", "62", "44,05")

        val expected = PrintCalibration.NONE.corrected(CalibrationMeasurement(102.0, 99.0, 62.0, 44.05))
        assertEquals(expected, printer.savedCalibration)
        assertEquals(expected, state.calibration)
        assertFalse(state.calibrating)
        assertEquals("Size correction saved: ${expected.describe()}. Print the calibration sheet again to check it.", state.message)
    }

    @Test
    fun aSecondRoundBuildsOnTheSavedCorrection() {
        val first = PrintCalibration(scaleX = 0.98, offsetXMm = -0.8)
        startCalibrating(saved = first)
        vm.saveCalibration("100.5", "100", "60.2", "45")
        assertEquals(first.corrected(CalibrationMeasurement(100.5, 100.0, 60.2, 45.0)), printer.savedCalibration)
    }

    @Test
    fun missingOrImplausibleNumbersAreRefused() {
        startCalibrating()
        vm.saveCalibration("100", "", "60", "45")
        assertEquals("Enter all four measurements in mm, for example 100.5.", state.calibrationError)

        vm.saveCalibration("1000", "100", "60", "45")
        assertEquals("The square should be close to 100 mm wide. Check the width measurement.", state.calibrationError)

        assertTrue(state.calibrating, "the window stays open to fix the numbers")
        assertEquals(PrintCalibration.NONE, printer.savedCalibration)
    }

    @Test
    fun theCorrectionCanBeRemoved() {
        startCalibrating(saved = PrintCalibration(scaleY = 1.01))
        vm.removeCalibration()
        assertEquals(PrintCalibration.NONE, printer.savedCalibration)
        assertEquals(PrintCalibration.NONE, state.calibration)
        assertEquals("Size correction removed.", state.message)
        assertFalse(state.calibrating)
    }

    @Test
    fun withoutAPrinterSheetsAreMarkedByHand() {
        start(PrintQueue().add(customer(), 5), withPrinter = false)
        assertFalse(vm.hasPrinter)
        vm.print()
        assertTrue(printer.jobs.isEmpty())
        assertEquals(1, state.queue.orders.size)
        vm.refresh()
        assertNull(state.printerName)
        assertEquals(PhotoSettings.NotSupported, state.photoSettings)
        vm.choosePhotoSettings()
        assertNull(state.message)
    }

    @Test
    fun longJobNamesAreShortened() {
        val queue = (1..7).fold(PrintQueue()) { q, n -> q.add(Customer(today, n), 5) }
        assertEquals("Photo Dokan 0001, 0002, 0003, 0004, 0005 …", PrintViewModel.jobName(queue))
    }
}

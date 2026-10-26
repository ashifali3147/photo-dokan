package com.tlw.photodokan.ui.print

import com.tlw.photodokan.core.library.Customer
import com.tlw.photodokan.core.library.StudioDate
import com.tlw.photodokan.core.matte.Matte
import com.tlw.photodokan.core.model.BackgroundConfig
import com.tlw.photodokan.core.model.BackgroundMode
import com.tlw.photodokan.core.model.EditRecipe
import com.tlw.photodokan.core.model.PrintQueue
import com.tlw.photodokan.core.model.SheetState
import com.tlw.photodokan.core.model.SheetTemplate
import com.tlw.photodokan.print.CalibrationMeasurement
import com.tlw.photodokan.print.CalibrationSheet
import com.tlw.photodokan.print.PageItem
import com.tlw.photodokan.print.PdfSheetReader
import com.tlw.photodokan.print.PrintPage
import com.tlw.photodokan.print.PhotoSettings
import com.tlw.photodokan.print.PrintCalibration
import com.tlw.photodokan.print.PrintTone
import com.tlw.photodokan.print.ToneStrip
import com.tlw.photodokan.print.PrintDocument
import com.tlw.photodokan.print.PrintException
import com.tlw.photodokan.print.SheetPrinter
import com.tlw.photodokan.station.JobResult
import com.tlw.photodokan.station.PairedHello
import com.tlw.photodokan.station.PairingInvite
import com.tlw.photodokan.station.StationConnector
import com.tlw.photodokan.station.StationException
import com.tlw.photodokan.station.StationLink
import com.tlw.photodokan.station.StationLinkStore
import com.tlw.photodokan.ui.customers.InMemoryPhotoLibrary
import com.tlw.photodokan.vision.MattePng
import com.tlw.photodokan.vision.PortraitMatter
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
import kotlin.test.assertNotNull
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
     * size correction and print tone only once photo settings are saved.
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
        var savedTone = PrintTone.NONE
        override fun tone() = savedTone.takeIf { settings == PhotoSettings.Saved }
        override fun saveTone(tone: PrintTone) {
            if (settings != PhotoSettings.Saved) throw PrintException("Save the Photo settings first: the print colours go with them.")
            savedTone = tone
        }
        override fun choosePhotoSettings(): Boolean {
            settingsFailure?.let { throw PrintException(it) }
            if (operatorSaves) settings = PhotoSettings.Saved
            return operatorSaves
        }
    }

    private val printer = FakePrinter()

    /** Keeps what it was asked to share, as the phone's share sheet would receive it. */
    private class FakeSharer : SheetSharer {
        val shared = mutableListOf<Pair<ByteArray, String>>()
        override fun share(pdf: ByteArray, fileName: String) {
            shared += pdf to fileName
        }
    }

    private val sharer = FakeSharer()

    /** Keeps what was sent to the phone's print dialog, or fails like a phone with no screen open. */
    private class FakePhonePrinter(var failure: String? = null) : PhonePrinter {
        val jobs = mutableListOf<Pair<PrintDocument, String>>()
        override fun print(document: PrintDocument, jobName: String) {
            failure?.let { throw PrintException(it) }
            jobs += document to jobName
        }
    }

    private val phonePrinter = FakePhonePrinter()

    /** Reads every PDF as [document], or refuses it with [failure]. */
    private class FakeReader(var failure: String? = null) : PdfSheetReader {
        val document = PrintDocument(listOf(PrintPage(SheetTemplate.STUDIO_A4_5X8.paper, emptyList()), PrintPage(SheetTemplate.STUDIO_A4_5X8.paper, emptyList())))
        override fun read(pdf: ByteArray): PrintDocument {
            failure?.let { throw PrintException(it) }
            return document
        }
    }

    private val reader = FakeReader()
    private val phonePdf = PickedFile("Photo Dokan 0001 0002.pdf") { "%PDF-1.4".encodeToByteArray() }

    private val link = StationLink("STUDIO-PC", listOf("192.168.1.5"), 47474, "ab".repeat(32), "device-1", "token-1")

    /** The PC's Print Station as the phone sees it: prints what it is sent, or answers as told. */
    private class FakeStation : StationConnector {
        val sent = mutableListOf<Pair<ByteArray, String>>()
        var answer = JobResult(printed = true, message = "The sheet printed on the PC.")
        var failure: StationException? = null
        override fun pair(invite: PairingInvite, deviceName: String) = throw UnsupportedOperationException()
        override fun check(link: StationLink) = PairedHello(link.stationName, "Galaxy A15")
        override fun send(link: StationLink, pdf: ByteArray, name: String): JobResult {
            failure?.let { throw it }
            sent += pdf to name
            return answer
        }
    }

    private class MemoryLinks(var saved: StationLink?) : StationLinkStore {
        override fun load() = saved
        override fun save(link: StationLink?) { saved = link }
    }

    private val station = FakeStation()
    private val links = MemoryLinks(link)

    private fun start(
        saved: PrintQueue = PrintQueue(),
        withPrinter: Boolean = true,
        withSharer: Boolean = false,
        withReader: Boolean = false,
        withPhonePrinter: Boolean = false,
        withStation: Boolean = false,
        withMatter: Boolean = false,
    ) {
        store.saved = saved
        orders = PrintOrders(store)
        vm = PrintViewModel(
            orders, library, template, printer.takeIf { withPrinter }, sharer.takeIf { withSharer },
            pdfReader = reader.takeIf { withReader }, phonePrinter = phonePrinter.takeIf { withPhonePrinter },
            station = station.takeIf { withStation }, links = links.takeIf { withStation },
            matter = matter.takeIf { withMatter }, work = Dispatchers.Unconfined,
        )
    }

    /** A phone paired with the PC, with [saved] queued; the Print screen has been opened. */
    private fun startPaired(saved: PrintQueue) {
        start(saved, withPrinter = false, withSharer = true, withStation = true)
        vm.refresh()
    }

    /** Says the right half of every photo is the person. */
    private class FakeMatter : PortraitMatter {
        var calls = 0
        override fun matte(argb: IntArray, width: Int, height: Int): Matte {
            calls++
            return personOnTheRight
        }
    }

    private val matter = FakeMatter()

    /** A saved customer whose original is a 720 × 680 photo, red on the left half and blue on the right. */
    private fun halfRedHalfBlue(recipe: EditRecipe = EditRecipe(presetId = "studio-slot")): Customer {
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
            library.saveEdit(it, recipe, jpeg)
        }
    }

    private fun printedColours(): Pair<java.awt.Color, java.awt.Color> {
        val printed = ImageIO.read(ByteArrayInputStream(images(printer.jobs.single().first).first().jpeg))
        return java.awt.Color(printed.getRGB(20, 67)) to java.awt.Color(printed.getRGB(121, 67))
    }

    private val onWhite = EditRecipe(presetId = "studio-slot", background = BackgroundConfig(mode = BackgroundMode.REPLACE))

    @Test
    fun theBackgroundPrintsAsTheEditorShowedIt() {
        val customer = halfRedHalfBlue(onWhite)
        library.saveMatte(customer, MattePng.encode(personOnTheRight))
        start(PrintQueue().add(customer, 4))
        vm.print()
        val (left, right) = printedColours()
        assertTrue(left.red > 245 && left.green > 245 && left.blue > 245, "white behind the person, was $left")
        assertTrue(right.blue > 200 && right.red < 60, "the person as shot, was $right")
    }

    @Test
    fun aCustomerWithoutAKeptMatteGetsOneWhenPrinted() {
        val customer = halfRedHalfBlue(onWhite)
        start(PrintQueue().add(customer, 4), withMatter = true)
        vm.print()
        assertEquals(1, matter.calls)
        assertEquals(personOnTheRight, library.readMatte(customer)?.let(MattePng::decode), "kept for next time")
        assertTrue(printedColours().first.green > 245)
    }

    @Test
    fun aPhotoKeptAsShotNeedsNoMatte() {
        val customer = halfRedHalfBlue(onWhite.copy(background = BackgroundConfig(mode = BackgroundMode.KEEP)))
        start(PrintQueue().add(customer, 4), withMatter = true)
        vm.print()
        assertEquals(0, matter.calls)
        assertTrue(printedColours().first.red > 200)
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

    /** A printer with photo settings saved, and the Match colours window open. */
    private fun startMatchingTone(saved: PrintTone = PrintTone.NONE) {
        printer.settings = PhotoSettings.Saved
        printer.savedTone = saved
        start(PrintQueue().add(customer(), 5))
        vm.refresh()
        vm.openTone()
    }

    @Test
    fun matchingColoursIsOfferedOnceThePhotoSettingsAreSaved() {
        start()
        vm.refresh()
        assertNull(state.tone)
        vm.openTone()
        assertFalse(state.matchingTone, "nothing to match without photo settings")

        vm.choosePhotoSettings()
        assertEquals(PrintTone.NONE, state.tone)
        vm.openTone()
        assertTrue(state.matchingTone)
    }

    @Test
    fun theColourStripPrintsWithoutTheSavedToneOrTouchingTheQueue() {
        startMatchingTone(saved = PrintTone(brightness = 2))
        val before = store.saved
        vm.printToneStrip()

        val (document, jobName) = printer.jobs.single()
        assertEquals("Photo Dokan colour strip", jobName)
        assertFalse(document.applyTone, "the patches carry their own tones")
        assertEquals(ToneStrip.PATCHES, document.pages.single().items.count { it is PageItem.Image })
        assertEquals(before, store.saved)
        assertTrue(state.matchingTone)
        assertFalse(state.printing)
    }

    @Test
    fun thePatchNumberBecomesTheSavedTone() {
        startMatchingTone()
        vm.saveTone(" 9 ")

        assertEquals(ToneStrip.toneOf(9), printer.savedTone)
        assertEquals(ToneStrip.toneOf(9), state.tone)
        assertFalse(state.matchingTone)
        assertEquals("Print colours saved: brighter +2, more colour +1.", state.message)
    }

    @Test
    fun aNumberNotOnTheStripIsRefused() {
        startMatchingTone()
        for (typed in listOf("", "0", "25", "x")) {
            vm.saveTone(typed)
            assertEquals("Enter the number under the patch, from 1 to 24.", state.toneError, "typed \"$typed\"")
        }
        assertTrue(state.matchingTone)
        assertEquals(PrintTone.NONE, printer.savedTone)
    }

    @Test
    fun theToneCanBeRemoved() {
        startMatchingTone(saved = PrintTone(3, 1))
        vm.removeTone()
        assertEquals(PrintTone.NONE, printer.savedTone)
        assertEquals(PrintTone.NONE, state.tone)
        assertFalse(state.matchingTone)
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
    fun thePhoneSharesTheSheetAsAPdf() {
        start(PrintQueue(sheet = SheetState((0..6).toSet())).add(halfRedHalfBlue(), 5).add(customer(), 4), withPrinter = false, withSharer = true)
        assertTrue(vm.canShare)
        vm.sharePdf()

        val (pdf, name) = sharer.shared.single()
        assertEquals("Photo Dokan 0001 0002.pdf", name)
        val text = pdf.decodeToString()
        assertTrue(text.startsWith("%PDF-1.4"))
        assertEquals(1, Regex("/Type /Page ").findAll(text).count(), "one sheet")
        assertEquals(9, Regex(" Do Q").findAll(text).count(), "every copy placed")
        assertTrue(text.contains("/MediaBox [0 0 595.2756 841.8898]"))
        assertFalse(state.printing)
    }

    @Test
    fun sharingIsNotPrinting() {
        val queue = PrintQueue(sheet = SheetState((0..6).toSet())).add(customer(), 5)
        start(queue, withPrinter = false, withSharer = true)
        vm.sharePdf()

        assertEquals(queue, store.saved, "the queue waits until the PC has printed it")
        assertEquals("PDF ready. Send it to the PC and print it there on the part-used sheet (7 of 40 slots used). Then press Mark as printed.", state.message)
        vm.markPrinted()
        assertEquals((0..11).toSet(), store.saved.sheet.usedSlots)
    }

    @Test
    fun aFreshSheetIsSharedForABlankSheet() {
        start(PrintQueue().add(customer(), 5), withPrinter = false, withSharer = true)
        vm.sharePdf()
        assertEquals("PDF ready. Send it to the PC and print it there on a blank photo sheet. Then press Mark as printed.", state.message)
    }

    @Test
    fun aPhotoThatCannotBeOpenedStopsTheShare() {
        val broken = library.create(today, null, null, byteArrayOf(1, 2, 3))
        start(PrintQueue().add(broken, 5), withPrinter = false, withSharer = true)
        vm.sharePdf()
        assertTrue(sharer.shared.isEmpty())
        assertEquals("The photo of customer 0001 could not be opened.", state.message)
        assertTrue(state.messageIsError)
    }

    @Test
    fun thePhonePrintsTheSameSheetThroughItsPrintDialog() {
        start(PrintQueue().add(halfRedHalfBlue(), 5).add(customer(), 4), withPrinter = false, withSharer = true, withPhonePrinter = true)
        assertTrue(vm.canPrintOnPhone)
        vm.printOnPhone()

        val (document, name) = phonePrinter.jobs.single()
        assertEquals("Photo Dokan 0001 0002", name)
        assertEquals(template.paper, document.pages.single().paper)
        assertEquals(9, images(document).size, "every copy placed")
        assertTrue(sharer.shared.isEmpty())
        assertFalse(state.printing)
    }

    @Test
    fun printingOnThePhoneWaitsForMarkAsPrinted() {
        val queue = PrintQueue(sheet = SheetState((0..6).toSet())).add(customer(), 5)
        start(queue, withPrinter = false, withSharer = true, withPhonePrinter = true)
        vm.printOnPhone()

        assertEquals(queue, store.saved, "the dialog opening is not the sheet coming out")
        assertEquals(
            "Put the part-used sheet (7 of 40 slots used) in the printer. In the print screen choose A4 paper, " +
                "and photo paper and best quality if it offers them. Once the sheet has come out right, press Mark as printed.",
            state.message,
        )
        vm.markPrinted()
        assertEquals((0..11).toSet(), store.saved.sheet.usedSlots)
    }

    @Test
    fun aPhoneThatCannotPrintSaysWhy() {
        phonePrinter.failure = "Open Photo Dokan on the screen, then press Print again."
        start(PrintQueue().add(customer(), 5), withPrinter = false, withSharer = true, withPhonePrinter = true)
        vm.printOnPhone()
        assertEquals("Open Photo Dokan on the screen, then press Print again.", state.message)
        assertTrue(state.messageIsError)
        assertFalse(state.printing)
    }

    @Test
    fun withoutAPrintServiceThereIsNoPhonePrinting() {
        start(PrintQueue().add(customer(), 5), withPrinter = false, withSharer = true)
        assertFalse(vm.canPrintOnPhone)
        vm.printOnPhone()
        assertNull(state.message)
    }

    @Test
    fun thePhoneKnowsWhichPcItSendsTo() {
        startPaired(PrintQueue().add(customer(), 5))
        assertTrue(vm.canSend)
        assertEquals("STUDIO-PC", state.stationName)
        links.saved = null
        vm.refresh()
        assertNull(state.stationName, "forgotten in Settings")
    }

    @Test
    fun aSheetSentToThePcIsPrintedThere() {
        startPaired(PrintQueue(sheet = SheetState((0..6).toSet())).add(halfRedHalfBlue(), 5).add(customer(), 4))
        vm.sendToPc()

        val (pdf, name) = station.sent.single()
        assertEquals("Photo Dokan 0001 0002", name)
        val text = pdf.decodeToString()
        assertTrue(text.startsWith("%PDF-1.4"))
        assertEquals(9, Regex(" Do Q").findAll(text).count(), "every copy placed")
        assertTrue(sharer.shared.isEmpty())
        assertFalse(state.printing)
        assertNull(state.sendingTo)
    }

    @Test
    fun aSheetThePcPrintedLeavesTheQueue() {
        startPaired(PrintQueue(sheet = SheetState((0..6).toSet())).add(customer(), 5))
        vm.sendToPc()

        assertTrue(store.saved.orders.isEmpty())
        assertEquals((0..11).toSet(), store.saved.sheet.usedSlots)
        assertEquals("Printed 5 photos on STUDIO-PC. 28 slots are left on this sheet.", state.message)
        assertFalse(state.messageIsError)
        assertNull(state.unsent)
    }

    @Test
    fun anUnreachablePcOffersTheSamePdfToShare() {
        val queue = PrintQueue().add(customer(), 5)
        startPaired(queue)
        station.failure = StationException("The PC can't be reached.", StationException.Reason.Unreachable)
        vm.sendToPc()

        assertEquals(queue, store.saved, "nothing printed")
        assertEquals("The PC can't be reached.", state.message)
        assertTrue(state.messageIsError)
        val unsent = assertNotNull(state.unsent)
        assertEquals("The PC can't be reached.", unsent.reason)
        assertEquals("STUDIO-PC", state.stationName, "still paired: the PC may just be off")

        vm.shareUnsent()
        val (pdf, name) = sharer.shared.single()
        assertTrue(pdf.contentEquals(unsent.pdf), "the PDF already made")
        assertEquals("Photo Dokan 0001.pdf", name)
        assertNull(state.unsent)
        assertEquals("PDF ready. Send it to the PC and print it there on a blank photo sheet. Then press Mark as printed.", state.message)
        assertEquals(queue, store.saved, "sharing is not printing")
    }

    @Test
    fun whatThePcCouldNotPrintIsShownAndCanBeShared() {
        val queue = PrintQueue().add(customer(), 5)
        startPaired(queue)
        station.answer = JobResult(printed = false, message = "The printer is out of paper.")
        vm.sendToPc()

        assertEquals(queue, store.saved)
        assertEquals("The printer is out of paper.", state.message)
        assertTrue(state.messageIsError)
        assertNotNull(state.unsent)
        vm.keepUnsent()
        assertNull(state.unsent)
        assertTrue(sharer.shared.isEmpty())
    }

    @Test
    fun aPhoneRemovedOnThePcForgetsIt() {
        startPaired(PrintQueue().add(customer(), 5))
        station.failure = StationException("This phone isn't paired with the PC. Pair it again in Settings.", StationException.Reason.NotPaired)
        vm.sendToPc()

        assertNull(links.saved)
        assertNull(state.stationName)
        assertEquals("This phone isn't paired with the PC. Pair it again in Settings.", state.message)
        assertNotNull(state.unsent, "the sheet can still be shared")
    }

    @Test
    fun aPcForgottenInSettingsMeanwhileIsNotSentTo() {
        startPaired(PrintQueue().add(customer(), 5))
        links.saved = null
        vm.sendToPc()

        assertTrue(station.sent.isEmpty())
        assertNull(state.stationName)
        assertEquals("This phone isn't paired with a PC any more. Pair it again in Settings.", state.message)
        assertNotNull(state.unsent)
    }

    @Test
    fun aPhoneThatCannotShareKeepsNoUnsentSheet() {
        start(PrintQueue().add(customer(), 5), withPrinter = false, withStation = true)
        station.failure = StationException("The PC can't be reached.", StationException.Reason.Unreachable)
        vm.sendToPc()
        assertEquals("The PC can't be reached.", state.message)
        assertNull(state.unsent)
    }

    @Test
    fun thePcPrintsAPdfFromThePhoneOnceConfirmed() {
        val queue = PrintQueue(sheet = SheetState((0..4).toSet())).add(customer(), 5)
        start(queue, withReader = true)
        assertTrue(vm.canPrintPdf)
        vm.openPdf(phonePdf)
        assertEquals(2, state.pendingPdf?.pages)
        assertTrue(printer.jobs.isEmpty(), "nothing prints before the operator confirms")

        vm.printPdf()
        val (document, jobName) = printer.jobs.single()
        assertEquals(reader.document, document)
        assertEquals("Photo Dokan 0001 0002", jobName)
        assertEquals(queue, store.saved, "this PC's queue and sheet don't change")
        assertEquals("Printed \"Photo Dokan 0001 0002.pdf\". Press Mark as printed on the phone.", state.message)
        assertNull(state.pendingPdf)
        assertFalse(state.printing)
    }

    @Test
    fun aPdfCanBePutAsideWithoutPrinting() {
        start(withReader = true)
        vm.openPdf(phonePdf)
        vm.cancelPdf()
        assertNull(state.pendingPdf)
        vm.printPdf()
        assertTrue(printer.jobs.isEmpty())
    }

    @Test
    fun aPdfThatCannotBePrintedSaysWhy() {
        start(withReader = true)
        reader.failure = "This file is not a PDF, or it is damaged."
        vm.openPdf(phonePdf)
        assertNull(state.pendingPdf)
        assertEquals(reader.failure, state.message)
        assertTrue(state.messageIsError)
    }

    @Test
    fun onlyAPcWithAPrinterPrintsPdfs() {
        start(withPrinter = false, withSharer = true, withReader = true)
        assertFalse(vm.canPrintPdf)
        start(withReader = false)
        assertFalse(vm.canPrintPdf)
    }

    @Test
    fun sharedFileNamesStayShort() {
        val queue = (1..5).fold(PrintQueue()) { q, n -> q.add(Customer(today, n), 5) }
        assertEquals("Photo Dokan 0001 0002 0003 and 2 more.pdf", PrintViewModel.pdfName(queue))
    }

    @Test
    fun longJobNamesAreShortened() {
        val queue = (1..7).fold(PrintQueue()) { q, n -> q.add(Customer(today, n), 5) }
        assertEquals("Photo Dokan 0001, 0002, 0003, 0004, 0005 …", PrintViewModel.jobName(queue))
    }

    private companion object {
        val personOnTheRight = Matte(4, 2, byteArrayOf(0, 0, -1, -1, 0, 0, -1, -1))
    }

    // Blank slots

    @Test
    fun aSlotLeftBlankIsSkippedOnThePrintedPageAndStaysFreeAfterwards() {
        start(PrintQueue().add(customer(), 4))
        vm.toggleBlank(0, 2)
        assertEquals(listOf(0, 1, 3, 4), state.shownSheet.placements.map { it.slotIndex })
        assertEquals(setOf(2), state.shownSheet.blankSlots)

        vm.print()
        val photos = images(printer.jobs.single().first)
        assertEquals(listOf(0, 1, 3, 4).map { template.slotRect(it) }, photos.map { it.rect }, "nothing is printed in the blank slot")
        assertEquals(SheetState(setOf(0, 1, 3, 4)), state.queue.sheet)
        assertEquals(emptyMap(), state.queue.blanks)
        assertEquals(state.queue, store.saved)
    }

    @Test
    fun aPrintedSlotCannotBeLeftBlankAndTheOperatorIsTold() {
        start(PrintQueue(sheet = SheetState(setOf(0, 1))).add(customer(), 4))
        vm.toggleBlank(0, 1)
        assertEquals(emptyMap(), state.queue.blanks)
        assertEquals("That slot is already printed.", state.message)
        assertTrue(state.messageIsError)
    }

    @Test
    fun theLastSlotToPrintOnCannotBeLeftBlank() {
        start(PrintQueue(sheet = SheetState((0..38).toSet())).add(customer(), 1))
        vm.toggleBlank(0, 39)
        assertEquals(emptyMap(), state.queue.blanks)
        assertEquals("Keep at least one slot free to print on.", state.message)
    }

    @Test
    fun leavingASlotBlankOnTheSecondSheetKeepsItShown() {
        start(PrintQueue().add(customer(), 45))
        vm.showSheet(1)
        vm.toggleBlank(1, 0)
        assertEquals(1, state.sheetIndex)
        assertEquals(setOf(0), state.shownSheet.blankSlots)
    }

    @Test
    fun aNewSheetForgetsTheBlanks() {
        start(PrintQueue(sheet = SheetState(setOf(0))).add(customer(), 4))
        vm.toggleBlank(0, 3)
        vm.newSheet()
        assertEquals(emptyMap(), state.queue.blanks)
    }
}

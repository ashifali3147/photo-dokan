package com.tlw.photodokan.ui.print

import com.tlw.photodokan.core.library.StudioDate
import com.tlw.photodokan.core.model.Order
import com.tlw.photodokan.core.model.SheetState
import com.tlw.photodokan.core.model.SheetTemplate
import com.tlw.photodokan.core.packing.PackingEngine
import com.tlw.photodokan.print.PageItem
import com.tlw.photodokan.print.PdfBoxSheetReader
import com.tlw.photodokan.print.PdfFilePrinter
import com.tlw.photodokan.print.PrintDocument
import com.tlw.photodokan.print.SimplePdfRenderer
import com.tlw.photodokan.print.sheetPage
import com.tlw.photodokan.station.JobResult
import com.tlw.photodokan.station.PairedDevices
import com.tlw.photodokan.station.PrintStation
import com.tlw.photodokan.station.StationCertificate
import com.tlw.photodokan.station.StationClient
import com.tlw.photodokan.station.StationPairing
import com.tlw.photodokan.ui.customers.InMemoryPhotoLibrary
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A sheet from the phone, end to end with nothing faked in between: the phone's client sends the
 * PDF its Share PDF makes; the PC's station checks it, prints it (to a PDF file, as on a PC without
 * Windows printing) and keeps it in the library.
 */
class PhoneJobEndToEndTest {

    private val folder: File = Files.createTempDirectory("photo-dokan-phone-job").toFile()
    private val today = StudioDate(2026, 10, 24)
    private val template = SheetTemplate.STUDIO_A4_5X8
    private val library = InMemoryPhotoLibrary()
    private val printer = PdfFilePrinter(File(folder, "prints"), open = false)
    private val pairing = StationPairing(PairedDevices(File(folder, "paired-devices.json")))
    private val station = PrintStation(
        StationCertificate.loadOrCreate(File(folder, "station.p12")), "STUDIO-PC", pairing,
        jobs = StationJobPrinter(PdfBoxSheetReader(), printer, library, { today }, { "14.30" }),
        port = 0, addresses = { listOf("127.0.0.1") },
    ).apply { start() }
    private val phone = StationClient()

    @AfterTest
    fun cleanUp() {
        station.stop()
        folder.deleteRecursively()
    }

    private fun jpeg(): ByteArray =
        ByteArrayOutputStream().also { ImageIO.write(BufferedImage(850, 803, BufferedImage.TYPE_INT_RGB), "jpg", it) }.toByteArray()

    /** What the phone's Share PDF makes for 5 copies of one customer on a fresh sheet. */
    private fun phoneSheet(): ByteArray {
        val sheet = PackingEngine.pack(template, SheetState.FRESH, listOf(Order("A", "0001", 5))).sheets.single()
        val photo = jpeg()
        return SimplePdfRenderer().render(PrintDocument(listOf(sheetPage(template, sheet) { photo })))
    }

    @Test
    fun aPhoneJobPrintsAndIsFiledOnThePc() {
        val link = phone.pair(assertNotNull(station.invite()), "Galaxy A15")
        val pdf = phoneSheet()

        val result = phone.send(link, pdf, "Photo Dokan 0001")

        assertEquals(JobResult(printed = true, message = "The sheet printed on the PC."), result)
        val printed = assertNotNull(printer.lastFile, "printed on the PC")
        val pages = PdfBoxSheetReader().read(printed.readBytes()).pages
        assertEquals(5, pages.single().items.count { it is PageItem.Image }, "every copy printed")
        assertContentEquals(pdf, library.sheets.getValue("2026/10/24/Phone sheets/Photo Dokan 0001 14.30.pdf"), "kept as sent")
    }

    @Test
    fun aPdfPhotoDokanDidNotMakeIsTurnedAway() {
        val link = phone.pair(assertNotNull(station.invite()), "Galaxy A15")
        val notASheet = "%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\ntrailer << /Root 1 0 R >>\n%%EOF".encodeToByteArray()

        val result = phone.send(link, notASheet, "Invoice")

        assertTrue(!result.printed, result.message)
        assertEquals(null, printer.lastFile, "nothing printed")
        assertTrue(library.sheets.isEmpty(), "nothing kept")
    }
}

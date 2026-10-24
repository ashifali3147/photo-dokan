package com.tlw.photodokan.station

import java.io.File
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Sheets sent from the phone's real client to the PC's real station. */
class JobsTest {

    private val folder: File = Files.createTempDirectory("photo-dokan-jobs").toFile()
    private val certificate = StationCertificate.loadOrCreate(File(folder, "station.p12"))
    private val pairing = StationPairing(PairedDevices(File(folder, "paired-devices.json")))
    private val phone = StationClient(timeoutMillis = 2_000)
    private val stations = mutableListOf<PrintStation>()

    /** What the PC's printing was handed, and how many jobs printed at the same moment at most. */
    private val received = Collections.synchronizedList(mutableListOf<Triple<ByteArray, String, String>>())
    private val printingNow = AtomicInteger()
    private val mostAtOnce = AtomicInteger()

    private val printer = StationJobs { pdf, name, deviceName ->
        mostAtOnce.accumulateAndGet(printingNow.incrementAndGet(), ::maxOf)
        Thread.sleep(150) // the printer driver taking its time
        printingNow.decrementAndGet()
        received += Triple(pdf, name, deviceName)
        JobResult(printed = true, message = "Printed $name.")
    }

    @AfterTest
    fun cleanUp() {
        stations.forEach(PrintStation::stop)
        folder.deleteRecursively()
    }

    private fun station(jobs: StationJobs? = printer) =
        PrintStation(certificate, "STUDIO-PC", pairing, jobs, announce = false, port = 0, addresses = { listOf("127.0.0.1") }).also { stations += it; it.start() }

    private fun pairedPhone(station: PrintStation, name: String = "Galaxy A15") = phone.pair(assertNotNull(station.invite()), name)

    private val sheet = "%PDF-1.4 a sheet of passport photos".encodeToByteArray()

    @Test
    fun aPairedPhonesSheetIsPrintedOnThePc() {
        val link = pairedPhone(station())
        val result = phone.send(link, sheet, "Photo Dokan 0001 0002")

        assertEquals(JobResult(printed = true, message = "Printed Photo Dokan 0001 0002."), result)
        val (pdf, name, device) = received.single()
        assertContentEquals(sheet, pdf)
        assertEquals("Photo Dokan 0001 0002", name)
        assertEquals("Galaxy A15", device)
    }

    @Test
    fun anUnpairedPhoneCannotPrint() {
        val link = pairedPhone(station())
        val stranger = link.copy(token = PairedDevices.randomString(32))
        assertEquals(StationException.Reason.NotPaired, assertFailsWith<StationException> { phone.send(stranger, sheet, "Sheet") }.reason)
        assertTrue(received.isEmpty())
    }

    @Test
    fun aPcThatCannotPrintSaysSo() {
        val link = pairedPhone(station(jobs = null))
        val refused = assertFailsWith<StationException> { phone.send(link, sheet, "Sheet") }
        assertEquals(StationException.Reason.Refused, refused.reason)
        assertEquals("This PC can't print sheets from phones.", refused.message)
    }

    @Test
    fun whatThePcCouldNotPrintComesBackToThePhone() {
        val link = pairedPhone(station(jobs = { _, _, _ -> JobResult(printed = false, message = "The printer is out of paper.") }))
        assertEquals(JobResult(printed = false, message = "The printer is out of paper."), phone.send(link, sheet, "Sheet"))
    }

    @Test
    fun sheetsFromTwoPhonesPrintOneAfterTheOther() {
        val station = station()
        val first = pairedPhone(station, "First")
        val second = pairedPhone(station, "Second")
        val sending = listOf(first, second, first).map { link -> thread { phone.send(link, sheet, "Sheet") } }
        sending.forEach(Thread::join)
        assertEquals(3, received.size)
        assertEquals(1, mostAtOnce.get(), "never two at the same moment")
    }

    @Test
    fun aSheetThePcIsSlowToPrintIsNotSentAgain() {
        val slow = StationJobs { pdf, name, deviceName ->
            Thread.sleep(1_000)
            received += Triple(pdf, name, deviceName)
            JobResult(printed = true, message = "Printed.")
        }
        // Two addresses for the same PC, e.g. its Wi-Fi and its cable.
        val link = pairedPhone(station(slow)).copy(hosts = listOf("127.0.0.1", "127.0.0.1"))
        val impatient = StationClient(timeoutMillis = 2_000, printTimeoutMillis = 300)

        val error = assertFailsWith<StationException> { impatient.send(link, sheet, "Sheet") }
        assertEquals(StationException.Reason.NoAnswer, error.reason)
        assertTrue(error.message!!.startsWith("The sheet reached the PC"))
        Thread.sleep(2_000)
        assertEquals(1, received.size, "printed once, not once per address")
    }

    @Test
    fun aSheetWithoutANameIsNamedAfterThePhone() {
        val link = pairedPhone(station())
        phone.send(link, sheet, " ")
        assertEquals("Sheet from Galaxy A15", received.single().second)
    }
}

package com.tlw.photodokan.station

import java.io.File
import java.net.InetAddress
import java.nio.file.Files
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The station announcing itself on this computer's real network, as a phone's search sees it. */
class AnnounceTest {

    private val folder: File = Files.createTempDirectory("photo-dokan-announce").toFile()
    private val certificate = StationCertificate.loadOrCreate(File(folder, "station.p12"))
    private val pairing = StationPairing(PairedDevices(File(folder, "paired-devices.json")))
    private val stations = mutableListOf<PrintStation>()

    @AfterTest
    fun cleanUp() {
        stations.forEach(PrintStation::stop)
        folder.deleteRecursively()
    }

    /** What a search on [address] finds announced with this station's fingerprint, within [seconds]. */
    private fun search(address: String, seconds: Int): ServiceInfo? = JmDNS.create(InetAddress.getByName(address)).use { browser ->
        repeat(seconds) {
            browser.list(StationProtocol.SERVICE_TYPE + ".local.", 1_000)
                .firstOrNull { it.getPropertyString(StationProtocol.SERVICE_FINGERPRINT) == certificate.fingerprint }
                ?.let { return it }
        }
        null
    }

    @Test
    fun theStationCanBeFoundOnTheShopsNetwork() {
        // On the PC's own network, as phones see it; a machine without one has nothing to announce on.
        val address = StationPairing.localAddresses().firstOrNull() ?: return println("No network: skipped")
        val station = PrintStation(certificate, "STUDIO-PC", pairing, port = 0, addresses = { listOf(address) }).also { stations += it; it.start() }

        val found = assertNotNull(search(address, seconds = 10), "announced")
        // Another PC, or this one just restarted, may already hold the name; phones go by the fingerprint.
        assertTrue(found.name.startsWith("STUDIO-PC"), found.name)
        assertEquals(station.listeningPort, found.port)
        assertContentEquals(arrayOf(address), found.inet4Addresses.map { it.hostAddress }.toTypedArray())
        assertEquals(StationProtocol.VERSION.toString(), found.getPropertyString(StationProtocol.SERVICE_VERSION))

        station.stop()
        assertNull(search(address, seconds = 2), "gone once the app closes")
    }
}

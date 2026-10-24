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

    @Test
    fun aPhoneFindsItsPcAtANewAddress() {
        val address = StationPairing.localAddresses().firstOrNull() ?: return println("No network: skipped")
        val station = PrintStation(
            certificate, "STUDIO-PC", pairing, jobs = { _, name, _ -> JobResult(printed = true, message = "Printed $name.") },
            port = 0, addresses = { listOf(address) },
        ).also { stations += it; it.start() }
        val client = StationClient(timeoutMillis = 1_000)
        val paired = client.pair(assertNotNull(station.invite()), "Galaxy A15")
        // What the phone kept from before the router gave the PC a new address (TEST-NET, nobody there).
        val old = paired.copy(hosts = listOf("192.0.2.1"))
        val links = object : StationLinkStore {
            var saved: StationLink? = old
            override fun load() = saved
            override fun save(link: StationLink?) { saved = link }
        }
        val finder = object : StationFinder {
            override fun find(fingerprint: String): List<String> = JmDNS.create(InetAddress.getByName(address)).use { browser ->
                browser.list(StationProtocol.SERVICE_TYPE + ".local.", 6_000)
                    .filter { it.getPropertyString(StationProtocol.SERVICE_FINGERPRINT) == fingerprint }
                    .flatMap { info -> info.inet4Addresses.map { it.hostAddress } }
            }
        }
        Thread.sleep(3_000) // the announcement goes out in the background

        val result = FindingConnector(client, finder, links).send(old, "%PDF-1.4 sheet".encodeToByteArray(), "Photo Dokan 0001")

        assertEquals(JobResult(printed = true, message = "Printed Photo Dokan 0001."), result)
        assertEquals(listOf(address, "192.0.2.1"), links.saved?.hosts)
    }
}

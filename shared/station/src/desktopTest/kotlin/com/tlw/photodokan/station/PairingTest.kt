package com.tlw.photodokan.station

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import javax.net.ssl.HttpsURLConnection
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pairing end to end: the phone's real client against the PC's real station, over HTTPS. */
class PairingTest {

    private val folder: File = Files.createTempDirectory("photo-dokan-pairing").toFile()
    private val certificate = StationCertificate.loadOrCreate(File(folder, "station.p12"))
    private var now = 1_000_000L
    private val devicesFile = File(folder, "paired-devices.json")
    private val pairing = StationPairing(PairedDevices(devicesFile) { now }) { now }
    private val station = PrintStation(certificate, "STUDIO-PC", pairing, port = 0, addresses = { listOf("127.0.0.1") }).apply { start() }
    private val phone = StationClient(timeoutMillis = 2_000)

    @AfterTest
    fun cleanUp() {
        station.stop()
        folder.deleteRecursively()
    }

    private fun invite() = assertNotNull(station.invite())

    @Test
    fun aPhonePairsByScanningTheQr() {
        val link = phone.pair(invite(), "Samsung Galaxy A15")

        assertEquals("STUDIO-PC", link.stationName)
        assertEquals(certificate.fingerprint, link.fingerprint)
        assertEquals(PairedHello("STUDIO-PC", "Samsung Galaxy A15"), phone.check(link))
        assertEquals(listOf("Samsung Galaxy A15"), pairing.devices.devices.value.map { it.name })
    }

    @Test
    fun anUnpairedPhoneIsRejected() {
        val paired = phone.pair(invite(), "Phone")
        val stranger = paired.copy(token = PairedDevices.randomString(32))
        val refused = assertFailsWith<StationException> { phone.check(stranger) }
        assertEquals(StationException.Reason.NotPaired, refused.reason)
        assertEquals("This phone isn't paired with the PC. Pair it again in Settings.", refused.message)
        assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, statusWithoutToken(), "no token at all")
    }

    @Test
    fun aRemovedPhoneIsRejectedAtOnce() {
        val link = phone.pair(invite(), "Old phone")
        pairing.devices.remove(pairing.devices.devices.value.single().id)
        assertEquals(StationException.Reason.NotPaired, assertFailsWith<StationException> { phone.check(link) }.reason)
    }

    @Test
    fun aCodeWorksOnce() {
        val invite = invite()
        phone.pair(invite, "First")
        val second = assertFailsWith<StationException> { phone.pair(invite, "Second") }
        assertEquals(StationException.Reason.Refused, second.reason)
        assertEquals(listOf("First"), pairing.devices.devices.value.map { it.name })
    }

    @Test
    fun aCodeExpiresAfterTenMinutes() {
        val invite = invite()
        now += StationPairing.CODE_MINUTES * 60_000 + 1
        val late = assertFailsWith<StationException> { phone.pair(invite, "Late") }
        assertEquals("This code has expired. Press Pair a phone on the PC again and scan the new code.", late.message)
    }

    @Test
    fun aNewQrReplacesTheOldOne() {
        val old = invite()
        val current = invite()
        assertEquals(StationException.Reason.Refused, assertFailsWith<StationException> { phone.pair(old, "Phone") }.reason)
        phone.pair(current, "Phone")
        pairing.close()
        assertEquals(StationException.Reason.Refused, assertFailsWith<StationException> { phone.pair(invite().copy(code = current.code), "Phone") }.reason)
    }

    @Test
    fun pairedPhonesStayPairedAcrossRestartsWithoutTheirTokensOnDisk() {
        val link = phone.pair(invite(), "Phone")
        val reloaded = PairedDevices(devicesFile)
        assertEquals(pairing.devices.devices.value, reloaded.devices.value)
        assertNotNull(reloaded.find(link.token))
        assertFalse(devicesFile.readText().contains(link.token), "only the token's hash is kept")
    }

    @Test
    fun aPhoneTrustsOnlyThePairedPc() {
        val link = phone.pair(invite(), "Phone")
        val impostor = link.copy(fingerprint = "0".repeat(64))
        val refused = assertFailsWith<StationException> { phone.check(impostor) }
        assertEquals(StationException.Reason.Unreachable, refused.reason)
        assertTrue(refused.message!!.startsWith("Another device answered"), refused.message)
    }

    @Test
    fun theWorkingAddressIsFoundAndTriedFirstNextTime() {
        val invite = invite().let { it.copy(hosts = listOf("127.0.0.2") + it.hosts) }
        val link = phone.pair(invite, "Phone")
        assertEquals(listOf("127.0.0.1", "127.0.0.2"), link.hosts)
    }

    @Test
    fun whenThePcIsClosedThePhoneSaysSo() {
        val link = phone.pair(invite(), "Phone")
        station.stop()
        val refused = assertFailsWith<StationException> { phone.check(link) }
        assertEquals(StationException.Reason.Unreachable, refused.reason)
        assertTrue(refused.message!!.startsWith("The PC can't be reached."), refused.message)
    }

    @Test
    fun noInviteWithoutTheStationOrANetwork() {
        assertNull(PrintStation(certificate, "PC", pairing, port = 0, addresses = { listOf("127.0.0.1") }).invite(), "not started")
        val offline = PrintStation(certificate, "PC", pairing, port = 0, addresses = { emptyList() }).apply { start() }
        try {
            assertNull(offline.invite())
        } finally {
            offline.stop()
        }
    }

    /** What the station answers a request to a paired-only path with no token. */
    private fun statusWithoutToken(): Int {
        val connection = URI("https://127.0.0.1:${station.listeningPort}${StationProtocol.PAIRED_PATH}").toURL().openConnection() as HttpsURLConnection
        connection.sslSocketFactory = StationClient.pinnedTo(certificate.fingerprint).socketFactory
        connection.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
        return try {
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }
}

package com.tlw.photodokan.station

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import java.io.File
import java.net.BindException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.X509TrustManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrintStationTest {

    private val folder: File = Files.createTempDirectory("photo-dokan-station").toFile()
    private val certificate = StationCertificate.loadOrCreate(File(folder, "station.p12"))
    private val stations = mutableListOf<PrintStation>()

    @AfterTest
    fun cleanUp() {
        stations.forEach(PrintStation::stop)
        folder.deleteRecursively()
    }

    private fun running(port: Int = 0) = PrintStation(certificate, "STUDIO-PC", port).also { stations += it; it.start() }

    /**
     * Calls the station as a paired phone will: trusting only the certificate with [fingerprint],
     * whatever name or address it was reached by.
     */
    private fun get(port: Int, path: String, fingerprint: String = certificate.fingerprint): Pair<Int, String> {
        val pinned = object : X509TrustManager {
            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                if (StationCertificate.fingerprintOf(chain.first()) != fingerprint) throw java.security.cert.CertificateException("Not the paired PC")
            }
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = throw UnsupportedOperationException()
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(pinned), null) }
        val connection = URI("https://127.0.0.1:$port$path").toURL().openConnection() as HttpsURLConnection
        connection.sslSocketFactory = tls.socketFactory
        connection.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true } // the fingerprint is the check
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        return try {
            connection.responseCode to (if (connection.responseCode < 400) connection.inputStream else connection.errorStream)?.bufferedReader()?.readText().orEmpty()
        } finally {
            connection.disconnect()
        }
    }

    @Test
    fun aPhoneOnTheNetworkSeesWhoIsListening() {
        val station = running()
        val (code, body) = get(station.listeningPort!!, StationProtocol.STATUS_PATH)
        assertEquals(HttpURLConnection.HTTP_OK, code)
        assertEquals(StationStatus(name = "STUDIO-PC"), Json.decodeFromString<StationStatus>(body))
    }

    @Test
    fun aDifferentCertificateIsRefused() {
        val station = running()
        assertFailsWith<SSLHandshakeException> { get(station.listeningPort!!, StationProtocol.STATUS_PATH, fingerprint = "0".repeat(64)) }
    }

    @Test
    fun theCertificateIsKeptAcrossRestarts() {
        val file = File(folder, "station.p12")
        assertEquals(certificate.fingerprint, StationCertificate.loadOrCreate(file).fingerprint)
        assertEquals(64, certificate.fingerprint.length)
        assertEquals("CN=Photo Dokan Print Station,O=Photo Dokan", certificate.certificate.subjectX500Principal.name)
        assertTrue(certificate.certificate.notAfter.toInstant().isAfter(java.time.Instant.now().plus(java.time.Duration.ofDays(3650))), "valid for years")
    }

    @Test
    fun aDamagedCertificateFileIsReplaced() {
        val file = File(folder, "damaged.p12").apply { writeText("not a key store") }
        val replaced = StationCertificate.loadOrCreate(file)
        assertNotEquals(certificate.fingerprint, replaced.fingerprint)
        assertEquals(replaced.fingerprint, StationCertificate.loadOrCreate(file).fingerprint, "and kept from then on")
    }

    @Test
    fun aSecondCopyOfTheAppCannotTakeThePort() {
        val first = running()
        val second = PrintStation(certificate, "STUDIO-PC", first.listeningPort!!).also { stations += it }
        assertFailsWith<BindException> { second.start() }
        assertNull(second.listeningPort)
        assertEquals(HttpURLConnection.HTTP_OK, get(first.listeningPort!!, StationProtocol.STATUS_PATH).first, "the first keeps working")
    }

    @Test
    fun theStationStopsAndStartsAgain() {
        val station = running()
        station.start() // already running: nothing happens
        station.stop()
        station.stop()
        assertNull(station.listeningPort)
        station.start()
        assertEquals(HttpURLConnection.HTTP_OK, get(station.listeningPort!!, StationProtocol.STATUS_PATH).first)
    }

    @Test
    fun theStationNeverKeepsTheAppFromClosing() {
        val before = Thread.getAllStackTraces().keys
        fun blocking() = Thread.getAllStackTraces().keys.filter { it.isAlive && !it.isDaemon && it !in before }.map { it.name }
        val station = running()
        assertEquals(emptyList(), blocking(), "while it runs")
        station.stop()
        // Netty's helper for shutting down ends itself after a second of quiet.
        val deadline = System.nanoTime() + 3_000_000_000
        while (blocking().isNotEmpty() && System.nanoTime() < deadline) Thread.sleep(100)
        assertEquals(emptyList(), blocking(), "within moments of stopping")
    }

    @Test
    fun callersOutsideTheShopAreRefused() = testApplication {
        var reached = false
        application {
            station("STUDIO-PC", allowed = { false })
            routing { get("/reached") { reached = true } }
        }
        val response = client.get(StationProtocol.STATUS_PATH)
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("", response.bodyAsText())
        assertEquals(HttpStatusCode.Forbidden, client.get("/reached").status)
        assertTrue(!reached, "the route never ran")
    }
}

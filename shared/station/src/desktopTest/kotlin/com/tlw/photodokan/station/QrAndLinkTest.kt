package com.tlw.photodokan.station

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QrAndLinkTest {

    private val folder: File = Files.createTempDirectory("photo-dokan-link").toFile()

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    private val invite = PairingInvite(
        listOf("192.168.1.5", "10.0.0.2"), 47474,
        "e473de1fd170c260288c9ef462317f75900f7bc02803c88d2a0cf09f42fd9364", "Zx3k9Qm2Lp7Wv1Rt-8_aBc",
    )

    /** [modules] drawn as a camera would see a screen: [scale] pixels each, a white border, off-white paper. */
    private fun photograph(modules: List<BooleanArray>, scale: Int, border: Int = 4): Triple<ByteArray, Int, Int> {
        val size = (modules.size + 2 * border) * scale
        val pixels = ByteArray(size * size) { 230.toByte() }
        modules.forEachIndexed { y, row ->
            row.forEachIndexed { x, dark ->
                if (dark) for (dy in 0 until scale) for (dx in 0 until scale) {
                    pixels[((y + border) * scale + dy) * size + (x + border) * scale + dx] = 25
                }
            }
        }
        return Triple(pixels, size, size)
    }

    @Test
    fun theQrOnThePcReadsBackOnThePhone() {
        val modules = QrCodes.encode(invite.toUri())
        assertTrue(modules.size in 33..61, "a QR the phone can read from arm's length: ${modules.size} modules")
        assertTrue(modules.all { it.size == modules.size }, "square")
        val (pixels, width, height) = photograph(modules, scale = 6)
        assertEquals(invite, PairingInvite.parse(QrCodes.decode(pixels, width, height)!!))
    }

    @Test
    fun aFrameWithoutAQrReadsAsNothing() {
        assertNull(QrCodes.decode(ByteArray(320 * 240) { (it % 256).toByte() }, 320, 240))
    }

    @Test
    fun thePairedPcIsRememberedAndForgotten() {
        val store = FileStationLinkStore(File(folder, "station.json"))
        assertNull(store.load())
        val link = StationLink("STUDIO-PC", invite.hosts, invite.port, invite.fingerprint, "device-1", "token-1")
        store.save(link)
        assertEquals(link, FileStationLinkStore(File(folder, "station.json")).load())
        store.save(null)
        assertNull(store.load())
    }

    @Test
    fun aDamagedFileMeansNotPaired() {
        val file = File(folder, "station.json").apply { writeText("{ not json") }
        assertNull(FileStationLinkStore(file).load())
    }
}

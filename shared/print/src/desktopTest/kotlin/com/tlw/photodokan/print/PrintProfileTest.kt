package com.tlw.photodokan.print

import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrintProfileTest {

    private val folder: File = Files.createTempDirectory("photo-dokan-profile").toFile()
    private val file = File(folder, "settings/photo-print-profile.bin")

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    /**
     * A DEVMODEW as a driver returns it: [size] public bytes followed by [extra] private ones.
     * The array is never shorter than the header, so a bad [size] can be written into it.
     */
    private fun devMode(extra: Int = 1200, size: Int = 220): ByteArray = ByteArray(maxOf(size + extra, 80)) { (it % 251).toByte() }.apply {
        this[68] = (size and 0xFF).toByte()
        this[69] = (size shr 8).toByte()
        this[70] = (extra and 0xFF).toByte()
        this[71] = (extra shr 8).toByte()
    }

    @Test
    fun acceptsADevModeWhosePartsAddUp() {
        assertTrue(DevMode.isWellFormed(devMode()))
        assertTrue(DevMode.isWellFormed(devMode(extra = 0)))
    }

    @Test
    fun rejectsDamagedOrTruncatedDevModes() {
        assertFalse(DevMode.isWellFormed(ByteArray(0)))
        assertFalse(DevMode.isWellFormed(ByteArray(75)))
        assertFalse(DevMode.isWellFormed(devMode().copyOf(1000)), "cut short")
        assertFalse(DevMode.isWellFormed(devMode() + 0), "a byte too many")
        assertFalse(DevMode.isWellFormed(devMode(size = 40, extra = 40)), "dmSize too small")
        assertFailsWith<IllegalArgumentException> { PrintProfile("EPSON L3360 Series", ByteArray(10)) }
    }

    @Test
    fun trimsADriverBufferToTheDevModeInIt() {
        val bytes = devMode()
        assertContentEquals(bytes, DevMode.trim(bytes + ByteArray(64)))
        assertContentEquals(bytes, DevMode.trim(bytes))
        assertNull(DevMode.trim(bytes.copyOf(1000)), "incomplete")
        assertNull(DevMode.trim(ByteArray(40)))
    }

    @Test
    fun belongsOnlyToItsOwnPrinter() {
        val profile = PrintProfile("EPSON L3360 Series", devMode())
        assertTrue(profile.isFor("EPSON L3360 Series"))
        assertFalse(profile.isFor("EPSON L360 Series"))
        assertFalse(profile.isFor("Microsoft Print to PDF"))
    }

    @Test
    fun savesAndLoadsTheProfile() {
        val store = FilePrintProfileStore(file)
        assertNull(store.load(), "nothing saved yet")

        val bytes = devMode()
        store.save(PrintProfile("\\\\STUDIO-PC\\EPSON L3360 Series", bytes))
        val loaded = FilePrintProfileStore(file).load()!!
        assertEquals("\\\\STUDIO-PC\\EPSON L3360 Series", loaded.printerName)
        assertContentEquals(bytes, loaded.devMode)
    }

    @Test
    fun savingAgainReplacesTheProfile() {
        val store = FilePrintProfileStore(file)
        store.save(PrintProfile("EPSON L3360 Series", devMode(extra = 1200)))
        store.save(PrintProfile("EPSON L360 Series", devMode(extra = 800)))

        val loaded = store.load()!!
        assertEquals("EPSON L360 Series", loaded.printerName)
        assertEquals(1020, loaded.devMode.size)
        assertFalse(File(file.path + ".tmp").exists())
    }

    @Test
    fun keepsTheCalibrationWithTheSettings() {
        val calibration = PrintCalibration(scaleX = 0.992, scaleY = 1.004, offsetXMm = -0.7, offsetYMm = 1.3)
        FilePrintProfileStore(file).save(PrintProfile("EPSON L3360 Series", devMode(), calibration))
        assertEquals(calibration, FilePrintProfileStore(file).load()!!.calibration)
        assertEquals(PrintCalibration.NONE, PrintProfile("EPSON L3360 Series", devMode()).calibration)
    }

    @Test
    fun loadsProfilesSavedBeforeCalibrationExisted() {
        val bytes = devMode()
        file.parentFile.mkdirs()
        DataOutputStream(file.outputStream()).use { out ->
            out.writeInt(0x50445050)
            out.writeInt(1)
            out.writeUTF("EPSON L3360 Series")
            out.writeInt(bytes.size)
            out.write(bytes)
        }
        val loaded = FilePrintProfileStore(file).load()!!
        assertContentEquals(bytes, loaded.devMode)
        assertEquals(PrintCalibration.NONE, loaded.calibration)
    }

    @Test
    fun aDamagedFileMeansNothingIsSaved() {
        file.parentFile.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        assertNull(FilePrintProfileStore(file).load())

        FilePrintProfileStore(file).save(PrintProfile("EPSON L3360 Series", devMode()))
        file.writeBytes(file.readBytes().copyOf(200)) // cut off part-way through the DEVMODE
        assertNull(FilePrintProfileStore(file).load())
    }
}

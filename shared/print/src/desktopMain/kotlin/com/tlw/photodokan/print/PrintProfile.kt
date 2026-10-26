package com.tlw.photodokan.print

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The photo settings chosen once in the printer's own settings window: the Windows DEVMODE the
 * driver returned, kept as it is, for the printer it belongs to, and the [calibration] and
 * [tone] measured with them. Another printer's DEVMODE must never be handed to a driver, so [printerName] is
 * checked before every use.
 */
class PrintProfile(
    val printerName: String,
    val devMode: ByteArray,
    val calibration: PrintCalibration = PrintCalibration.NONE,
    val tone: PrintTone = PrintTone.NONE,
) {
    init {
        require(printerName.isNotBlank()) { "A print profile needs a printer name" }
        require(DevMode.isWellFormed(devMode)) { "Not a DEVMODE: ${devMode.size} bytes" }
    }

    fun isFor(printer: String): Boolean = printerName == printer
}

/** The few DEVMODEW fields needed to tell a real one from a damaged file. */
internal object DevMode {
    /** dmDeviceName (32 WCHARs), dmSpecVersion and dmDriverVersion come first. */
    private const val SIZE_OFFSET = 68
    private const val DRIVER_EXTRA_OFFSET = 70

    /** dmSize up to and including dmFields; anything shorter is not a DEVMODE. */
    private const val MIN_SIZE = 76

    /** True when the public part and the driver's private part add up to exactly [bytes]. */
    fun isWellFormed(bytes: ByteArray): Boolean = length(bytes) == bytes.size

    /**
     * [buffer] cut to the DEVMODE it holds: drivers may ask for a buffer larger than they fill.
     * Null when it holds no complete DEVMODE.
     */
    fun trim(buffer: ByteArray): ByteArray? {
        val length = length(buffer) ?: return null
        return if (length <= buffer.size) buffer.copyOf(length) else null
    }

    /** dmSize + dmDriverExtra, or null when [bytes] cannot be a DEVMODE. */
    private fun length(bytes: ByteArray): Int? {
        if (bytes.size < MIN_SIZE) return null
        val size = word(bytes, SIZE_OFFSET)
        return if (size >= MIN_SIZE) size + word(bytes, DRIVER_EXTRA_OFFSET) else null
    }

    /** A little-endian WORD. */
    private fun word(bytes: ByteArray, offset: Int) =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
}

/** Keeps the one [PrintProfile] the studio uses in a small binary [file]. */
class FilePrintProfileStore(private val file: File) {

    /** A missing or damaged file means no settings are saved, rather than stopping the app. */
    fun load(): PrintProfile? {
        if (!file.isFile) return null
        return runCatching {
            DataInputStream(ByteArrayInputStream(file.readBytes())).use { input ->
                if (input.readInt() != MAGIC) return null
                val version = input.readInt()
                if (version !in 1..VERSION) return null
                val name = input.readUTF()
                val devMode = ByteArray(input.readInt()).also(input::readFully)
                // Version 1 files were saved before calibration existed.
                val calibration = if (version >= 2) {
                    PrintCalibration(input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble())
                } else {
                    PrintCalibration.NONE
                }
                // Version 2 files were saved before the print tone existed.
                val tone = if (version >= 3) PrintTone(input.readInt(), input.readInt()) else PrintTone.NONE
                PrintProfile(name, devMode, calibration, tone)
            }
        }.getOrNull()
    }

    fun save(profile: PrintProfile) {
        val bytes = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(VERSION)
                out.writeUTF(profile.printerName)
                out.writeInt(profile.devMode.size)
                out.write(profile.devMode)
                with(profile.calibration) {
                    out.writeDouble(scaleX)
                    out.writeDouble(scaleY)
                    out.writeDouble(offsetXMm)
                    out.writeDouble(offsetYMm)
                }
                out.writeInt(profile.tone.brightness)
                out.writeInt(profile.tone.colour)
            }
        }.toByteArray()
        file.parentFile?.mkdirs()
        // Written beside the old file and moved over it, so a crash never leaves half a profile.
        val temp = File(file.path + ".tmp")
        temp.writeBytes(bytes)
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private companion object {
        /** "PDPP": Photo Dokan print profile. */
        const val MAGIC = 0x50445050
        const val VERSION = 3
    }
}

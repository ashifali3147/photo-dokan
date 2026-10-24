package com.tlw.photodokan.station

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * The phones paired with this PC, kept in [file] so they stay paired across restarts. A phone
 * proves itself with the token it got when pairing; removing it makes that token useless at once.
 */
class PairedDevices(private val file: File, private val clock: () -> Long = System::currentTimeMillis) {

    private val _devices = MutableStateFlow(load())

    /** Oldest first, for the paired-devices list. */
    val devices: StateFlow<List<PairedDevice>> = _devices.asStateFlow()

    /** Adds a phone called [name] and returns it with its new token, which is not kept here. */
    @Synchronized
    fun add(name: String): Pair<PairedDevice, String> {
        val token = randomString(TOKEN_BYTES)
        val device = PairedDevice(randomString(ID_BYTES), cleanName(name), clock(), hash(token))
        save(_devices.value + device)
        return device to token
    }

    /** The phone whose token this is, or null. */
    fun find(token: String): PairedDevice? {
        val hash = hash(token).toByteArray()
        // Compares every stored hash in full, so timing can't hint at a near miss.
        return _devices.value.lastOrNull { MessageDigest.isEqual(it.tokenHash.toByteArray(), hash) }
    }

    @Synchronized
    fun remove(id: String) {
        save(_devices.value.filterNot { it.id == id })
    }

    private fun save(devices: List<PairedDevice>) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, ".${file.name}.tmp")
        temp.writeText(json.encodeToString(devices))
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        _devices.value = devices
    }

    /** A missing or damaged file means no phones are paired; they can pair again. */
    private fun load(): List<PairedDevice> =
        if (file.isFile) runCatching { json.decodeFromString<List<PairedDevice>>(file.readText()) }.getOrDefault(emptyList()) else emptyList()

    companion object {
        private const val TOKEN_BYTES = 32
        private const val ID_BYTES = 9
        private const val MAX_NAME_LENGTH = 60

        private val random = SecureRandom()
        private val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            prettyPrint = true
        }

        /** [bytes] random bytes as URL-safe text without padding, e.g. for tokens and pairing codes. */
        internal fun randomString(bytes: Int): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(bytes).also(random::nextBytes))

        internal fun hash(token: String): String =
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }

        private fun cleanName(name: String): String =
            name.filter { !it.isISOControl() }.trim().take(MAX_NAME_LENGTH).ifEmpty { "Phone" }
    }
}

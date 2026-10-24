package com.tlw.photodokan.station

import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.MessageDigest

/**
 * Pairing on the PC: while the operator shows the QR, one code is open; the first phone to send it
 * in time is added to [devices]. Showing a new QR replaces the old code, and a code works once.
 */
class StationPairing(
    val devices: PairedDevices,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Open(val code: String, val expiresAtMillis: Long)

    @Volatile
    private var open: Open? = null

    /** A new code for the QR, valid for [CODE_MINUTES]; any earlier code stops working. */
    @Synchronized
    fun newCode(): String = PairedDevices.randomString(CODE_BYTES).also { open = Open(it, clock() + CODE_MINUTES * 60_000) }

    /** The QR was closed without pairing: the code stops working. */
    @Synchronized
    fun close() {
        open = null
    }

    /**
     * Adds the phone called [deviceName] when [code] is the open one and still valid, and returns
     * it with its token; otherwise why not, in words for the operator.
     */
    @Synchronized
    fun pair(code: String, deviceName: String): Result<Pair<PairedDevice, String>> {
        val current = open ?: return failure("Pairing isn't open on the PC. Press Pair a phone there and scan the new code.")
        if (clock() > current.expiresAtMillis) {
            open = null
            return failure("This code has expired. Press Pair a phone on the PC again and scan the new code.")
        }
        if (!MessageDigest.isEqual(current.code.toByteArray(), code.toByteArray())) {
            return failure("This code isn't the one on the PC's screen. Scan the code shown there now.")
        }
        open = null
        return Result.success(devices.add(deviceName))
    }

    private fun failure(message: String) = Result.failure<Pair<PairedDevice, String>>(PairingRefused(message))

    class PairingRefused(message: String) : Exception(message)

    companion object {
        const val CODE_MINUTES = 10L
        private const val CODE_BYTES = 16

        /**
         * This PC's addresses that phones on the shop's Wi-Fi can reach, the usual home-router range
         * first. Virtual adapters (VirtualBox, VPNs, Hyper-V) are left out where Windows says so.
         */
        fun localAddresses(): List<String> =
            NetworkInterface.networkInterfaces().toList()
                .filter { runCatching { it.isUp && !it.isLoopback && !it.isVirtual && !it.isPointToPoint }.getOrDefault(false) }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .map { it.hostAddress }
                .filter { LocalNetwork.isLocal(it) && !it.startsWith("169.254.") }
                .distinct()
                .sortedBy { if (it.startsWith("192.168.")) 0 else if (it.startsWith("10.")) 1 else 2 }
    }
}

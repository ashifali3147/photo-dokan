package com.tlw.photodokan.station

import kotlinx.serialization.Serializable

/**
 * The Print Station: the studio PC's app listens on the shop's Wi-Fi so phones can send it sheets
 * to print. Everything here is shared by the PC (the server) and the phone (the client).
 *
 * It speaks HTTPS with the PC's own certificate, which phones learn when they are paired, and
 * answers only devices on the local network.
 */
object StationProtocol {
    /** Fixed, so the Windows firewall rule can name it. */
    const val PORT = 47474

    /** Raised when a change would confuse phones running an older app. */
    const val VERSION = 1

    /** Who is listening: answered to anyone on the network, before pairing. */
    const val STATUS_PATH = "/v1/status"
}

/** The answer to [StationProtocol.STATUS_PATH]. */
@Serializable
data class StationStatus(
    val app: String = APP,
    val version: Int = StationProtocol.VERSION,
    /** The PC's name, so the operator recognises it on the phone. */
    val name: String,
) {
    companion object {
        const val APP = "Photo Dokan Print Station"
    }
}

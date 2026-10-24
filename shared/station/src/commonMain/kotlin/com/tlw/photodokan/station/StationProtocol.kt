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

    /** A phone trades the one-time code from the PC's QR for its own token: [PairRequest] → [PairResponse]. */
    const val PAIR_PATH = "/v1/pair"

    /** Answers a paired phone with [PairedHello], and anyone else with 401. */
    const val PAIRED_PATH = "/v1/paired"

    /** How a paired phone proves itself on every other request: `Authorization: Bearer <token>`. */
    const val AUTHORIZATION = "Authorization"
    const val BEARER = "Bearer "
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

@Serializable
data class PairRequest(
    /** The one-time code from the QR on the PC. */
    val code: String,
    /** The phone's name for the PC's paired-devices list, e.g. "Samsung Galaxy A15". */
    val deviceName: String,
)

@Serializable
data class PairResponse(
    val deviceId: String,
    /** The phone's own secret for later requests; the PC keeps only its hash. */
    val token: String,
    val stationName: String,
)

@Serializable
data class PairedHello(val stationName: String, val deviceName: String)

/** Why the station refused, in words for the operator. */
@Serializable
data class StationError(val message: String)

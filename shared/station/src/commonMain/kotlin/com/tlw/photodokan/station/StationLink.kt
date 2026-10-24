package com.tlw.photodokan.station

import kotlinx.serialization.Serializable

/** What a phone keeps about the PC it is paired with. */
@Serializable
data class StationLink(
    val stationName: String,
    /** The PC's addresses, the one that answered last first. */
    val hosts: List<String>,
    val port: Int,
    /** The PC's certificate; the phone trusts no other. */
    val fingerprint: String,
    val deviceId: String,
    val token: String,
)

/** Why talking to the PC failed: [message] is for the operator. */
class StationException(message: String, val reason: Reason) : Exception(message) {
    enum class Reason {
        /** No address answered: the PC is off, the app closed, or the phone is on another network. */
        Unreachable,

        /** The PC answered but said no, e.g. an expired pairing code. */
        Refused,

        /** The PC no longer knows this phone: it was removed there. */
        NotPaired,

        /** The PC got the request but didn't answer in time, so it may have acted on it. */
        NoAnswer,
    }
}

/** The phone's side of the Print Station. Blocking: call it off the main thread. */
interface StationConnector {
    /** Pairs with the PC in [invite] under [deviceName]. Throws [StationException]. */
    fun pair(invite: PairingInvite, deviceName: String): StationLink

    /** Asks the PC whether this phone is still paired. Throws [StationException]. */
    fun check(link: StationLink): PairedHello

    /**
     * Sends a sheet [pdf] to the PC, which prints it straight away, and returns how that went.
     * Throws [StationException] when the PC can't be reached or refuses the job.
     */
    fun send(link: StationLink, pdf: ByteArray, name: String): JobResult
}

package com.tlw.photodokan.station

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/** A phone allowed to send jobs. Only a hash of its token is kept, so the file alone can't be used to pose as it. */
@Serializable
data class PairedDevice(
    val id: String,
    val name: String,
    val pairedAtMillis: Long,
    internal val tokenHash: String,
)

/** The PC's side of the Print Station, as its Settings screen sees it. */
interface StationHost {
    /** The PC's name as phones show it. */
    val name: String

    /** Whether phones can reach the station now; false while it starts, or when it couldn't. */
    val running: StateFlow<Boolean>

    /** The paired phones, oldest first. */
    val devices: StateFlow<List<PairedDevice>>

    /** Opens pairing and returns what the QR shows, or null when phones couldn't reach the PC. */
    fun invite(): PairingInvite?

    /** The QR was closed: its code stops working. */
    fun closeInvite()

    /** The phone can no longer send anything, until it is paired again. */
    fun remove(deviceId: String)
}

/** Where a phone keeps the PC it is paired with. */
interface StationLinkStore {
    fun load(): StationLink?

    /** Saves [link], or forgets the PC when it is null. */
    fun save(link: StationLink?)
}

/** Pairing QR codes: drawn on the PC, read from the phone's camera. */
expect object QrCodes {
    /** [text] as a QR code: rows of dark (true) and light modules, without the quiet border. */
    fun encode(text: String): List<BooleanArray>

    /** The text of a QR code in a grey [width] × [height] image (one byte per pixel), or null. */
    fun decode(luminance: ByteArray, width: Int, height: Int): String?
}

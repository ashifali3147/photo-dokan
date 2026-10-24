package com.tlw.photodokan.ui.station

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/** This phone's name as the PC lists it, e.g. "Samsung Galaxy A15". */
data class PhoneName(val value: String)

/**
 * A pairing link opened from outside the app, e.g. the PC's QR scanned with the phone's own camera
 * app. Holds the latest one until the Print Station settings take it.
 */
class IncomingInvites {
    private val latest = MutableStateFlow<String?>(null)

    val pending: StateFlow<String?> = latest.asStateFlow()

    fun send(link: String) {
        latest.value = link
    }

    /** Returns the waiting link, if any, and clears it so it is used only once. */
    fun take(): String? = latest.getAndUpdate { null }
}

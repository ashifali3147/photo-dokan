package com.tlw.photodokan.ui.photo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * Photos handed to the app from outside, such as a photo shared from another Android app.
 * Holds the latest one until the Customers screen takes it.
 */
class IncomingPhotos {
    private val latest = MutableStateFlow<PhotoSource?>(null)

    val pending: StateFlow<PhotoSource?> = latest.asStateFlow()

    fun send(photo: PhotoSource) {
        latest.value = photo
    }

    /** Returns the waiting photo, if any, and clears it so it is imported only once. */
    fun take(): PhotoSource? = latest.getAndUpdate { null }
}

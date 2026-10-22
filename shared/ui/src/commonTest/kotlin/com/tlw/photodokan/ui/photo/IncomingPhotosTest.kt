package com.tlw.photodokan.ui.photo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class IncomingPhotosTest {

    @Test
    fun photoIsTakenOnlyOnce() {
        val incoming = IncomingPhotos()
        val photo = PhotoSource("shared.jpg") { ByteArray(0) }
        incoming.send(photo)
        assertSame(photo, incoming.pending.value)
        assertSame(photo, incoming.take())
        assertNull(incoming.take())
        assertNull(incoming.pending.value)
    }

    @Test
    fun latestPhotoWins() {
        val incoming = IncomingPhotos()
        incoming.send(PhotoSource("first.jpg") { ByteArray(0) })
        incoming.send(PhotoSource("second.jpg") { ByteArray(0) })
        assertEquals("second.jpg", incoming.take()?.name)
    }
}

package com.tlw.photodokan.ui.station

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.station.PairingInvite
import com.tlw.photodokan.station.QrCodes
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class QrCodeScreenTest {

    private val invite = PairingInvite(
        listOf("192.168.1.5", "10.0.0.2"), 47474,
        "e473de1fd170c260288c9ef462317f75900f7bc02803c88d2a0cf09f42fd9364", "wbaMe9AedH4yYx51cGR6BQ",
    )

    /** The QR as the PC draws it, at its size in the pairing window, reads back as the invite. */
    @Test
    fun theQrOnThePcScreenScans() = runComposeUiTest {
        setContent { QrCode(QrCodes.encode(invite.toUri()), Modifier.size(260.dp)) }
        val image = onRoot().captureToImage().toAwtImage()
        val grey = ByteArray(image.width * image.height) { i ->
            val rgb = image.getRGB(i % image.width, i / image.width)
            (((rgb shr 16 and 0xFF) + (rgb shr 8 and 0xFF) + (rgb and 0xFF)) / 3).toByte()
        }
        assertEquals(invite.toUri(), QrCodes.decode(grey, image.width, image.height))
    }
}

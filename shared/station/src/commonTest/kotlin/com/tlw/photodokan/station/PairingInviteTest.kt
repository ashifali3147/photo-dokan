package com.tlw.photodokan.station

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PairingInviteTest {

    private val fingerprint = "e473de1fd170c260288c9ef462317f75900f7bc02803c88d2a0cf09f42fd9364"
    private val invite = PairingInvite(listOf("192.168.1.5", "10.0.0.2"), 47474, fingerprint, "Zx3k9Qm2Lp7Wv1Rt")

    @Test
    fun anInviteSurvivesTheQr() {
        val uri = invite.toUri()
        assertEquals("photodokan://pair?v=1&h=192.168.1.5,10.0.0.2&p=47474&f=$fingerprint&c=Zx3k9Qm2Lp7Wv1Rt", uri)
        assertEquals(invite, PairingInvite.parse(uri))
        assertEquals(invite, PairingInvite.parse("  ${uri.replace("photodokan", "PhotoDokan")}\n"), "as a camera app may hand it over")
    }

    @Test
    fun otherQrCodesAreNotInvites() {
        for (text in listOf("", "https://example.com", "photodokan://open?x=1", "WIFI:S:Shop;T:WPA;P:secret;;", "photodokan://pair")) {
            assertNull(PairingInvite.parse(text), text)
        }
    }

    @Test
    fun aDamagedOrNewerInviteIsRefused() {
        val uri = invite.toUri()
        assertNull(PairingInvite.parse(uri.replace("v=1", "v=2")), "a newer app's invite")
        assertNull(PairingInvite.parse(uri.replace("&c=Zx3k9Qm2Lp7Wv1Rt", "")), "no code")
        assertNull(PairingInvite.parse(uri.replace(fingerprint, fingerprint.dropLast(2))), "cut-off fingerprint")
        assertNull(PairingInvite.parse(uri.replace("p=47474", "p=0")), "no port")
        assertNull(PairingInvite.parse(uri.replace("c=Zx3k9Qm2Lp7Wv1Rt", "c=short")), "a code too short to be safe")
    }

    @Test
    fun anInviteCanOnlyPointAtTheShopsNetwork() {
        assertNull(PairingInvite.parse(invite.toUri().replace("192.168.1.5,10.0.0.2", "8.8.8.8")))
        assertNull(PairingInvite.parse(invite.toUri().replace("192.168.1.5,10.0.0.2", "evil.example.com")))
        assertFailsWith<IllegalArgumentException> { invite.copy(hosts = emptyList()) }
    }
}

package com.tlw.photodokan.ui.station

import com.tlw.photodokan.station.PairedDevice
import com.tlw.photodokan.station.PairedHello
import com.tlw.photodokan.station.PairingInvite
import com.tlw.photodokan.station.StationConnector
import com.tlw.photodokan.station.StationException
import com.tlw.photodokan.station.StationHost
import com.tlw.photodokan.station.StationLink
import com.tlw.photodokan.station.StationLinkStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StationViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val fingerprint = "e473de1fd170c260288c9ef462317f75900f7bc02803c88d2a0cf09f42fd9364"
    private val invite = PairingInvite(listOf("192.168.1.5"), 47474, fingerprint, "Zx3k9Qm2Lp7Wv1Rt")
    private val link = StationLink("STUDIO-PC", listOf("192.168.1.5"), 47474, fingerprint, "device-1", "token-1")

    /** The PC's station: running or not, on a network or not, with phones pairing as told. */
    private class FakeHost : StationHost {
        override val name = "STUDIO-PC"
        override val running = MutableStateFlow(true)
        override val devices = MutableStateFlow(emptyList<PairedDevice>())
        var invite: PairingInvite? = null
        var closed = 0
        val removed = mutableListOf<String>()
        override fun invite() = invite
        override fun closeInvite() { closed++ }
        override fun remove(deviceId: String) { removed += deviceId; devices.value = devices.value.filterNot { it.id == deviceId } }
        fun phonePairs(name: String) { devices.value = devices.value + PairedDevice("id-$name", name, 0, "hash") }
    }

    /** The phone's client: answers as told, or fails with [failure]. */
    private inner class FakeConnector : StationConnector {
        var failure: StationException? = null
        val paired = mutableListOf<Pair<PairingInvite, String>>()
        override fun pair(invite: PairingInvite, deviceName: String): StationLink {
            failure?.let { throw it }
            paired += invite to deviceName
            return link
        }
        var onCheck: () -> Unit = {}
        override fun check(link: StationLink): PairedHello {
            failure?.let { throw it }
            onCheck()
            return PairedHello(link.stationName, "Galaxy A15")
        }
        override fun send(link: StationLink, pdf: ByteArray, name: String) = throw UnsupportedOperationException()
    }

    private class MemoryLinks(var saved: StationLink? = null) : StationLinkStore {
        override fun load() = saved
        override fun save(link: StationLink?) { saved = link }
    }

    private val host = FakeHost().apply { invite = this@StationViewModelTest.invite }
    private val connector = FakeConnector()
    private val links = MemoryLinks()
    private val invites = IncomingInvites()

    private fun pc() = StationViewModel(host = host, work = Dispatchers.Unconfined)
    private fun phone() = StationViewModel(connector = connector, links = links, deviceName = "Galaxy A15", invites = invites, work = Dispatchers.Unconfined)

    // ---- On the PC ----

    @Test
    fun thePcShowsAQrThatHoldsTheInvite() {
        val vm = pc()
        assertTrue(vm.isHost)
        vm.showInvite()
        val shown = assertNotNull(vm.state.value.invite)
        assertEquals(invite, shown.invite)
        assertTrue(shown.modules.size >= 21, "a QR code")
        vm.closeInvite()
        assertNull(vm.state.value.invite)
        assertEquals(1, host.closed, "the code stops working when the QR closes")
    }

    @Test
    fun aPhonePairingClosesTheQr() {
        val vm = pc()
        host.phonePairs("Old phone")
        vm.showInvite()
        host.phonePairs("Galaxy A15")
        assertNull(vm.state.value.invite)
        assertEquals("Paired with Galaxy A15. It can now send sheets to this PC.", vm.state.value.message)
        assertEquals(listOf("Old phone", "Galaxy A15"), vm.state.value.devices.map { it.name })
    }

    @Test
    fun aRemovedPhoneLeavesTheList() {
        val vm = pc()
        host.phonePairs("Galaxy A15")
        vm.remove(vm.state.value.devices.single())
        assertEquals(listOf("id-Galaxy A15"), host.removed)
        assertEquals(emptyList(), vm.state.value.devices)
        assertEquals("Galaxy A15 was removed. It can't send sheets until it is paired again.", vm.state.value.message)
    }

    @Test
    fun thePcSaysWhyNoQrCanBeShown() {
        host.invite = null
        host.running.value = false
        val vm = pc()
        vm.showInvite()
        assertTrue(vm.state.value.message!!.startsWith("The Print Station isn't running."))
        host.running.value = true
        vm.showInvite()
        assertEquals("This PC isn't on a network phones can reach. Connect it to the shop's Wi-Fi router.", vm.state.value.message)
        assertTrue(vm.state.value.messageIsError)
    }

    // ---- On the phone ----

    @Test
    fun thePhonePairsWithTheScannedQr() {
        val vm = phone()
        assertTrue(vm.isPhone)
        assertFalse(vm.isHost)
        vm.pair(invite.toUri())
        assertEquals(listOf(invite to "Galaxy A15"), connector.paired)
        assertEquals(link, links.saved)
        assertEquals(link, vm.state.value.link)
        assertEquals("Paired with STUDIO-PC. Sheets can now be sent to it.", vm.state.value.message)
    }

    @Test
    fun aLinkOpenedFromTheCameraAppPairsToo() {
        val vm = phone()
        invites.send(invite.toUri())
        assertEquals(link, vm.state.value.link)
        assertNull(invites.pending.value, "used once")
    }

    @Test
    fun anotherQrIsNotAPairingCode() {
        val vm = phone()
        vm.pair("WIFI:S:Shop;T:WPA;P:secret;;")
        assertTrue(connector.paired.isEmpty())
        assertTrue(vm.state.value.message!!.startsWith("That isn't a Photo Dokan pairing code."))
    }

    @Test
    fun aPhoneThatCannotReachThePcSaysSo() {
        connector.failure = StationException("The PC can't be reached.", StationException.Reason.Unreachable)
        val vm = phone()
        vm.pair(invite.toUri())
        assertNull(links.saved)
        assertEquals("The PC can't be reached.", vm.state.value.message)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun thePairedPcIsRememberedAndChecked() {
        links.saved = link
        val vm = phone()
        assertEquals(link, vm.state.value.link)
        vm.check()
        assertEquals("Connected to STUDIO-PC.", vm.state.value.message)
    }

    @Test
    fun aPcFoundAtANewAddressIsKeptThere() {
        links.saved = link
        val vm = phone()
        val moved = link.copy(hosts = listOf("192.168.1.9", "192.168.1.5"))
        connector.onCheck = { links.saved = moved } // as FindingConnector saves it
        vm.check()
        assertEquals(moved, vm.state.value.link)
    }

    @Test
    fun aPhoneRemovedOnThePcIsUnpairedHereToo() {
        links.saved = link
        val vm = phone()
        connector.failure = StationException("This phone isn't paired with the PC. Pair it again in Settings.", StationException.Reason.NotPaired)
        vm.check()
        assertNull(vm.state.value.link)
        assertNull(links.saved)
        assertTrue(vm.state.value.messageIsError)
    }

    @Test
    fun forgettingThePc() {
        links.saved = link
        val vm = phone()
        vm.forget()
        assertNull(links.saved)
        assertNull(vm.state.value.link)
        assertEquals("This phone no longer sends sheets to STUDIO-PC.", vm.state.value.message)
    }
}

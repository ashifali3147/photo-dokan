package com.tlw.photodokan.station

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FindingConnectorTest {

    private val fingerprint = "e473de1fd170c260288c9ef462317f75900f7bc02803c88d2a0cf09f42fd9364"
    private val link = StationLink("STUDIO-PC", listOf("192.168.1.5"), 47474, fingerprint, "device-1", "token-1")

    /** The PC, answering only at [at]; or refusing with [failure]. */
    private class FakePc(var at: String, var failure: StationException? = null) : StationConnector {
        val tried = mutableListOf<List<String>>()
        override fun pair(invite: PairingInvite, deviceName: String) = throw UnsupportedOperationException()
        override fun check(link: StationLink): PairedHello = answer(link) { PairedHello(link.stationName, "Galaxy A15") }
        override fun send(link: StationLink, pdf: ByteArray, name: String): JobResult = answer(link) { JobResult(printed = true, message = "Printed $name.") }
        private fun <T> answer(link: StationLink, ok: () -> T): T {
            tried += link.hosts
            failure?.let { throw it }
            if (at !in link.hosts) throw StationException("The PC can't be reached.", StationException.Reason.Unreachable)
            return ok()
        }
    }

    /** What the shop's network announces, by fingerprint. */
    private class FakeNetwork(val announced: MutableMap<String, List<String>> = mutableMapOf()) : StationFinder {
        var searches = 0
        override fun find(fingerprint: String): List<String> {
            searches++
            return announced[fingerprint].orEmpty()
        }
    }

    private class MemoryLinks(var saved: StationLink?) : StationLinkStore {
        override fun load() = saved
        override fun save(link: StationLink?) { saved = link }
    }

    private val pc = FakePc(at = "192.168.1.5")
    private val network = FakeNetwork()
    private val links = MemoryLinks(link)
    private val connector = FindingConnector(pc, network, links)

    @Test
    fun aPcStillAtItsAddressIsNotSearchedFor() {
        assertEquals("Printed Sheet.", connector.send(link, byteArrayOf(1), "Sheet").message)
        assertEquals(0, network.searches)
        assertEquals(link, links.saved)
    }

    @Test
    fun aPcWithANewAddressIsFoundAndRemembered() {
        pc.at = "192.168.1.9"
        network.announced[fingerprint] = listOf("192.168.1.9")

        assertEquals("Printed Sheet.", connector.send(link, byteArrayOf(1), "Sheet").message)
        assertEquals(listOf(listOf("192.168.1.5"), listOf("192.168.1.9", "192.168.1.5")), pc.tried)
        assertEquals(listOf("192.168.1.9", "192.168.1.5"), links.saved?.hosts, "first next time")
        assertEquals("STUDIO-PC", connector.check(links.saved!!).stationName)
    }

    @Test
    fun anotherPcOnTheNetworkIsNotMistakenForIt() {
        pc.at = "192.168.1.9"
        network.announced["another-pc"] = listOf("192.168.1.9")
        val error = assertFailsWith<StationException> { connector.send(link, byteArrayOf(1), "Sheet") }
        assertEquals(StationException.Reason.Unreachable, error.reason)
        assertEquals(1, pc.tried.size, "nothing sent elsewhere")
        assertEquals(link, links.saved)
    }

    @Test
    fun onlyNewLocalAddressesAreTried() {
        pc.at = "8.8.8.8"
        network.announced[fingerprint] = listOf("192.168.1.5", "8.8.8.8")
        assertFailsWith<StationException> { connector.check(link) }
        assertEquals(1, pc.tried.size, "the old address isn't tried twice, nor one off the shop's network")
    }

    @Test
    fun aPcThatAnsweredIsNotSearchedFor() {
        pc.failure = StationException("This phone isn't paired with the PC.", StationException.Reason.NotPaired)
        assertEquals(StationException.Reason.NotPaired, assertFailsWith<StationException> { connector.check(link) }.reason)
        pc.failure = StationException("The sheet reached the PC, but…", StationException.Reason.NoAnswer)
        assertEquals(StationException.Reason.NoAnswer, assertFailsWith<StationException> { connector.send(link, byteArrayOf(1), "Sheet") }.reason)
        assertEquals(0, network.searches)
    }

    @Test
    fun aSearchThatFailsLeavesTheFirstError() {
        val broken = FindingConnector(FakePc(at = "192.168.1.9"), object : StationFinder {
            override fun find(fingerprint: String): List<String> = throw IllegalStateException("Wi-Fi off")
        }, links)
        assertEquals("The PC can't be reached.", assertFailsWith<StationException> { broken.check(link) }.message)
    }

    @Test
    fun aPcForgottenMeanwhileStaysForgotten() {
        pc.at = "192.168.1.9"
        network.announced[fingerprint] = listOf("192.168.1.9")
        links.saved = null
        connector.check(link)
        assertNull(links.saved)
    }

    @Test
    fun oldAddressesDontPileUp() {
        val many = link.copy(hosts = listOf("192.168.1.5", "192.168.1.6", "192.168.1.7", "192.168.1.8"))
        links.saved = many
        pc.at = "192.168.1.9"
        network.announced[fingerprint] = listOf("192.168.1.9")
        connector.check(many)
        assertEquals(listOf("192.168.1.9", "192.168.1.5", "192.168.1.6", "192.168.1.7"), links.saved?.hosts)
    }
}

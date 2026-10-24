package com.tlw.photodokan.station

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalNetworkTest {

    @Test
    fun theShopsOwnNetworkIsLocal() {
        for (address in listOf("192.168.1.23", "10.0.0.5", "172.16.0.1", "172.31.255.254", "169.254.10.2", "127.0.0.1")) {
            assertTrue(LocalNetwork.isLocal(address), address)
        }
    }

    @Test
    fun theInternetIsNot() {
        for (address in listOf("8.8.8.8", "172.32.0.1", "172.15.255.255", "192.169.0.1", "100.64.0.1", "1.1.1.1")) {
            assertFalse(LocalNetwork.isLocal(address), address)
        }
    }

    @Test
    fun ipv6IsJudgedTheSameWay() {
        for (address in listOf("::1", "[::1]", "fe80::1c2:3ff:fe4d:5e6f%wlan0", "fd12:3456:789a::1", "::ffff:192.168.1.23")) {
            assertTrue(LocalNetwork.isLocal(address), address)
        }
        for (address in listOf("2001:4860:4860::8888", "::ffff:8.8.8.8", "2a00:1450::1")) {
            assertFalse(LocalNetwork.isLocal(address), address)
        }
    }

    @Test
    fun anythingElseIsRefused() {
        for (address in listOf("", "localhost", "192.168.1", "192.168.1.256", "192.168.1.2.3", "a.b.c.d", "::g")) {
            assertFalse(LocalNetwork.isLocal(address), address)
        }
    }
}

package com.tlw.photodokan.station

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The phone's search, against a station announced on the device itself the way the PC announces
 * it. Run with ./gradlew :shared:station:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class NsdStationFinderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val fingerprint = "e473de1fd170c260288c9ef462317f75900f7bc02803c88d2a0cf09f42fd9364"
    private val announced = mutableListOf<NsdManager.RegistrationListener>()

    @After
    fun cleanUp() = announced.forEach { runCatching { nsd.unregisterService(it) } }

    private fun announce(name: String, fingerprint: String) {
        val service = NsdServiceInfo().apply {
            serviceName = name
            serviceType = StationProtocol.SERVICE_TYPE
            port = StationProtocol.PORT
            setAttribute(StationProtocol.SERVICE_VERSION, StationProtocol.VERSION.toString())
            setAttribute(StationProtocol.SERVICE_FINGERPRINT, fingerprint)
        }
        val registered = CountDownLatch(1)
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = registered.countDown()
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        nsd.registerService(service, NsdManager.PROTOCOL_DNS_SD, listener)
        announced += listener
        assertTrue(registered.await(10, TimeUnit.SECONDS), "announced")
    }

    @Test
    fun findsThePairedPcByItsFingerprint() {
        announce("OTHER-PC", "another-pc")
        announce("STUDIO-PC", fingerprint)
        val found = NsdStationFinder(context, waitMillis = 10_000).find(fingerprint)
        assertTrue(found.isNotEmpty(), "found")
        assertTrue(found.all(LocalNetwork::isLocal), found.toString())
    }

    @Test
    fun findsNothingWhenThePcIsntAnnounced() {
        announce("OTHER-PC", "another-pc")
        // A PC nobody announces; the other test's may still be heard for a moment after it ends.
        val missing = "0".repeat(64)
        assertEquals(emptyList(), NsdStationFinder(context, waitMillis = 3_000).find(missing))
    }
}

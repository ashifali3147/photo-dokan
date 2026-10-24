package com.tlw.photodokan.station

import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

/**
 * Announces the Print Station on each of the PC's networks (mDNS), with its certificate
 * fingerprint, so a paired phone finds the PC again when the router gives it a new address. The
 * addresses are checked every [CHECK_SECONDS], e.g. after the Wi-Fi reconnects. Everything runs
 * on one background thread; the station works without it.
 */
internal class StationAnnouncer(
    private val name: String,
    private val fingerprint: String,
    private val addresses: () -> List<String>,
) {
    private val announced = mutableMapOf<String, JmDNS>()
    private var worker: ScheduledExecutorService? = null

    @Synchronized
    fun start(port: Int) {
        if (worker != null) return
        worker = Executors.newSingleThreadScheduledExecutor { Thread(it, "Print Station announcer").apply { isDaemon = true } }.also {
            it.scheduleWithFixedDelay({ announce(port) }, 0, CHECK_SECONDS, TimeUnit.SECONDS)
        }
    }

    /**
     * Stops announcing; phones see the station leave. Waits a moment for that to be sent, as the
     * app closes right after.
     */
    @Synchronized
    fun stop() {
        val running = worker ?: return
        worker = null
        running.execute {
            announced.values.forEach { runCatching { it.unregisterAllServices(); it.close() } }
            announced.clear()
        }
        running.shutdown()
        running.awaitTermination(STOP_SECONDS, TimeUnit.SECONDS)
    }

    /** On the worker thread: announces on new addresses and stops on ones the PC no longer has. */
    private fun announce(port: Int) {
        val now = runCatching(addresses).getOrDefault(emptyList()).toSet()
        (announced.keys - now).forEach { address -> announced.remove(address)?.let { runCatching { it.close() } } }
        (now - announced.keys).forEach { address ->
            try {
                val mdns = JmDNS.create(InetAddress.getByName(address), HOST_NAME)
                mdns.registerService(serviceInfo(port))
                announced[address] = mdns
            } catch (e: Exception) {
                log.log(Level.WARNING, "Could not announce the Print Station on $address", e)
            }
        }
    }

    private fun serviceInfo(port: Int): ServiceInfo = ServiceInfo.create(
        StationProtocol.SERVICE_TYPE + ".local.",
        name,
        port,
        0,
        0,
        mapOf(StationProtocol.SERVICE_VERSION to StationProtocol.VERSION.toString(), StationProtocol.SERVICE_FINGERPRINT to fingerprint),
    )

    private companion object {
        const val CHECK_SECONDS = 30L
        const val STOP_SECONDS = 3L

        /** Its own host name, leaving the PC's to Windows. */
        const val HOST_NAME = "photo-dokan-station"
        val log: Logger = Logger.getLogger(StationAnnouncer::class.java.name)
    }
}

package com.tlw.photodokan.station

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Finds the Print Station with Android's own network service discovery: looks for announced
 * stations for up to [waitMillis], and answers as soon as the one with the paired fingerprint
 * gives its address.
 */
class NsdStationFinder(context: Context, private val waitMillis: Long = 5_000) : StationFinder {
    private val nsd = context.getSystemService(NsdManager::class.java)

    override fun find(fingerprint: String): List<String> {
        val seen = LinkedBlockingQueue<NsdServiceInfo>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(service: NsdServiceInfo) { seen += service }
            override fun onServiceLost(service: NsdServiceInfo) = Unit
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        nsd.discoverServices(StationProtocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        val deadline = System.currentTimeMillis() + waitMillis
        try {
            while (true) {
                val left = deadline - System.currentTimeMillis()
                if (left <= 0) break
                val service = seen.poll(left, TimeUnit.MILLISECONDS) ?: break
                val resolved = resolve(service, deadline - System.currentTimeMillis()) ?: continue
                val announced = resolved.attributes[StationProtocol.SERVICE_FINGERPRINT]?.decodeToString()
                if (announced == fingerprint) return addressesOf(resolved)
            }
        } finally {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
        return emptyList()
    }

    /** [service] with its address and TXT entries, or null when it doesn't answer within [millis]. */
    private fun resolve(service: NsdServiceInfo, millis: Long): NsdServiceInfo? {
        if (millis <= 0) return null
        val answer = ArrayBlockingQueue<NsdServiceInfo>(1)
        return if (Build.VERSION.SDK_INT >= 34) {
            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceUpdated(info: NsdServiceInfo) { answer.offer(info) }
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = Unit
                override fun onServiceLost() = Unit
                override fun onServiceInfoCallbackUnregistered() = Unit
            }
            nsd.registerServiceInfoCallback(service, Executor(Runnable::run), callback)
            try {
                answer.poll(millis, TimeUnit.MILLISECONDS)
            } finally {
                runCatching { nsd.unregisterServiceInfoCallback(callback) }
            }
        } else {
            @Suppress("DEPRECATION")
            nsd.resolveService(service, object : NsdManager.ResolveListener {
                override fun onServiceResolved(info: NsdServiceInfo) { answer.offer(info) }
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            })
            answer.poll(millis, TimeUnit.MILLISECONDS)
        }
    }

    private fun addressesOf(service: NsdServiceInfo): List<String> {
        val addresses: List<InetAddress> = if (Build.VERSION.SDK_INT >= 34) {
            service.hostAddresses
        } else {
            @Suppress("DEPRECATION")
            listOfNotNull(service.host)
        }
        return addresses.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }
    }
}

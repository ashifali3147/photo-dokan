package com.tlw.photodokan.station

/**
 * Whether an address belongs to this machine or the shop's own network: loopback, the private
 * IPv4 ranges (10/8, 172.16/12, 192.168/16), link-local, and IPv6 unique-local and link-local.
 * The Print Station refuses everything else, so even a router that forwards the port by mistake
 * doesn't put it on the internet.
 */
object LocalNetwork {

    fun isLocal(address: String): Boolean {
        val host = address.trim().removePrefix("[").removeSuffix("]").substringBefore('%')
        ipv4(host)?.let { return isLocalIpv4(it) }
        return isLocalIpv6(host.lowercase())
    }

    private fun isLocalIpv4(b: IntArray): Boolean = when {
        b[0] == 127 -> true // loopback
        b[0] == 10 -> true
        b[0] == 172 && b[1] in 16..31 -> true
        b[0] == 192 && b[1] == 168 -> true
        b[0] == 169 && b[1] == 254 -> true // link-local, e.g. a phone's own hotspot without DHCP
        else -> false
    }

    private fun isLocalIpv6(host: String): Boolean {
        if (!host.contains(':')) return false
        if (host == "::1") return true
        // An IPv4 address carried in IPv6, as a dual-stack server reports IPv4 callers.
        if (host.startsWith("::ffff:")) return ipv4(host.removePrefix("::ffff:"))?.let(::isLocalIpv4) ?: false
        val first = host.substringBefore(':').ifEmpty { return false }.toIntOrNull(16) ?: return false
        return first and 0xfe00 == 0xfc00 || // unique-local fc00::/7
            first and 0xffc0 == 0xfe80 // link-local fe80::/10
    }

    /** The four parts of a dotted IPv4 address, or null when [host] isn't one. */
    private fun ipv4(host: String): IntArray? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        return IntArray(4) { i -> parts[i].takeIf { it.isNotEmpty() && it.length <= 3 && it.all(Char::isDigit) }?.toInt()?.takeIf { it <= 255 } ?: return null }
    }
}

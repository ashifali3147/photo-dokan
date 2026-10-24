package com.tlw.photodokan.station

/**
 * What the PC's pairing QR holds: where the station is ([hosts], [port]), the [fingerprint] of its
 * certificate so the phone trusts only this PC, and a one-time [code]. As a link,
 * `photodokan://pair?v=1&h=192.168.1.5,10.0.0.2&p=47474&f=<sha-256 hex>&c=<code>`, so the phone's
 * own camera app can open it in Photo Dokan as well as the app's scanner.
 */
data class PairingInvite(
    /** The PC's addresses on its local networks, most likely first. */
    val hosts: List<String>,
    val port: Int,
    val fingerprint: String,
    val code: String,
) {
    init {
        require(hosts.isNotEmpty() && hosts.all(::isHost)) { "Hosts must be local IPv4 addresses: $hosts" }
        require(port in 1..65535) { "Port out of range: $port" }
        require(FINGERPRINT.matches(fingerprint)) { "Fingerprint must be 64 lowercase hex digits" }
        require(CODE.matches(code)) { "Code must be 16 to 64 letters, digits, '-' or '_'" }
    }

    fun toUri(): String = "$SCHEME://$HOST?v=${StationProtocol.VERSION}&h=${hosts.joinToString(",")}&p=$port&f=$fingerprint&c=$code"

    companion object {
        const val SCHEME = "photodokan"
        const val HOST = "pair"

        private val FINGERPRINT = Regex("[0-9a-f]{64}")
        private val CODE = Regex("[A-Za-z0-9_-]{16,64}")

        /**
         * The invite in a scanned QR or opened link, or null when it isn't a Photo Dokan pairing
         * link this app understands (another QR, a damaged one, or one from a newer version).
         */
        fun parse(text: String): PairingInvite? {
            val prefix = "$SCHEME://$HOST?"
            val trimmed = text.trim()
            if (!trimmed.startsWith(prefix, ignoreCase = true)) return null
            val fields = trimmed.substring(prefix.length).split('&').mapNotNull {
                val key = it.substringBefore('=', missingDelimiterValue = "")
                if (key.isEmpty()) null else key to it.substringAfter('=')
            }.toMap()
            if (fields["v"] != StationProtocol.VERSION.toString()) return null
            return runCatching {
                PairingInvite(
                    hosts = fields.getValue("h").split(',').filter { it.isNotEmpty() },
                    port = fields.getValue("p").toInt(),
                    fingerprint = fields.getValue("f").lowercase(),
                    code = fields.getValue("c"),
                )
            }.getOrNull()
        }

        /** Only addresses on the shop's network: a QR can't send the phone anywhere else. */
        private fun isHost(host: String) = host.count { it == '.' } == 3 && LocalNetwork.isLocal(host)
    }
}

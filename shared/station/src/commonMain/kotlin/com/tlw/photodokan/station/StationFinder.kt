package com.tlw.photodokan.station

/** Searches the shop's network for the Print Station's announcement. Blocking: call it off the main thread. */
interface StationFinder {
    /** The addresses of the PC announcing [fingerprint], or none when it isn't found within a few seconds. */
    fun find(fingerprint: String): List<String>
}

/**
 * The phone's side of the Print Station, finding the paired PC again when it doesn't answer at the
 * addresses it had, e.g. after the router gave the PC a new one. The PC is recognised by its
 * certificate's fingerprint, never by name, and the new address is kept for next time.
 */
class FindingConnector(
    private val connector: StationConnector,
    private val finder: StationFinder,
    private val links: StationLinkStore,
) : StationConnector {

    override fun pair(invite: PairingInvite, deviceName: String): StationLink = connector.pair(invite, deviceName)

    override fun check(link: StationLink): PairedHello = withFinding(link) { connector.check(it) }

    override fun send(link: StationLink, pdf: ByteArray, name: String): JobResult = withFinding(link) { connector.send(it, pdf, name) }

    private fun <T> withFinding(link: StationLink, call: (StationLink) -> T): T {
        try {
            return call(link)
        } catch (e: StationException) {
            if (e.reason != StationException.Reason.Unreachable) throw e
            // Only addresses not tried yet: the PC didn't answer at those.
            val found = runCatching { finder.find(link.fingerprint) }.getOrDefault(emptyList())
                .filter { LocalNetwork.isLocal(it) && it !in link.hosts }
            if (found.isEmpty()) throw e
            val moved = link.copy(hosts = (found + link.hosts).distinct().take(MAX_HOSTS))
            val result = call(moved)
            // Unless the PC was forgotten meanwhile.
            runCatching { if (links.load()?.deviceId == link.deviceId) links.save(moved) }
            return result
        }
    }

    private companion object {
        /** Old addresses are kept in case the PC goes back to one, but not forever. */
        const val MAX_HOSTS = 4
    }
}

package com.tlw.photodokan.station

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import java.util.logging.Level
import java.util.logging.Logger

/**
 * The Print Station on the studio PC: an HTTPS server on the shop's network that phones send
 * sheets to. It runs while the app runs; [start] and [stop] are safe to call more than once.
 *
 * @param name the PC's name as phones show it.
 * @param port [StationProtocol.PORT]; tests pass 0 for any free port.
 */
class PrintStation(
    private val certificate: StationCertificate,
    private val name: String,
    private val port: Int = StationProtocol.PORT,
) {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    /** The port the station listens on while running, or null when it is stopped. */
    @Volatile
    var listeningPort: Int? = null
        private set

    /**
     * Starts listening on every network of this PC. Throws when the port can't be used, e.g. when
     * another copy of the app is already running.
     */
    @Synchronized
    fun start() {
        if (server != null) return
        val password = StationCertificate.PASSWORD.toCharArray()
        val started = embeddedServer(
            Netty,
            configure = {
                sslConnector(certificate.keyStore, StationCertificate.ALIAS, { password }, { password }) {
                    host = "0.0.0.0"
                    port = this@PrintStation.port
                }
            },
            module = { station(name) },
        )
        try {
            started.start(wait = false)
        } catch (e: Exception) {
            started.stop(0, 0)
            throw e
        }
        server = started
        listeningPort = runBlocking { started.engine.resolvedConnectors().first().port }
        log.info("Print Station listening on port $listeningPort")
    }

    @Synchronized
    fun stop() {
        val running = server ?: return
        server = null
        listeningPort = null
        running.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
    }

    companion object {
        private val log = Logger.getLogger(PrintStation::class.java.name)

        /** The PC's name as Windows shows it, e.g. STUDIO-PC. */
        fun computerName(): String =
            System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }
                ?: runCatching { java.net.InetAddress.getLocalHost().hostName.substringBefore('.') }.getOrNull()
                ?: "Studio PC"

        /** Starts [station], logging instead of failing when it can't: the app works without it. */
        fun startQuietly(station: PrintStation): Boolean = try {
            station.start()
            true
        } catch (e: Exception) {
            log.log(Level.WARNING, "The Print Station could not start", e)
            false
        }
    }
}

/**
 * What the station answers. Callers [allowed] by address only: by default this PC and the shop's
 * own network (see [LocalNetwork]); everyone else gets 403 Forbidden and nothing more.
 */
internal fun Application.station(name: String, allowed: (String) -> Boolean = LocalNetwork::isLocal) {
    install(
        createApplicationPlugin("LocalNetworkOnly") {
            onCall { call ->
                if (!allowed(call.request.origin.remoteAddress)) call.respond(HttpStatusCode.Forbidden)
            }
        },
    )
    install(ContentNegotiation) { json() }
    routing {
        get(StationProtocol.STATUS_PATH) { call.respond(StationStatus(name = name)) }
    }
}

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
import io.ktor.server.request.contentLength
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.logging.Level
import java.util.logging.Logger

/**
 * The Print Station on the studio PC: an HTTPS server on the shop's network that phones send
 * sheets to. It runs while the app runs; [start] and [stop] are safe to call more than once.
 *
 * @param name the PC's name as phones show it.
 * @param pairing the paired phones, and the code a new phone pairs with.
 * @param jobs prints the sheets phones send; without it, jobs are refused.
 * @param port [StationProtocol.PORT]; tests pass 0 for any free port.
 */
class PrintStation(
    private val certificate: StationCertificate,
    override val name: String,
    val pairing: StationPairing,
    private val jobs: StationJobs? = null,
    private val port: Int = StationProtocol.PORT,
    private val addresses: () -> List<String> = StationPairing::localAddresses,
) : StationHost {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    private val _running = MutableStateFlow(false)
    override val running: StateFlow<Boolean> = _running.asStateFlow()

    override val devices: StateFlow<List<PairedDevice>> get() = pairing.devices.devices

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
            module = { station(name, pairing, jobs) },
        )
        try {
            started.start(wait = false)
        } catch (e: Exception) {
            started.stop(0, 0)
            throw e
        }
        server = started
        listeningPort = runBlocking { started.engine.resolvedConnectors().first().port }
        _running.value = true
        log.info("Print Station listening on port $listeningPort")
    }

    /**
     * Opens pairing and returns what the QR should hold, or null when the station isn't running or
     * the PC has no address phones could reach (no network).
     */
    override fun invite(): PairingInvite? {
        val port = listeningPort ?: return null
        val hosts = addresses().ifEmpty { return null }
        return PairingInvite(hosts, port, certificate.fingerprint, pairing.newCode())
    }

    override fun closeInvite() = pairing.close()

    override fun remove(deviceId: String) = pairing.devices.remove(deviceId)

    @Synchronized
    fun stop() {
        val running = server ?: return
        server = null
        listeningPort = null
        _running.value = false
        pairing.close()
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
internal fun Application.station(
    name: String,
    pairing: StationPairing,
    jobs: StationJobs? = null,
    allowed: (String) -> Boolean = LocalNetwork::isLocal,
) {
    // One sheet at a time: the printer takes them in turn anyway, and the PC stays responsive.
    val printing = Mutex()
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

        post(StationProtocol.PAIR_PATH) {
            val request = runCatching { call.receive<PairRequest>() }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, StationError("The phone sent a request the PC can't read. Update Photo Dokan on both."))
            pairing.pair(request.code, request.deviceName).fold(
                onSuccess = { (device, token) -> call.respond(PairResponse(device.id, token, name)) },
                onFailure = { call.respond(HttpStatusCode.Forbidden, StationError(it.message ?: "Pairing was refused.")) },
            )
        }

        get(StationProtocol.PAIRED_PATH) {
            val device = pairedDevice(pairing.devices) ?: return@get
            call.respond(PairedHello(name, device.name))
        }

        post(StationProtocol.JOBS_PATH) {
            val device = pairedDevice(pairing.devices) ?: return@post
            val printer = jobs ?: return@post call.respond(HttpStatusCode.ServiceUnavailable, StationError("This PC can't print sheets from phones."))
            val length = call.request.contentLength()
                ?: return@post call.respond(HttpStatusCode.LengthRequired, StationError("The phone sent a sheet the PC can't read. Update Photo Dokan on both."))
            if (length > StationProtocol.MAX_JOB_BYTES) {
                return@post call.respond(HttpStatusCode.PayloadTooLarge, StationError("This sheet is too big to send. Use Share PDF instead."))
            }
            val pdf = call.receive<ByteArray>()
            val sheetName = call.request.queryParameters[StationProtocol.JOB_NAME]?.takeIf { it.isNotBlank() } ?: "Sheet from ${device.name}"
            val result = printing.withLock { withContext(Dispatchers.IO) { printer.print(pdf, sheetName, device.name) } }
            call.respond(result)
        }
    }
}

/**
 * The paired phone making this request, by the token it sends. Anyone else is answered with
 * 401 and the route stops there (null).
 */
private suspend fun RoutingContext.pairedDevice(devices: PairedDevices): PairedDevice? {
    val token = call.request.headers[StationProtocol.AUTHORIZATION]?.takeIf { it.startsWith(StationProtocol.BEARER) }?.removePrefix(StationProtocol.BEARER)
    val device = token?.let(devices::find)
    if (device == null) call.respond(HttpStatusCode.Unauthorized, StationError("This phone isn't paired with the PC. Pair it again in Settings."))
    return device
}

package com.tlw.photodokan.ui.station

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tlw.photodokan.station.PairedDevice
import com.tlw.photodokan.station.PairingInvite
import com.tlw.photodokan.station.QrCodes
import com.tlw.photodokan.station.StationConnector
import com.tlw.photodokan.station.StationException
import com.tlw.photodokan.station.StationHost
import com.tlw.photodokan.station.StationLink
import com.tlw.photodokan.station.StationLinkStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The pairing QR on the PC's screen. */
class ShownInvite(val invite: PairingInvite, val modules: List<BooleanArray>)

data class StationState(
    // On the PC
    val running: Boolean = false,
    val devices: List<PairedDevice> = emptyList(),
    val invite: ShownInvite? = null,
    // On the phone
    val link: StationLink? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
)

/**
 * The Print Station part of Settings. On the PC ([host]): whether phones can reach it, the pairing
 * QR, and the paired phones. On the phone ([connector] and [links]): which PC it is paired with,
 * pairing by a scanned QR or opened link, checking the connection, and forgetting the PC.
 */
class StationViewModel(
    private val host: StationHost? = null,
    private val connector: StationConnector? = null,
    private val links: StationLinkStore? = null,
    private val deviceName: String = "Phone",
    invites: IncomingInvites? = null,
    private val work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    val isHost: Boolean get() = host != null
    val isPhone: Boolean get() = connector != null && links != null

    private val _state = MutableStateFlow(StationState())
    val state: StateFlow<StationState> = _state.asStateFlow()

    init {
        host?.let { host ->
            viewModelScope.launch { host.running.collect { running -> _state.update { it.copy(running = running) } } }
            viewModelScope.launch { host.devices.collect(::devicesChanged) }
        }
        links?.let { links ->
            viewModelScope.launch {
                val link = withContext(work) { links.load() }
                _state.update { it.copy(link = link) }
            }
        }
        invites?.let { incoming ->
            viewModelScope.launch { incoming.pending.filterNotNull().collect { incoming.take()?.let(::pair) } }
        }
    }

    // ---- On the PC ----

    /** Shows the QR a phone scans to pair; it works once, for a few minutes. */
    fun showInvite() {
        val host = host ?: return
        val invite = host.invite()
        if (invite == null) {
            return error(
                if (!_state.value.running) {
                    "The Print Station isn't running. Close any other copy of Photo Dokan, then close and open this one again."
                } else {
                    "This PC isn't on a network phones can reach. Connect it to the shop's Wi-Fi router."
                },
            )
        }
        _state.update { it.copy(invite = ShownInvite(invite, QrCodes.encode(invite.toUri())), message = null) }
    }

    fun closeInvite() {
        host?.closeInvite()
        _state.update { it.copy(invite = null) }
    }

    fun remove(device: PairedDevice) {
        host?.remove(device.id)
        _state.update { it.copy(message = "${device.name} was removed. It can't send sheets until it is paired again.", messageIsError = false) }
    }

    /** A phone that paired while the QR was shown closes it. */
    private fun devicesChanged(devices: List<PairedDevice>) {
        val before = _state.value.devices.map { it.id }.toSet()
        val added = devices.lastOrNull { it.id !in before }
        _state.update { it.copy(devices = devices) }
        if (added != null && _state.value.invite != null) {
            _state.update { it.copy(invite = null, message = "Paired with ${added.name}. It can now send sheets to this PC.", messageIsError = false) }
        }
    }

    // ---- On the phone ----

    /** Pairs with the PC whose QR said [text], scanned in the app or opened from the phone's camera. */
    fun pair(text: String) {
        val connector = connector ?: return
        val links = links ?: return
        val invite = PairingInvite.parse(text)
            ?: return error("That isn't a Photo Dokan pairing code. On the PC, open Settings, press Pair a phone and scan the code it shows.")
        launchWork {
            val link = withContext(work) { connector.pair(invite, deviceName).also(links::save) }
            _state.update { it.copy(link = link, message = "Paired with ${link.stationName}. Sheets can now be sent to it.", messageIsError = false) }
        }
    }

    /** Asks the paired PC whether it still answers and still knows this phone. */
    fun check() {
        val connector = connector ?: return
        val link = _state.value.link ?: return
        launchWork {
            val hello = withContext(work) { connector.check(link) }
            // The PC may have been found at a new address, now saved.
            val current = withContext(work) { links?.load() } ?: link
            _state.update { it.copy(link = current, message = "Connected to ${hello.stationName}.", messageIsError = false) }
        }
    }

    /** Forgets the PC on this phone. The PC still lists the phone until it is removed there. */
    fun forget() {
        val links = links ?: return
        val link = _state.value.link ?: return
        viewModelScope.launch {
            withContext(work) { links.save(null) }
            _state.update { it.copy(link = null, message = "This phone no longer sends sheets to ${link.stationName}.", messageIsError = false) }
        }
    }

    private fun launchWork(block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: StationException) {
                if (e.reason == StationException.Reason.NotPaired) {
                    // The PC removed this phone: pairing again is the only way forward.
                    withContext(work) { links?.save(null) }
                    _state.update { it.copy(link = null) }
                }
                error(e.message ?: "The PC refused.")
            } catch (e: Exception) {
                error("Something went wrong: ${e.message ?: e::class.simpleName}")
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun error(text: String) = _state.update { it.copy(message = text, messageIsError = true) }
}

package com.tlw.photodokan.ui.station

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.tlw.photodokan.ui.components.Notice
import com.tlw.photodokan.ui.components.PrimaryButton
import com.tlw.photodokan.ui.components.QuietButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.station.PairedDevice
import org.koin.compose.viewmodel.koinViewModel

/** The Print Station section of Settings, for whichever side of it this device is. */
@Composable
fun StationSettings(viewModel: StationViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    when {
        viewModel.isHost -> HostSettings(state, viewModel)
        viewModel.isPhone -> PhoneSettings(state, viewModel)
    }
}

@Composable
private fun HostSettings(state: StationState, viewModel: StationViewModel) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("Print from the phone", style = MaterialTheme.typography.titleLarge)
    Text(
        if (state.running) "Phones on the shop's Wi-Fi can send sheets to this PC." else "Not running: phones can't send sheets to this PC.",
        style = MaterialTheme.typography.bodyLarge,
    )
    QuietButton(onClick = viewModel::showInvite) { Text("Pair a phone") }
    Message(state)

    var removing by remember { mutableStateOf<PairedDevice?>(null) }
    Text("Paired phones", style = MaterialTheme.typography.titleMedium)
    if (state.devices.isEmpty()) Text("None yet.", style = MaterialTheme.typography.bodyLarge)
    for (device in state.devices) {
        Row(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(device.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { removing = device }) { Text("Remove") }
        }
        HorizontalDivider(Modifier.widthIn(max = 520.dp))
    }

    removing?.let { device ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${device.name}?") },
            text = { Text("It won't be able to send sheets to this PC until it is paired again.") },
            confirmButton = { TextButton(onClick = { viewModel.remove(device); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep") } },
        )
    }
    state.invite?.let { shown ->
        AlertDialog(
            onDismissRequest = viewModel::closeInvite,
            title = { Text("Pair a phone") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    QrCode(shown.modules, Modifier.size(260.dp).semantics { contentDescription = "Pairing code" })
                    Text(
                        "On the phone, open Photo Dokan → Settings → Pair with the PC and scan this code. " +
                            "The phone's own camera can scan it too. It works once, for 10 minutes.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            },
            confirmButton = { TextButton(onClick = viewModel::closeInvite) { Text("Close") } },
        )
    }
}

@Composable
private fun PhoneSettings(state: StationState, viewModel: StationViewModel) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("Print from the phone", style = MaterialTheme.typography.titleLarge)
    val scan = rememberQrScanner(viewModel::pair)
    val link = state.link
    if (link == null) {
        Text("Pair this phone with the studio PC to send sheets to it over the shop's Wi-Fi.", style = MaterialTheme.typography.bodyLarge)
        PrimaryButton(onClick = { scan?.invoke() }, enabled = scan != null && !state.busy) { Text("Pair with the PC") }
    } else {
        Text("Paired with ${link.stationName}.", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuietButton(onClick = viewModel::check, enabled = !state.busy) { Text("Check connection") }
            TextButton(onClick = viewModel::forget, enabled = !state.busy) { Text("Forget this PC") }
        }
    }
    if (state.busy) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("Talking to the PC…")
        }
    }
    Message(state)
}

@Composable
private fun Message(state: StationState) {
    state.message?.let { Notice(it, state.messageIsError) }
}

/** Dark modules on white, with the white border of four modules scanners need around a QR code. */
@Composable
fun QrCode(modules: List<BooleanArray>, modifier: Modifier = Modifier) = Canvas(modifier.aspectRatio(1f)) {
    val border = 4
    val cell = size.width / (modules.size + 2 * border)
    drawRect(Color.White)
    modules.forEachIndexed { y, row ->
        row.forEachIndexed { x, dark ->
            // A hair over a cell, so no light seams show between neighbours.
            if (dark) drawRect(Color.Black, Offset((x + border) * cell, (y + border) * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

/**
 * Opens the camera to scan a pairing QR and hands its text to [onScanned]; null where there is no
 * camera scanner (the PC shows the code instead).
 */
@Composable
expect fun rememberQrScanner(onScanned: (String) -> Unit): (() -> Unit)?

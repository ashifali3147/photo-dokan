package com.tlw.photodokan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.model.SheetTemplate
import com.tlw.photodokan.print.PhotoSettings
import com.tlw.photodokan.ui.components.Card
import com.tlw.photodokan.ui.components.StudioBackground
import com.tlw.photodokan.ui.components.riseIn
import com.tlw.photodokan.ui.print.PrintViewModel
import com.tlw.photodokan.ui.print.PrinterSettings
import com.tlw.photodokan.ui.station.StationSettings
import com.tlw.photodokan.ui.station.StationViewModel
import com.tlw.photodokan.ui.theme.AppearanceSettings
import com.tlw.photodokan.ui.theme.StudioTheme
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Everything set up once and rarely touched again, one card each: the printer, the sheet, printing
 * from the phone, and how the app looks. A card with nothing to say on this device is left out.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val c = StudioTheme.colors
    val print: PrintViewModel = koinViewModel()
    val printState by print.state.collectAsState()
    // The printer and its saved settings are only known once asked for, so ask on opening Settings.
    LaunchedEffect(print) { print.refresh() }
    val station: StationViewModel = koinViewModel()
    StudioBackground(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 880.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineLarge, color = c.text)
                // A phone has no printer of its own: it sends sheets to the PC, set up below.
                if (print.hasPrinter && printState.photoSettings != PhotoSettings.NotSupported) Section(0) { PrinterSettings(print) }
                Section(1) { SheetSettings() }
                if (station.isHost || station.isPhone) Section(2) { StationSettings(station) }
                Section(3) { AppearanceSettings() }
            }
        }
    }
}

/** A white card that rises in after the ones above it. */
@Composable
private fun Section(index: Int, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().riseIn(index)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** The sheet the app lays photos out on. Fixed for now; it says what the studio prints. */
@Composable
private fun SheetSettings(template: SheetTemplate = koinInject()) {
    val c = StudioTheme.colors
    Text("Sheet", style = MaterialTheme.typography.titleLarge, color = c.text)
    Text(
        "${template.name}: ${template.capacity} photos of ${template.photo.width.mm()} × ${template.photo.height.mm()} mm on " +
            "${template.paper.width.mm()} × ${template.paper.height.mm()} mm paper.",
        style = MaterialTheme.typography.bodyLarge,
        color = c.textMuted,
    )
}

private fun Double.mm(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()

package com.tlw.photodokan.ui.print

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.print.CalibrationSheet
import com.tlw.photodokan.print.PhotoSettings
import com.tlw.photodokan.print.ToneStrip
import com.tlw.photodokan.ui.components.Notice
import com.tlw.photodokan.ui.components.PrimaryButton
import com.tlw.photodokan.ui.components.QuietButton
import com.tlw.photodokan.ui.components.StatusDot
import com.tlw.photodokan.ui.platform.imageBitmapOf
import com.tlw.photodokan.ui.theme.StudioTheme
import org.koin.compose.viewmodel.koinViewModel

/**
 * The printer section of Settings: photo paper and quality saved once, then the size correction
 * and print colours measured on that paper, each with its window. Nothing where this device
 * doesn't print itself (a phone sends sheets to the PC instead). The caller refreshes [viewModel]
 * so the printer's saved settings are current.
 */
@Composable
fun PrinterSettings(viewModel: PrintViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    if (!viewModel.hasPrinter || state.photoSettings == PhotoSettings.NotSupported) return
    val c = StudioTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Printer", style = MaterialTheme.typography.titleLarge, color = c.text)
        PrinterChecklist(state, viewModel)
        state.message?.let { Notice(it, state.messageIsError) }
    }
    if (state.calibrating) CalibrationDialog(state, viewModel)
    if (state.matchingTone) ToneDialog(state, viewModel)
}

/** Whether photo paper and quality are saved, and the size and colour corrections measured with them. */
@Composable
private fun PrinterChecklist(state: PrintState, viewModel: PrintViewModel) {
    val c = StudioTheme.colors
    val idle = !state.printing && !state.choosingSettings
    val saved = state.photoSettings == PhotoSettings.Saved
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(state.printerName ?: "Printer", style = MaterialTheme.typography.titleMedium, color = c.text)
        PrinterRow(
            if (saved) "Photo paper settings saved" else "Photo paper settings not saved yet",
            good = saved,
            action = if (state.choosingSettings) "Settings open…" else "Photo settings",
            enabled = idle,
            onClick = viewModel::choosePhotoSettings,
        )
        // Measured on the photo paper, so offered once the photo settings are saved.
        state.calibration?.let {
            PrinterRow("Size correction: ${it.describe().replaceFirstChar { ch -> ch.lowercase() }}", good = !it.isNone, action = "Calibrate", enabled = idle, onClick = viewModel::openCalibration)
        }
        state.tone?.let {
            PrinterRow("Print colours: ${it.describe().replaceFirstChar { ch -> ch.lowercase() }}", good = !it.isNone, action = "Match colours", enabled = idle, onClick = viewModel::openTone)
        }
    }
}

@Composable
private fun PrinterRow(text: String, good: Boolean, action: String, enabled: Boolean, onClick: () -> Unit) {
    val c = StudioTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(if (good) c.ok else c.warn)
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = c.text)
        QuietButton(onClick, enabled = enabled) { Text(action) }
    }
}

/** Print the colour strip, hold it next to the patch on screen, and type in the number that matches. */
@Composable
private fun ToneDialog(state: PrintState, viewModel: PrintViewModel) {
    var number by remember { mutableStateOf("") }
    val chart = remember { imageBitmapOf(ToneStrip.chart(), ToneStrip.CHART_PX, ToneStrip.CHART_PX) }
    AlertDialog(
        onDismissRequest = viewModel::closeTone,
        title = { Text("Match print colours to the screen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Put one sheet of photo paper in the printer and print the colour strip.")
                QuietButton(onClick = viewModel::printToneStrip, enabled = !state.printing) {
                    Text(if (state.printing) "Printing…" else "Print colour strip")
                }
                Text("2. Hold the strip next to this patch. Which numbered patch looks most like it?")
                Image(chart, contentDescription = "The colour patch to match", modifier = Modifier.size(140.dp))
                OutlinedTextField(
                    value = number,
                    onValueChange = { text -> number = text.filter { it.isDigit() }.take(2) },
                    label = { Text("Patch number (1 to ${ToneStrip.PATCHES})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.toneError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.tone?.isNone == false) {
                    TextButton(onClick = viewModel::removeTone, contentPadding = PaddingValues(0.dp)) { Text("Print colours as on screen (no change)") }
                }
            }
        },
        confirmButton = {
            PrimaryButton(onClick = { viewModel.saveTone(number) }, enabled = !state.printing) { Text("Save colours") }
        },
        dismissButton = { TextButton(onClick = viewModel::closeTone) { Text("Cancel") } },
    )
}

/** Print the calibration sheet, measure it with a ruler, and type in the four numbers. */
@Composable
private fun CalibrationDialog(state: PrintState, viewModel: PrintViewModel) {
    val square = CalibrationSheet.SQUARE
    var width by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var left by remember { mutableStateOf("") }
    var top by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = viewModel::closeCalibration,
        title = { Text("Calibrate the printer") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Put one sheet of photo paper in the printer and print the calibration sheet.")
                QuietButton(onClick = viewModel::printCalibrationSheet, enabled = !state.printing) {
                    Text(if (state.printing) "Printing…" else "Print calibration sheet")
                }
                Text("2. Measure the big square on it with a steel ruler, and type the numbers in mm.")
                MeasurementField("Square width (should be ${square.width.toInt()})", width) { width = it }
                MeasurementField("Square height (should be ${square.height.toInt()})", height) { height = it }
                MeasurementField("Left edge of paper to square (should be ${square.x.toInt()})", left) { left = it }
                MeasurementField("Top edge of paper to square (should be ${square.y.toInt()})", top) { top = it }
                state.calibrationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.calibration?.isNone == false) {
                    TextButton(onClick = viewModel::removeCalibration, contentPadding = PaddingValues(0.dp)) { Text("Remove the saved correction") }
                }
            }
        },
        confirmButton = {
            PrimaryButton(onClick = { viewModel.saveCalibration(width, height, left, top) }, enabled = !state.printing) { Text("Save correction") }
        },
        dismissButton = { TextButton(onClick = viewModel::closeCalibration) { Text("Cancel") } },
    )
}

@Composable
private fun MeasurementField(label: String, value: String, onChange: (String) -> Unit) = OutlinedTextField(
    value = value,
    onValueChange = { text -> onChange(text.filter { it.isDigit() || it == '.' || it == ',' }.take(6)) },
    label = { Text(label) },
    suffix = { Text("mm") },
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    modifier = Modifier.fillMaxWidth(),
)


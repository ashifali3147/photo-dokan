package com.tlw.photodokan.ui.print

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.model.Order
import com.tlw.photodokan.core.model.QueuedOrder
import com.tlw.photodokan.print.CalibrationSheet
import com.tlw.photodokan.print.PhotoSettings
import com.tlw.photodokan.ui.SheetPreview
import com.tlw.photodokan.ui.orderColor
import org.koin.compose.viewmodel.koinViewModel

/** The print queue next to a to-scale preview of where each copy lands on the sheet. */
@Composable
fun PrintScreen(modifier: Modifier = Modifier, viewModel: PrintViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()

    // A customer's crop, or the default printer, may have changed since this screen was last shown.
    LaunchedEffect(viewModel) { viewModel.refresh() }

    BoxWithConstraints(modifier.fillMaxSize().padding(24.dp)) {
        if (maxWidth >= 720.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.width(340.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Queue(state, viewModel)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Sheets(state, viewModel)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { Queue(state, viewModel) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { Sheets(state, viewModel) }
            }
        }
    }
}

@Composable
private fun ColumnScope.Queue(state: PrintState, viewModel: PrintViewModel) {
    val queue = state.queue
    Text("Print", style = MaterialTheme.typography.headlineMedium)
    Text(
        if (queue.orders.isEmpty()) "Nothing to print. Add customers from the Customers screen."
        else "${count(queue.copies, "photo")} for ${count(queue.orders.size, "customer")} · ${count(state.pack.sheets.size, "sheet")}",
        style = MaterialTheme.typography.bodyLarge,
    )

    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
        items(queue.orders, key = { it.id }) { order ->
            OrderRow(order, color = orderColor(queue.orders.indexOf(order)), viewModel)
            HorizontalDivider()
        }
    }

    if (state.printing) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(if (viewModel.hasPrinter) "Printing on ${state.printerName ?: "the printer"}…" else "Making the PDF…")
        }
    }
    state.message?.let { Text(it, color = if (state.messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }

    if (viewModel.hasPrinter && state.photoSettings != PhotoSettings.NotSupported) PhotoSettingsRow(state, viewModel)
    if (state.calibrating) CalibrationDialog(state, viewModel)

    val pickPdf = if (viewModel.canPrintPdf) rememberPdfPicker(viewModel::openPdf) else null
    state.pendingPdf?.let { pending ->
        ConfirmDialog(
            title = "Print \"${pending.name}\"?",
            text = "${count(pending.pages, "page")} from the phone. Put in the sheet the phone asked for, the same way round as last time. " +
                "This PC's own queue and sheet don't change.",
            action = "Print",
            onConfirm = viewModel::printPdf,
            onDismiss = viewModel::cancelPdf,
        )
    }

    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val idle = !state.printing && !state.choosingSettings
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (viewModel.hasPrinter) {
            Button(onClick = { confirm = Confirm.Print }, enabled = idle && queue.orders.isNotEmpty()) { Text("Print") }
            OutlinedButton(onClick = { confirm = Confirm.NewSheet }, enabled = idle && queue.sheet.usedSlots.isNotEmpty()) { Text("New sheet") }
            // For a sheet printed some other way, e.g. from a saved PDF.
            TextButton(onClick = { confirm = Confirm.Printed }, enabled = idle && queue.orders.isNotEmpty()) { Text("Mark as printed") }
            pickPdf?.let { TextButton(onClick = it, enabled = idle) { Text("Print a PDF from the phone…") } }
        } else if (viewModel.canShare) {
            Button(onClick = viewModel::sharePdf, enabled = idle && queue.orders.isNotEmpty()) {
                Text(if (state.printing) "Making PDF…" else "Share PDF")
            }
            OutlinedButton(onClick = { confirm = Confirm.Printed }, enabled = idle && queue.orders.isNotEmpty()) { Text("Mark as printed") }
            TextButton(onClick = { confirm = Confirm.NewSheet }, enabled = idle && queue.sheet.usedSlots.isNotEmpty()) { Text("New sheet") }
        } else {
            Button(onClick = { confirm = Confirm.Printed }, enabled = queue.orders.isNotEmpty()) { Text("Mark as printed") }
            OutlinedButton(onClick = { confirm = Confirm.NewSheet }, enabled = queue.sheet.usedSlots.isNotEmpty()) { Text("New sheet") }
        }
    }

    when (confirm) {
        Confirm.Print -> ConfirmDialog(
            title = "Print ${count(queue.copies, "photo")}?",
            text = printInstructions(state, viewModel.template.capacity),
            action = "Print",
            onConfirm = viewModel::print,
            onDismiss = { confirm = null },
        )
        Confirm.Printed -> ConfirmDialog(
            title = "Mark ${count(queue.copies, "photo")} as printed?",
            text = "Do this once the sheet has come out of the printer. The queue empties and the used slots are remembered for the next customers.",
            action = "Mark as printed",
            onConfirm = viewModel::markPrinted,
            onDismiss = { confirm = null },
        )
        Confirm.NewSheet -> ConfirmDialog(
            title = "Start a new sheet?",
            text = "Do this after putting a blank sheet in the printer. All ${viewModel.template.capacity} slots will be treated as free.",
            action = "New sheet",
            onConfirm = viewModel::newSheet,
            onDismiss = { confirm = null },
        )
        null -> Unit
    }
}

/** Whether photo paper and quality are saved, and the size correction measured with them. */
@Composable
private fun PhotoSettingsRow(state: PrintState, viewModel: PrintViewModel) {
    val idle = !state.printing && !state.choosingSettings
    Row(verticalAlignment = Alignment.CenterVertically) {
        val saved = state.photoSettings == PhotoSettings.Saved
        Text(
            if (saved) "Photo paper settings saved." else "Photo paper settings not saved yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (saved) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = viewModel::choosePhotoSettings, enabled = idle) {
            Text(if (state.choosingSettings) "Settings open…" else "Photo settings")
        }
    }
    // Measured on the photo paper, so offered once the photo settings are saved.
    state.calibration?.let { calibration ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Size correction: ${calibration.describe().replaceFirstChar { it.lowercase() }}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = viewModel::openCalibration, enabled = idle) { Text("Calibrate") }
        }
    }
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Put one sheet of photo paper in the printer and print the calibration sheet.")
                OutlinedButton(onClick = viewModel::printCalibrationSheet, enabled = !state.printing) {
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
            Button(onClick = { viewModel.saveCalibration(width, height, left, top) }, enabled = !state.printing) { Text("Save correction") }
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

@Composable
private fun OrderRow(order: QueuedOrder, color: Color, viewModel: PrintViewModel) {
    val customer = order.customer
    ListItem(
        leadingContent = { Box(Modifier.size(16.dp).background(color, RoundedCornerShape(4.dp))) },
        headlineContent = { Text(listOfNotNull(customer.label, customer.name).joinToString("  ")) },
        supportingContent = { Text(customer.date.toString()) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepButton("−", enabled = order.copies > 1) { viewModel.setCopies(order, order.copies - 1) }
                Text("${order.copies}", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.width(32.dp))
                StepButton("+", enabled = order.copies < Order.MAX_COPIES) { viewModel.setCopies(order, order.copies + 1) }
                IconButton(onClick = { viewModel.remove(order) }) { Icon(Icons.Filled.Close, contentDescription = "Remove ${customer.label}") }
            }
        },
    )
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) =
    TextButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(40.dp)) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }

@Composable
private fun ColumnScope.Sheets(state: PrintState, viewModel: PrintViewModel) {
    val template = viewModel.template
    val sheets = state.sheets
    val index = state.sheetIndex.coerceIn(sheets.indices)
    val sheet = state.shownSheet
    val used = state.queue.sheet.usedSlots.size

    Row(verticalAlignment = Alignment.CenterVertically) {
        val title = buildString {
            if (sheets.size > 1) append("Sheet ${index + 1} of ${sheets.size} · ")
            append(
                when {
                    sheet.reusesPartialSheet -> "sheet in the printer, $used of ${template.capacity} slots already used"
                    index == 0 && used == 0 -> "blank sheet"
                    else -> "new sheet"
                },
            )
        }
        Text(title.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (sheets.size > 1) {
            TextButton(onClick = { viewModel.showSheet(index - 1) }, enabled = index > 0) { Text("◀") }
            TextButton(onClick = { viewModel.showSheet(index + 1) }, enabled = index < sheets.size - 1) { Text("▶") }
        }
    }

    val colors = state.queue.orders.mapIndexed { i, order -> order.id to orderColor(i) }.toMap()
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        SheetPreview(
            template = template,
            sheet = sheet,
            photos = state.photos,
            colorOf = { colors[it] ?: orderColor(0) },
        )
    }
    Text(
        "Grey slots are already printed. ${template.name}, ${template.photo.width.fmt()} × ${template.photo.height.fmt()} mm photos.",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun ConfirmDialog(title: String, text: String, action: String, onConfirm: () -> Unit, onDismiss: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(text) },
    confirmButton = { Button(onClick = { onConfirm(); onDismiss() }) { Text(action) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
)

private enum class Confirm { Print, Printed, NewSheet }

/** Which sheet goes into the printer first, and how many blank ones follow. */
private fun printInstructions(state: PrintState, capacity: Int): String = buildString {
    append("On ${state.printerName ?: "the default printer"}. ")
    val sheets = state.pack.sheets
    if (sheets.firstOrNull()?.reusesPartialSheet == true) {
        val used = state.queue.sheet.usedSlots.size
        append("Put the part-used sheet ($used of $capacity slots used) in the printer first, the same way round as last time.")
    } else {
        append("Put a blank photo sheet in the printer.")
    }
    val more = sheets.size - 1
    if (more > 0) append(if (more == 1) " It needs 1 more blank sheet after that." else " It needs $more more blank sheets after that.")
    if (state.photoSettings == PhotoSettings.NotSaved) {
        append("\n\nPhoto paper settings are not saved, so the printer will use its normal paper and quality. Use Photo settings first to avoid wasting a sheet.")
    }
}

private fun count(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"

private fun Double.fmt() = if (this % 1.0 == 0.0) toInt().toString() else toString()

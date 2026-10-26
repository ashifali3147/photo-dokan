package com.tlw.photodokan.ui.print

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.model.Order
import com.tlw.photodokan.core.model.QueuedOrder
import com.tlw.photodokan.print.CalibrationSheet
import com.tlw.photodokan.print.PhotoSettings
import com.tlw.photodokan.print.ToneStrip
import com.tlw.photodokan.ui.SheetPreview
import com.tlw.photodokan.ui.components.Card
import com.tlw.photodokan.ui.components.CopyStepper
import com.tlw.photodokan.ui.components.CustomerNumber
import com.tlw.photodokan.ui.components.Notice
import com.tlw.photodokan.ui.components.PrimaryButton
import com.tlw.photodokan.ui.components.QuietButton
import com.tlw.photodokan.ui.components.StatusDot
import com.tlw.photodokan.ui.components.StudioBackground
import com.tlw.photodokan.ui.components.riseIn
import com.tlw.photodokan.ui.platform.imageBitmapOf
import com.tlw.photodokan.ui.theme.StudioTheme
import org.koin.compose.viewmodel.koinViewModel

/** The print queue next to a to-scale preview of where each copy lands on the sheet. */
@Composable
fun PrintScreen(modifier: Modifier = Modifier, viewModel: PrintViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()

    // A customer's crop, or the default printer, may have changed since this screen was last shown.
    LaunchedEffect(viewModel) { viewModel.refresh() }

    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val pickPdf = if (viewModel.canPrintPdf) rememberPdfPicker(viewModel::openPdf) else null

    StudioBackground(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth >= 720.dp) {
                Row(Modifier.fillMaxSize().padding(start = 28.dp, top = 28.dp, end = 28.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.width(440.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        QueueHeader(state)
                        OrderList(state, viewModel, Modifier.weight(1f))
                        PrinterCard(state, viewModel)
                    }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SheetHeader(state, viewModel)
                        SheetWell(state, viewModel, Modifier.weight(1f))
                        Footer(state, viewModel, pickPdf) { confirm = it }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 20.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    QueueHeader(state)
                    SheetWell(state, viewModel, Modifier.weight(1f))
                    OrderList(state, viewModel, Modifier.weight(1f))
                    Footer(state, viewModel, pickPdf) { confirm = it }
                }
            }
        }
    }

    if (state.calibrating) CalibrationDialog(state, viewModel)
    if (state.matchingTone) ToneDialog(state, viewModel)
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
    state.unsent?.let { unsent ->
        ConfirmDialog(
            title = "Not printed on the PC",
            text = "${unsent.reason}\n\nYou can share the PDF instead, for example on WhatsApp, and print it on the PC from there.",
            action = "Share PDF",
            onConfirm = viewModel::shareUnsent,
            onDismiss = viewModel::keepUnsent,
            dismiss = "Not now",
        )
    }
    val queue = state.queue
    when (confirm) {
        Confirm.Print -> ConfirmDialog(
            title = "Print ${count(queue.copies, "photo")}?",
            text = printInstructions(state, viewModel.template.capacity),
            action = "Print",
            onConfirm = viewModel::print,
            onDismiss = { confirm = null },
        )
        Confirm.Send -> ConfirmDialog(
            title = "Send ${count(queue.copies, "photo")} to ${state.stationName ?: "the PC"}?",
            text = "It prints on the PC as soon as it arrives. ${sheetInstructions(state, viewModel.template.capacity, "the PC's printer")}",
            action = "Send",
            onConfirm = viewModel::sendToPc,
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

@Composable
private fun QueueHeader(state: PrintState) {
    val c = StudioTheme.colors
    val queue = state.queue
    Column {
        Text("Print", style = MaterialTheme.typography.headlineLarge, color = c.text)
        Text(
            if (queue.orders.isEmpty()) "Nothing to print yet. Add customers from the Customers screen."
            else "${count(queue.copies, "photo")} for ${count(queue.orders.size, "customer")} · ${count(state.pack.sheets.size, "sheet")}",
            style = MaterialTheme.typography.bodyLarge,
            color = c.textMuted,
        )
    }
}

@Composable
private fun OrderList(state: PrintState, viewModel: PrintViewModel, modifier: Modifier) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
        items(state.queue.orders.size, key = { state.queue.orders[it].id }) { index ->
            val order = state.queue.orders[index]
            OrderCard(order, state.photos[order.id], viewModel, Modifier.riseIn(index))
        }
    }
}

@Composable
private fun OrderCard(order: QueuedOrder, photo: ImageBitmap?, viewModel: PrintViewModel, modifier: Modifier = Modifier) {
    val c = StudioTheme.colors
    val customer = order.customer
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 52.dp, height = 68.dp).clip(RoundedCornerShape(10.dp)).background(c.well)) {
                if (photo != null) Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f)) {
                CustomerNumber(customer.label)
                Text(
                    listOfNotNull(customer.name, count(order.copies, "slot")).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textMuted,
                    maxLines = 1,
                )
            }
            CopyStepper(order.copies, { viewModel.setCopies(order, it) }, range = 1..Order.MAX_COPIES)
            IconButton(onClick = { viewModel.remove(order) }) { Icon(Icons.Filled.Close, contentDescription = "Remove ${customer.label}", tint = c.textMuted) }
        }
    }
}

/** Whether photo paper and quality are saved, and the size and colour corrections measured with them. */
@Composable
private fun PrinterCard(state: PrintState, viewModel: PrintViewModel) {
    if (!viewModel.hasPrinter || state.photoSettings == PhotoSettings.NotSupported) return
    val c = StudioTheme.colors
    val idle = !state.printing && !state.choosingSettings
    val saved = state.photoSettings == PhotoSettings.Saved
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

@Composable
private fun SheetHeader(state: PrintState, viewModel: PrintViewModel) {
    val c = StudioTheme.colors
    val sheets = state.sheets
    val index = state.sheetIndex.coerceIn(sheets.indices)
    val used = state.queue.sheet.usedSlots.size
    val capacity = viewModel.template.capacity
    val title = when {
        sheets[index].reusesPartialSheet -> "Sheet in the printer"
        index == 0 && used == 0 -> "Blank sheet"
        else -> "New sheet"
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                if (sheets.size > 1) "$title · sheet ${index + 1} of ${sheets.size}" else title,
                style = MaterialTheme.typography.titleLarge,
                color = c.text,
            )
            Text(
                (if (sheets[index].reusesPartialSheet) "$used of $capacity slots already printed" else "$capacity free slots") +
                    " · tap a slot to leave it blank",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textMuted,
            )
        }
        if (sheets.size > 1) {
            QuietButton({ viewModel.showSheet(index - 1) }, enabled = index > 0) { Text("Previous") }
            QuietButton({ viewModel.showSheet(index + 1) }, enabled = index < sheets.size - 1) { Text("Next") }
        }
    }
    Legend()
    }
}

@Composable
private fun Legend() {
    val c = StudioTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        for ((colour, label) in listOf(c.slotPrinted to "Printed", c.accent to "Will print", c.slotFree to "Free", null to "Blank")) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val swatch = Modifier.size(14.dp).clip(RoundedCornerShape(3.dp))
                if (colour != null) {
                    Box(swatch.background(colour).border(1.dp, c.outline, RoundedCornerShape(3.dp)))
                } else {
                    Box(swatch.background(c.paper).border(1.dp, c.slotBlank, RoundedCornerShape(3.dp)))
                }
                Text(label, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
        }
    }
}

/** The sheet on a recessed surround, like paper on a light table. */
@Composable
private fun SheetWell(state: PrintState, viewModel: PrintViewModel, modifier: Modifier) {
    val c = StudioTheme.colors
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.well).padding(16.dp), contentAlignment = Alignment.Center) {
        val sheetIndex = state.sheetIndex.coerceIn(state.sheets.indices)
        val sheet = state.shownSheet
        val labels = state.queue.orders.associate { it.id to it.customer.label }
        SheetPreview(
            template = viewModel.template,
            sheet = sheet,
            photos = state.photos,
            modifier = Modifier.fillMaxHeight(),
            onSlotTap = if (state.printing) null else { slot -> viewModel.toggleBlank(sheetIndex, slot) },
            describe = { slot ->
                val placed = sheet.placements.firstOrNull { it.slotIndex == slot }
                when {
                    placed != null -> "Slot ${slot + 1}, customer ${labels[placed.orderId].orEmpty()}. Tap to leave it blank."
                    slot in sheet.blankSlots -> "Slot ${slot + 1}, left blank. Tap to use it again."
                    else -> "Slot ${slot + 1}, free. Tap to leave it blank."
                }
            },
        )
    }
}

/** What is happening or happened, what to do with the sheet, and the buttons. */
@Composable
private fun Footer(state: PrintState, viewModel: PrintViewModel, pickPdf: (() -> Unit)?, onConfirm: (Confirm) -> Unit) {
    val c = StudioTheme.colors
    val queue = state.queue
    val idle = !state.printing && !state.choosingSettings
    val hasOrders = queue.orders.isNotEmpty()

    if (state.printing) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(
                when {
                    viewModel.hasPrinter -> "Printing on ${state.printerName ?: "the printer"}…"
                    state.sendingTo != null -> "Sending to ${state.sendingTo}. It prints there straight away…"
                    else -> "Making the PDF…"
                },
                color = c.text,
            )
        }
    }
    state.message?.let { Notice(it, state.messageIsError) }

    val main: Pair<String, () -> Unit>?
    val others = mutableListOf<Triple<String, Boolean, () -> Unit>>()
    when {
        viewModel.hasPrinter -> {
            main = "Print ${count(queue.copies, "photo")}" to { onConfirm(Confirm.Print) }
            others += Triple("New sheet", queue.sheet.usedSlots.isNotEmpty()) { onConfirm(Confirm.NewSheet) }
            // For a sheet printed some other way, e.g. from a saved PDF.
            others += Triple("Mark as printed", hasOrders) { onConfirm(Confirm.Printed) }
            pickPdf?.let { others += Triple("Print a PDF from the phone…", true, it) }
        }
        viewModel.canShare -> {
            val station = state.stationName.takeIf { viewModel.canSend }
            main = if (station != null) "Send to $station" to { onConfirm(Confirm.Send) }
            else (if (state.printing) "Making PDF…" else "Share PDF") to viewModel::sharePdf
            if (station != null) others += Triple("Share PDF", hasOrders, viewModel::sharePdf)
            // Straight to a Wi-Fi printer; the PC stays the sure way to get actual size.
            if (viewModel.canPrintOnPhone) others += Triple("Print", hasOrders, viewModel::printOnPhone)
            others += Triple("Mark as printed", hasOrders) { onConfirm(Confirm.Printed) }
            others += Triple("New sheet", queue.sheet.usedSlots.isNotEmpty()) { onConfirm(Confirm.NewSheet) }
        }
        else -> {
            main = "Mark as printed" to { onConfirm(Confirm.Printed) }
            others += Triple("New sheet", queue.sheet.usedSlots.isNotEmpty()) { onConfirm(Confirm.NewSheet) }
        }
    }
    Text(sheetAdvice(state, viewModel.template.capacity), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        for ((label, enabled, click) in others) QuietButton(click, enabled = idle && enabled) { Text(label) }
        main?.let { (label, click) -> PrimaryButton(click, enabled = idle && hasOrders) { Text(label) } }
    }
}

/** One line under the sheet: how to load it, so the operator needn't open the dialog to know. */
private fun sheetAdvice(state: PrintState, capacity: Int): String {
    val load = if (state.pack.sheets.firstOrNull()?.reusesPartialSheet == true) {
        "Put the part-used sheet (${state.queue.sheet.usedSlots.size} of $capacity slots used) in the printer, the same way round as last time."
    } else {
        "Put a blank photo sheet in the printer."
    }
    val blank = state.pack.sheets.sumOf { it.blankSlots.size }
    return when (blank) {
        0 -> load
        1 -> "$load 1 slot is left blank; it stays free for the next customers."
        else -> "$load $blank slots are left blank; they stay free for the next customers."
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

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    action: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismiss: String = "Cancel",
) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(text) },
    confirmButton = { PrimaryButton(onClick = { onConfirm(); onDismiss() }) { Text(action) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss) } },
)

private enum class Confirm { Print, Send, Printed, NewSheet }

/** What the Print dialog says: which printer, which sheets go in, and whether photo settings are saved. */
private fun printInstructions(state: PrintState, capacity: Int): String = buildString {
    append("On ${state.printerName ?: "the default printer"}. ")
    append(sheetInstructions(state, capacity, "the printer"))
    if (state.photoSettings == PhotoSettings.NotSaved) {
        append("\n\nPhoto paper settings are not saved, so the printer will use its normal paper and quality. Use Photo settings first to avoid wasting a sheet.")
    }
}

/** Which sheet goes into [printer] first, and how many blank ones follow. */
private fun sheetInstructions(state: PrintState, capacity: Int, printer: String): String = buildString {
    val sheets = state.pack.sheets
    if (sheets.firstOrNull()?.reusesPartialSheet == true) {
        val used = state.queue.sheet.usedSlots.size
        append("Put the part-used sheet ($used of $capacity slots used) in $printer first, the same way round as last time.")
    } else {
        append("Put a blank photo sheet in $printer.")
    }
    val more = sheets.size - 1
    if (more > 0) append(if (more == 1) " It needs 1 more blank sheet after that." else " It needs $more more blank sheets after that.")
}

private fun count(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"

private fun Double.fmt() = if (this % 1.0 == 0.0) toInt().toString() else toString()

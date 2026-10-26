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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.tlw.photodokan.ui.platform.today
import com.tlw.photodokan.ui.theme.StudioTheme
import org.koin.compose.viewmodel.koinViewModel

/** The print queue next to a to-scale preview of where each copy lands on the sheet. */
@Composable
fun PrintScreen(modifier: Modifier = Modifier, viewModel: PrintViewModel = koinViewModel(), onOpenSettings: () -> Unit = {}) {
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
                        PrinterStatus(state, viewModel, onOpenSettings)
                    }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SheetHeader(state, viewModel)
                        SheetWell(state, viewModel, Modifier.weight(1f))
                        Footer(state, viewModel, pickPdf) { confirm = it }
                    }
                }
            } else {
                // On a phone the sheet takes the full width, so its slots are big enough to tap,
                // and everything above the buttons scrolls; the buttons stay at the bottom.
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 20.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        QueueHeader(state)
                        SheetHeader(state, viewModel)
                        SheetWell(state, viewModel, Modifier, fillWidth = true)
                        state.queue.orders.forEachIndexed { index, order ->
                            OrderCard(order, state.photos[order.id], viewModel, Modifier.riseIn(index))
                        }
                    }
                    Footer(state, viewModel, pickPdf, compact = true) { confirm = it }
                }
            }
        }
    }

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
                    overflow = TextOverflow.Ellipsis,
                )
                // Numbers restart every day, so an order from another day says which day it is.
                if (customer.date != today()) {
                    Text("From ${customer.date}", style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 1)
                }
            }
            CopyStepper(order.copies, { viewModel.setCopies(order, it) }, range = 1..Order.MAX_COPIES)
            IconButton(onClick = { viewModel.remove(order) }) { Icon(Icons.Filled.Close, contentDescription = "Remove ${customer.label}", tint = c.textMuted) }
        }
    }
}

/**
 * The printer in one line: its name, and whether photo paper is saved so a sheet isn't wasted.
 * Setting it up lives in Settings, a tap away.
 */
@Composable
private fun PrinterStatus(state: PrintState, viewModel: PrintViewModel, onOpenSettings: () -> Unit) {
    if (!viewModel.hasPrinter || state.photoSettings == PhotoSettings.NotSupported) return
    val c = StudioTheme.colors
    val saved = state.photoSettings == PhotoSettings.Saved
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(if (saved) c.ok else c.warn)
            Column(Modifier.weight(1f)) {
                Text(state.printerName ?: "Printer", style = MaterialTheme.typography.titleSmall, color = c.text)
                Text(
                    if (saved) "Photo paper settings saved" else "Photo paper settings not saved yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (saved) c.textMuted else c.warn,
                )
            }
            QuietButton(onOpenSettings) { Text("Printer settings") }
        }
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
private fun SheetWell(state: PrintState, viewModel: PrintViewModel, modifier: Modifier, fillWidth: Boolean = false) {
    val c = StudioTheme.colors
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.well).padding(if (fillWidth) 10.dp else 16.dp), contentAlignment = Alignment.Center) {
        val sheetIndex = state.sheetIndex.coerceIn(state.sheets.indices)
        val sheet = state.shownSheet
        val labels = state.queue.orders.associate { it.id to it.customer.label }
        SheetPreview(
            template = viewModel.template,
            sheet = sheet,
            photos = state.photos,
            modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier.fillMaxHeight(),
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
private fun Footer(state: PrintState, viewModel: PrintViewModel, pickPdf: (() -> Unit)?, compact: Boolean = false, onConfirm: (Confirm) -> Unit) {
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
    if (compact) {
        // On a phone the main action takes the width; the rest wait in a menu beside it.
        var menu by remember { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (others.isNotEmpty()) {
                Box {
                    QuietButton({ menu = true }, enabled = idle) { Text("More") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        for ((label, enabled, click) in others) {
                            DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; click() }, enabled = idle && enabled)
                        }
                    }
                }
            }
            main?.let { (label, click) -> PrimaryButton(click, Modifier.weight(1f), enabled = idle && hasOrders) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        }
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            for ((label, enabled, click) in others) QuietButton(click, enabled = idle && enabled) { Text(label) }
            main?.let { (label, click) -> PrimaryButton(click, enabled = idle && hasOrders) { Text(label) } }
        }
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

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.model.Order
import com.tlw.photodokan.core.model.QueuedOrder
import com.tlw.photodokan.ui.SheetPreview
import com.tlw.photodokan.ui.orderColor
import org.koin.compose.viewmodel.koinViewModel

/** The print queue next to a to-scale preview of where each copy lands on the sheet. */
@Composable
fun PrintScreen(modifier: Modifier = Modifier, viewModel: PrintViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()

    // A customer's crop may have changed since their photo was last shown.
    LaunchedEffect(viewModel) { viewModel.refreshPhotos() }

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

    state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

    var confirm by remember { mutableStateOf<Confirm?>(null) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(onClick = { confirm = Confirm.Printed }, enabled = queue.orders.isNotEmpty()) { Text("Mark as printed") }
        OutlinedButton(onClick = { confirm = Confirm.NewSheet }, enabled = queue.sheet.usedSlots.isNotEmpty()) { Text("New sheet") }
    }

    when (confirm) {
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

private enum class Confirm { Printed, NewSheet }

private fun count(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"

private fun Double.fmt() = if (this % 1.0 == 0.0) toInt().toString() else toString()

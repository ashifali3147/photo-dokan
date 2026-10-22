package com.tlw.photodokan.ui.customers

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.library.Customer
import com.tlw.photodokan.ui.photo.IncomingPhotos
import com.tlw.photodokan.ui.photo.canDropPhotos
import com.tlw.photodokan.ui.photo.photoDropTarget
import com.tlw.photodokan.ui.photo.rememberPhotoPicker
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** Today's customers, search, and filing a new customer's photo. */
@Composable
fun CustomersScreen(modifier: Modifier = Modifier, viewModel: CustomersViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val pickPhoto = rememberPhotoPicker(viewModel::import)

    // A photo shared from another app is imported here.
    val incomingPhotos = koinInject<IncomingPhotos>()
    val incoming by incomingPhotos.pending.collectAsState()
    LaunchedEffect(incoming) {
        incomingPhotos.take()?.let(viewModel::import)
    }

    BoxWithConstraints(modifier.fillMaxSize().padding(24.dp)) {
        if (maxWidth >= 720.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.width(340.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CustomerList(state, viewModel)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Detail(state, viewModel, pickPhoto)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { CustomerList(state, viewModel) }
                Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(12.dp)) { Detail(state, viewModel, pickPhoto) }
            }
        }
    }
}

@Composable
private fun ColumnScope.CustomerList(state: CustomersState, viewModel: CustomersViewModel) {
    Text("Customers", style = MaterialTheme.typography.headlineMedium)
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = viewModel::previousDay) { Text("◀") }
        Text(
            if (state.date == state.today) "Today, ${state.date}" else state.date.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = viewModel::nextDay, enabled = state.date < state.today) { Text("▶") }
        if (state.date != state.today) TextButton(onClick = viewModel::showToday) { Text("Today") }
    }
    OutlinedTextField(
        value = state.query,
        onValueChange = viewModel::search,
        label = { Text("Search number, name or phone") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.customers.isEmpty()) {
        Text(
            if (state.searching) "No customer matches \"${state.query.trim()}\"." else "No customers on this day yet.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
        items(state.customers, key = { "${it.date}/${it.number}" }) { customer ->
            val selected = customer == state.selected
            val details = listOfNotNull(customer.phone, customer.date.toString().takeIf { state.searching }).joinToString(" · ")
            ListItem(
                headlineContent = { Text(listOfNotNull(customer.label, customer.name).joinToString("  ")) },
                supportingContent = if (details.isEmpty()) null else { { Text(details) } },
                colors = if (selected) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else ListItemDefaults.colors(),
                modifier = Modifier.clickable { viewModel.select(customer) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun ColumnScope.Detail(state: CustomersState, viewModel: CustomersViewModel, pickPhoto: () -> Unit) {
    var hovering by remember { mutableStateOf(false) }
    val borderColor = if (hovering) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .border(if (hovering) 3.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .photoDropTarget(onHoverChange = { hovering = it }, onDrop = viewModel::import)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val draft = state.draft
        val shown: ImageBitmap? = draft?.preview?.image ?: state.selectedImage
        when {
            state.busy && shown == null -> CircularProgressIndicator()
            shown != null -> Image(shown, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            state.selected != null -> Text("No result image for this customer yet.")
            else -> Text(
                if (canDropPhotos) "Drop a photo here, or choose one below." else "Choose a photo to start.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }

    state.message?.let {
        Text(it, color = if (state.messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    }

    val draft = state.draft
    val selected = state.selected
    if (draft != null) {
        Text("New customer · ${draft.fileName} · ${draft.preview.originalWidth} × ${draft.preview.originalHeight} px")
        // Busy days run on numbers alone, so name and phone stay tucked away unless asked for.
        var showDetails by remember(draft.fileName) { mutableStateOf(false) }
        if (showDetails || draft.name.isNotEmpty() || draft.phone.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(draft.name, { viewModel.editDraft(name = it) }, label = { Text("Name (optional)") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(
                    draft.phone, { viewModel.editDraft(phone = it) }, label = { Text("Phone (optional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.weight(1f),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = viewModel::saveDraft, enabled = !state.busy) { Text("Save as customer ${draft.numberLabel}") }
            TextButton(onClick = viewModel::discardDraft, enabled = !state.busy) { Text("Discard") }
            if (!showDetails && draft.name.isEmpty() && draft.phone.isEmpty()) {
                TextButton(onClick = { showDetails = true }) { Text("Add name or phone (optional)") }
            }
        }
    } else {
        if (selected != null) {
            Text(customerLine(selected), style = MaterialTheme.typography.titleMedium)
        }
        Button(onClick = pickPhoto, enabled = !state.busy) { Text(if (selected == null) "Choose photo" else "New customer photo") }
    }
}

private fun customerLine(c: Customer) = listOfNotNull("Customer ${c.label}", c.name, c.phone, c.date.toString()).joinToString(" · ")

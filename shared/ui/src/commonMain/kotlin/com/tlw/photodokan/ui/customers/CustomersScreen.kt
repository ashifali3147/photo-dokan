package com.tlw.photodokan.ui.customers

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.library.Customer
import com.tlw.photodokan.core.imaging.ColorAdjustment
import com.tlw.photodokan.core.model.BackgroundMode
import com.tlw.photodokan.core.model.BrushMode
import com.tlw.photodokan.ui.editor.AdjustControls
import com.tlw.photodokan.ui.editor.BackgroundControls
import com.tlw.photodokan.ui.editor.CropControls
import com.tlw.photodokan.ui.editor.CropEditor
import com.tlw.photodokan.ui.editor.SMALL_BRUSH
import com.tlw.photodokan.ui.editor.TouchUpBrush
import com.tlw.photodokan.ui.editor.backdrop
import com.tlw.photodokan.ui.photo.IncomingPhotos
import com.tlw.photodokan.ui.photo.canDropPhotos
import com.tlw.photodokan.ui.photo.photoDropTarget
import com.tlw.photodokan.ui.photo.rememberCamera
import com.tlw.photodokan.ui.photo.rememberPhotoPicker
import com.tlw.photodokan.ui.platform.SystemBackHandler
import com.tlw.photodokan.ui.print.PrintOrders
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** Today's customers, search, and filing a new customer's photo. */
@Composable
fun CustomersScreen(modifier: Modifier = Modifier, viewModel: CustomersViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val pickPhoto = rememberPhotoPicker(viewModel::import)
    val takePhoto = rememberCamera(viewModel::import)

    // A photo shared from another app is imported here.
    val incomingPhotos = koinInject<IncomingPhotos>()
    val incoming by incomingPhotos.pending.collectAsState()
    LaunchedEffect(incoming) {
        incomingPhotos.take()?.let(viewModel::import)
    }

    // Asked before Back or "◀ Customers" throws away a new photo or unsaved changes.
    var confirmClose by remember { mutableStateOf(false) }
    val close = { if (state.hasUnsavedWork) confirmClose = true else viewModel.closeEditor() }
    if (confirmClose) {
        CloseEditorDialog(state, onDiscard = { confirmClose = false; viewModel.closeEditor() }, onKeep = { confirmClose = false })
    }

    BoxWithConstraints(modifier.fillMaxSize().padding(24.dp)) {
        val wide = maxWidth >= 720.dp
        // On a phone Back closes the editor and shows the list again; side by side the list is
        // already showing, so Back only steps in to protect unsaved work.
        SystemBackHandler(enabled = state.edit != null && !state.busy && (!wide || state.hasUnsavedWork), onBack = close)
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.width(340.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CustomerList(state, viewModel)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Detail(state, viewModel, pickPhoto, takePhoto)
                }
            }
        } else {
            // On a phone the editor needs the whole screen; the list comes back when it closes.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.edit == null) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { CustomerList(state, viewModel) }
                    // The editor panel, which shows these side by side, is hidden here.
                    if (state.busy) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Opening photo…")
                        }
                    }
                    state.message?.let {
                        Text(it, color = if (state.messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                    NewPhotoButtons(pickPhoto, takePhoto, enabled = !state.busy)
                } else {
                    if (state.draft == null) TextButton(onClick = close) { Text("◀ Customers") }
                    Detail(state, viewModel, pickPhoto, takePhoto)
                }
            }
        }
    }
}

@Composable
private fun CloseEditorDialog(state: CustomersState, onDiscard: () -> Unit, onKeep: () -> Unit) {
    val draft = state.draft
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text(if (draft != null) "Discard the new photo?" else "Discard changes to customer ${state.selected?.label}?") },
        text = {
            Text(
                if (draft != null) "It has not been saved as customer ${draft.numberLabel} yet."
                else "The crop and colour changes since the last save will be lost.",
            )
        },
        confirmButton = { TextButton(onClick = onDiscard) { Text("Discard") } },
        dismissButton = { Button(onClick = onKeep) { Text("Keep editing") } },
    )
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
private fun ColumnScope.Detail(state: CustomersState, viewModel: CustomersViewModel, pickPhoto: () -> Unit, takePhoto: (() -> Unit)?) {
    var hovering by remember { mutableStateOf(false) }
    val borderColor = if (hovering) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val edit = state.edit
    var tab by remember(edit?.preview) { mutableStateOf(EditorTab.Crop) }
    // The touch-up brush in hand, if any: only on the background tab, while the background changes.
    var brushMode by remember(edit?.preview) { mutableStateOf<BrushMode?>(null) }
    var brushSize by remember { mutableStateOf(SMALL_BRUSH) }
    val brush = brushMode?.takeIf { tab == EditorTab.Background && edit?.recipe?.background?.mode != BackgroundMode.KEEP }
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .border(if (hovering) 3.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .photoDropTarget(onHoverChange = { hovering = it }, onDrop = viewModel::import)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            edit != null -> CropEditor(
                image = edit.shown,
                preset = viewModel.presetOf(edit.recipe),
                crop = edit.recipe.crop,
                onCropChange = viewModel::updateCrop,
                onCommit = viewModel::commit,
                modifier = Modifier.fillMaxSize(),
                colorMatrix = ColorAdjustment.matrix(edit.recipe),
                backdrop = backdrop(edit.recipe.background),
                brush = brush?.let { TouchUpBrush(it, brushSize, viewModel::touchUp) },
            )
            state.busy -> CircularProgressIndicator()
            else -> Text(
                if (canDropPhotos) "Drop a photo here, or choose one below." else "Choose a photo to start.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }

    if (edit != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val tabs = EditorTab.entries.filter { it != EditorTab.Background || viewModel.canChangeBackground }
            SingleChoiceSegmentedButtonRow {
                tabs.forEachIndexed { i, t ->
                    SegmentedButton(selected = tab == t, onClick = { tab = t }, shape = SegmentedButtonDefaults.itemShape(i, tabs.size)) {
                        Text(t.label)
                    }
                }
            }
            TextButton(onClick = viewModel::undo, enabled = edit.canUndo) { Text("Undo") }
            TextButton(onClick = viewModel::redo, enabled = edit.canRedo) { Text("Redo") }
        }
        when (tab) {
            EditorTab.Crop -> CropControls(
                presets = viewModel.presets,
                preset = viewModel.presetOf(edit.recipe),
                crop = edit.recipe.crop,
                onPreset = viewModel::setPreset,
                onCropChange = viewModel::updateCrop,
                onCommit = viewModel::commit,
                onReset = viewModel::resetCrop,
                onFitFace = if (viewModel.canFitFace) viewModel::fitFace else null,
            )
            EditorTab.Adjust -> AdjustControls(
                recipe = edit.recipe,
                onPreview = viewModel::adjust,
                onCommit = viewModel::commit,
                onReset = viewModel::resetAdjustments,
                onAuto = if (viewModel.canAutoLight) viewModel::autoLight else null,
            )
            EditorTab.Background -> BackgroundControls(
                background = edit.recipe.background,
                pending = edit.backgroundPending,
                onChange = viewModel::setBackground,
                onPreview = viewModel::previewBackground,
                onCommit = viewModel::commit,
                brush = brushMode,
                brushSize = brushSize,
                onBrush = { brushMode = it },
                onBrushSize = { brushSize = it },
                onClearTouchUps = viewModel::clearTouchUps,
            )
        }
    }

    state.message?.let {
        Text(it, color = if (state.messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    }

    val draft = state.draft
    val selected = state.selected
    when {
        draft != null && edit != null -> {
            Text("New customer · ${draft.fileName} · ${edit.preview.originalWidth} × ${edit.preview.originalHeight} px")
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = viewModel::saveDraft, enabled = !state.busy) { Text("Save as customer ${draft.numberLabel}") }
                TextButton(onClick = viewModel::discardDraft, enabled = !state.busy) { Text("Discard") }
                if (!showDetails && draft.name.isEmpty() && draft.phone.isEmpty()) {
                    TextButton(onClick = { showDetails = true }) { Text("Add name or phone (optional)") }
                }
            }
        }
        selected != null && edit != null -> {
            Text(customerLine(selected), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = viewModel::saveChanges, enabled = edit.changed && !state.busy) { Text("Save changes") }
                if (takePhoto != null) {
                    TextButton(onClick = takePhoto, enabled = !state.busy) { Text("Take new photo") }
                    TextButton(onClick = pickPhoto, enabled = !state.busy) { Text("Choose new photo") }
                } else {
                    TextButton(onClick = pickPhoto, enabled = !state.busy) { Text("New customer photo") }
                }
            }
            PrintRow(selected, edit, state.busy, viewModel)
        }
        else -> NewPhotoButtons(pickPhoto, takePhoto, enabled = !state.busy)
    }
}

/** Where there is a camera, taking the photo is the usual way; choosing one is the other. */
@Composable
private fun NewPhotoButtons(pickPhoto: () -> Unit, takePhoto: (() -> Unit)?, enabled: Boolean) {
    if (takePhoto == null) {
        Button(onClick = pickPhoto, enabled = enabled) { Text("Choose photo") }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = takePhoto, enabled = enabled) { Text("Take photo") }
        OutlinedButton(onClick = pickPhoto, enabled = enabled) { Text("Choose photo") }
    }
}

/** Copies to print: orders are usually 5 or 10, sometimes 4; the Print screen takes any number. */
private val CommonCopies = listOf(4, 5, 10)

@Composable
private fun PrintRow(customer: Customer, edit: EditSession, busy: Boolean, viewModel: CustomersViewModel) {
    val queue by koinInject<PrintOrders>().queue.collectAsState()
    val queued = queue.orderFor(customer)
    var copies by remember(customer) { mutableStateOf(5) }
    val shown = queued?.copies ?: copies

    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        Text("Copies")
        SingleChoiceSegmentedButtonRow {
            CommonCopies.forEachIndexed { i, n ->
                SegmentedButton(
                    selected = shown == n,
                    // Once queued, picking a number changes the queued copies straight away.
                    onClick = { if (queued != null) viewModel.addToPrint(n) else copies = n },
                    enabled = !busy,
                    shape = SegmentedButtonDefaults.itemShape(i, CommonCopies.size),
                ) { Text("$n") }
            }
        }
        if (queued == null) {
            Button(onClick = { viewModel.addToPrint(copies) }, enabled = !busy && !edit.changed) { Text("Add to print") }
        } else {
            TextButton(onClick = viewModel::removeFromPrint, enabled = !busy) { Text("Remove from print") }
        }
    }
}

private enum class EditorTab(val label: String) { Crop("Crop"), Adjust("Adjust"), Background("Background") }

private fun customerLine(c: Customer) = listOfNotNull("Customer ${c.label}", c.name, c.phone, c.date.toString()).joinToString(" · ")

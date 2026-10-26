package com.tlw.photodokan.ui.customers

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.imaging.ColorAdjustment
import com.tlw.photodokan.core.library.Customer
import com.tlw.photodokan.core.model.BackgroundMode
import com.tlw.photodokan.core.model.BrushMode
import com.tlw.photodokan.core.model.PrintQueue
import com.tlw.photodokan.ui.components.ButtonTone
import com.tlw.photodokan.ui.components.CopyChoices
import com.tlw.photodokan.ui.components.CopyStepper
import com.tlw.photodokan.ui.components.CustomerNumber
import com.tlw.photodokan.ui.components.DockTab
import com.tlw.photodokan.ui.components.GreyStage
import com.tlw.photodokan.ui.components.Notice
import com.tlw.photodokan.ui.components.PrimaryButton
import com.tlw.photodokan.ui.components.QuietButton
import com.tlw.photodokan.ui.components.ToolDock
import com.tlw.photodokan.ui.editor.BackgroundControls
import com.tlw.photodokan.ui.editor.CropControls
import com.tlw.photodokan.ui.editor.CropEditor
import com.tlw.photodokan.ui.editor.EditorIcons
import com.tlw.photodokan.ui.editor.LookControls
import com.tlw.photodokan.ui.editor.RetouchControls
import com.tlw.photodokan.ui.editor.SMALL_BRUSH
import com.tlw.photodokan.ui.editor.TouchUpBrush
import com.tlw.photodokan.ui.editor.backdrop
import com.tlw.photodokan.ui.photo.canDropPhotos
import com.tlw.photodokan.ui.photo.photoDropTarget
import com.tlw.photodokan.ui.theme.StudioTheme

private enum class EditorTab(val label: String) { Crop("Crop"), Look("Look"), Retouch("Retouch"), Backdrop("Backdrop") }

/** Copies to print: orders are usually 5 or 10, sometimes 4; the Print screen takes any number. */
private val CommonCopies = listOf(4, 5, 10)

/**
 * The selected customer or new photo: the photo on a grey stage, the four tools, and the one
 * main action for where the customer stands (save the new photo, save the changes, or add to the
 * sheet). With no photo it is the place to drop or choose one.
 */
@Composable
internal fun CustomerPanel(
    state: CustomersState,
    viewModel: CustomersViewModel,
    queue: PrintQueue,
    pickPhoto: () -> Unit,
    takePhoto: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val edit = state.edit
    if (edit == null) {
        EmptyPanel(state, viewModel, pickPhoto, takePhoto, modifier)
        return
    }
    val c = StudioTheme.colors
    var hovering by remember { mutableStateOf(false) }
    var tab by remember(edit.preview) { mutableStateOf(EditorTab.Crop) }
    // The touch-up brush in hand, if any: only on the backdrop tab, while the background changes.
    var brushMode by remember(edit.preview) { mutableStateOf<BrushMode?>(null) }
    var brushSize by remember { mutableStateOf(SMALL_BRUSH) }
    val brush = brushMode?.takeIf { tab == EditorTab.Backdrop && edit.recipe.background.mode != BackgroundMode.KEEP }
    val draft = state.draft
    val selected = state.selected

    Column(modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            onBack?.let { QuietButton(it) { Text("Back") } }
            CustomerNumber(draft?.numberLabel ?: selected?.label.orEmpty(), large = true)
            Column(Modifier.weight(1f)) {
                Text(
                    if (draft != null) "New photo · ${draft.fileName}" else customerLine(selected),
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.textMuted,
                    maxLines = 2,
                )
                Text(
                    "${viewModel.presetOf(edit.recipe).name} · ${edit.preview.originalWidth} × ${edit.preview.originalHeight} px",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textMuted,
                    maxLines = 1,
                )
            }
            if (state.busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }

        GreyStage(
            Modifier
                .weight(1.4f)
                .heightIn(min = 220.dp)
                .fillMaxWidth()
                .then(if (hovering) Modifier.border(3.dp, c.accent, RoundedCornerShape(20.dp)) else Modifier)
                .photoDropTarget(onHoverChange = { hovering = it }, onDrop = viewModel::import),
        ) {
            CropEditor(
                image = edit.shown,
                preset = viewModel.presetOf(edit.recipe),
                crop = edit.recipe.crop,
                onCropChange = viewModel::updateCrop,
                onCommit = viewModel::commit,
                modifier = Modifier.fillMaxSize().padding(12.dp),
                colorMatrix = ColorAdjustment.matrix(edit.recipe),
                backdrop = backdrop(edit.recipe.background),
                brush = brush?.let { TouchUpBrush(it, brushSize, viewModel::touchUp) },
            )
            UndoRedo(
                edit.canUndo, edit.canRedo, viewModel::undo, viewModel::redo,
                Modifier.align(Alignment.TopEnd).padding(10.dp),
            )
        }

        Column(
            Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
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
                EditorTab.Look -> LookControls(
                    recipe = edit.recipe,
                    onPreview = viewModel::adjust,
                    onCommit = viewModel::commit,
                    onReset = viewModel::resetAdjustments,
                    onAuto = if (viewModel.canAutoLight) viewModel::autoLight else null,
                )
                EditorTab.Retouch -> RetouchControls(
                    recipe = edit.recipe,
                    onCommit = viewModel::commit,
                    onEnhance = viewModel::enhance,
                    // Fair and smooth skin go by the face, so only a photo with one has them.
                    onFairness = if (edit.recipe.portrait != null) viewModel::fairness else null,
                    onRetouch = if (edit.recipe.portrait != null) viewModel::retouch else null,
                )
                EditorTab.Backdrop -> BackgroundControls(
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

        ToolDock {
            for (t in EditorTab.entries) {
                if (t == EditorTab.Backdrop && !viewModel.canChangeBackground) continue
                DockTab(
                    label = t.label,
                    selected = tab == t,
                    onClick = { tab = t },
                    icon = {
                        Icon(
                            when (t) {
                                EditorTab.Crop -> EditorIcons.Crop
                                EditorTab.Look -> EditorIcons.Look
                                EditorTab.Retouch -> EditorIcons.Retouch
                                EditorTab.Backdrop -> EditorIcons.Backdrop
                            },
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                )
            }
        }

        state.message?.let { Notice(it, state.messageIsError) }

        if (draft != null) DraftDetails(draft, state, viewModel)
        Actions(state, viewModel, queue, selected, draft != null, pickPhoto, takePhoto)
    }
}

@Composable
private fun UndoRedo(canUndo: Boolean, canRedo: Boolean, onUndo: () -> Unit, onRedo: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuietButton(onUndo, enabled = canUndo) { Text("Undo") }
        QuietButton(onRedo, enabled = canRedo) { Text("Redo") }
    }
}

/** Busy days run on numbers alone, so name and phone stay tucked away unless asked for. */
@Composable
private fun DraftDetails(draft: Draft, state: CustomersState, viewModel: CustomersViewModel) {
    var showDetails by remember(draft.fileName) { mutableStateOf(false) }
    if (showDetails || draft.name.isNotEmpty() || draft.phone.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(draft.name, { viewModel.editDraft(name = it) }, label = { Text("Name (optional)") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(
                draft.phone, { viewModel.editDraft(phone = it) }, label = { Text("Phone (optional)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.weight(1f),
            )
        }
    } else {
        QuietButton({ showDetails = true }, enabled = !state.busy) { Text("Add name or phone (optional)") }
    }
}

/** Copies, then the one main button for where this customer stands. */
@Composable
private fun Actions(
    state: CustomersState,
    viewModel: CustomersViewModel,
    queue: PrintQueue,
    selected: Customer?,
    isDraft: Boolean,
    pickPhoto: () -> Unit,
    takePhoto: (() -> Unit)?,
) {
    val edit = state.edit ?: return
    val c = StudioTheme.colors
    if (isDraft) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            QuietButton(viewModel::discardDraft, enabled = !state.busy) { Text("Discard") }
            PrimaryButton(viewModel::saveDraft, Modifier.weight(1f), enabled = !state.busy) { Text("Save as customer ${state.draft?.numberLabel}") }
        }
        return
    }
    if (selected == null) return
    val queued = queue.orderFor(selected)
    var copies by remember(selected) { mutableStateOf(5) }
    val shown = queued?.copies ?: copies
    // Once queued, picking a number changes the queued copies straight away.
    val choose = { n: Int -> if (queued != null) viewModel.addToPrint(n) else copies = n }

    Text("Copies", style = MaterialTheme.typography.titleSmall, color = c.text)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        CopyChoices(shown, CommonCopies, choose, Modifier.weight(1f))
        CopyStepper(shown, choose)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        val newPhoto = if (takePhoto != null) takePhoto else pickPhoto
        QuietButton(newPhoto, enabled = !state.busy) { Text(if (takePhoto != null) "Retake" else "New photo") }
        if (queued != null) QuietButton(viewModel::removeFromPrint, enabled = !state.busy) { Text("Remove") }
        when {
            // Saving comes first: the sheet prints what was saved.
            edit.changed -> PrimaryButton(viewModel::saveChanges, Modifier.weight(1f), enabled = !state.busy) { Text("Save changes") }
            queued == null -> PrimaryButton({ viewModel.addToPrint(copies) }, Modifier.weight(1f), enabled = !state.busy) { Text("Add to sheet · $copies copies") }
            else -> PrimaryButton({}, Modifier.weight(1f), tone = ButtonTone.Ok) { Text("On the sheet · ${queued.copies} copies") }
        }
    }
}

@Composable
private fun EmptyPanel(
    state: CustomersState,
    viewModel: CustomersViewModel,
    pickPhoto: () -> Unit,
    takePhoto: (() -> Unit)?,
    modifier: Modifier,
) {
    val c = StudioTheme.colors
    var hovering by remember { mutableStateOf(false) }
    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(if (hovering) 3.dp else 1.dp, if (hovering) c.accent else c.outline, RoundedCornerShape(20.dp))
                .photoDropTarget(onHoverChange = { hovering = it }, onDrop = viewModel::import),
            contentAlignment = Alignment.Center,
        ) {
            if (state.busy) {
                CircularProgressIndicator()
            } else {
                Text(
                    if (canDropPhotos) "Drop a photo here, or choose one." else "Choose a photo to start.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.textMuted,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        state.message?.let { Notice(it, state.messageIsError) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (takePhoto != null) {
                PrimaryButton(takePhoto, Modifier.weight(1f), enabled = !state.busy) { Text("Take photo") }
                QuietButton(pickPhoto, enabled = !state.busy) { Text("Choose photo") }
            } else {
                PrimaryButton(pickPhoto, Modifier.weight(1f), enabled = !state.busy) { Text("Choose photo") }
            }
        }
    }
}

private fun customerLine(c: Customer?): String =
    if (c == null) "" else listOfNotNull(c.name, c.phone, c.date.toString()).joinToString(" · ")

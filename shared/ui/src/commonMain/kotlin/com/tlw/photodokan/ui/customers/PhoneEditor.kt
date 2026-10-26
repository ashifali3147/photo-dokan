package com.tlw.photodokan.ui.customers

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.library.Customer
import com.tlw.photodokan.core.model.PrintQueue
import com.tlw.photodokan.ui.components.ButtonTone
import com.tlw.photodokan.ui.components.CopyChoices
import com.tlw.photodokan.ui.components.CopyStepper
import com.tlw.photodokan.ui.components.Notice
import com.tlw.photodokan.ui.components.PrimaryButton
import com.tlw.photodokan.ui.components.QuietButton
import com.tlw.photodokan.ui.components.scrollFade
import com.tlw.photodokan.ui.editor.EditorIcons
import com.tlw.photodokan.ui.theme.StudioTheme
import kotlinx.coroutines.delay

/** The height of the open tool's controls on a phone; longer ones scroll. */
private val TOOL_HEIGHT = 184.dp

/** How long a good-news message stays over the photo on a phone before it fades. */
private const val NOTICE_MILLIS = 3_000L

/**
 * The editor on a phone, where height is short: a slim top bar (back, number, undo and redo),
 * the photo on the grey stage taking all the room it can, the open tool's controls (tap the
 * tool again to fold them away), the tool dock, and one main button at the bottom. Copies open
 * in a sheet from the bottom, so they don't cost height while editing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhoneEditor(
    state: CustomersState,
    viewModel: CustomersViewModel,
    queue: PrintQueue,
    pickPhoto: () -> Unit,
    takePhoto: (() -> Unit)?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val edit = state.edit ?: return
    val c = StudioTheme.colors
    val tools = rememberEditorTools(edit.preview)
    val draft = state.draft
    val selected = state.selected
    var showCopies by remember { mutableStateOf(false) }
    var showDetails by remember(draft?.fileName) { mutableStateOf(false) }
    // One count for the copies button, the copies sheet and Add to sheet.
    val queued = selected?.let { queue.orderFor(it) }
    val (copies, chooseCopies) = if (selected != null) rememberCopies(selected, queued, viewModel) else 5 to { _: Int -> }

    Column(modifier.fillMaxSize()) {
        TopBar(state, edit, viewModel, pickPhoto, takePhoto, onBack, onAddDetails = { showDetails = true })

        if (draft != null && (showDetails || draft.name.isNotEmpty() || draft.phone.isNotEmpty())) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(draft.name, { viewModel.editDraft(name = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(
                    draft.phone, { viewModel.editDraft(phone = it) }, label = { Text("Phone") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.weight(1f),
                )
            }
        }

        EditorStage(edit, viewModel, tools, Modifier.weight(1f).heightIn(min = 240.dp).fillMaxWidth().padding(horizontal = 12.dp)) {
            FloatingNotice(state, Modifier.align(Alignment.BottomCenter).padding(10.dp))
        }

        AnimatedVisibility(tools.showControls, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            // One height for every tool, so the photo doesn't jump when switching between them.
            AnimatedContent(tools.tab, Modifier.height(TOOL_HEIGHT), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tool") { tab ->
                val scroll = rememberScrollState()
                Column(
                    Modifier.fillMaxSize().scrollFade(scroll, c.base).verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ToolControls(edit, viewModel, tools, tab)
                }
            }
        }

        EditorDock(
            viewModel,
            tools,
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            // Tapping the open tool again folds its controls away, so the photo gets the room.
            onTab = { t ->
                if (t == tools.tab) {
                    tools.showControls = !tools.showControls
                } else {
                    tools.tab = t
                    tools.showControls = true
                }
            },
        )

        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                draft != null -> {
                    QuietButton(viewModel::discardDraft, enabled = !state.busy) { Text("Discard") }
                    PrimaryButton(viewModel::saveDraft, Modifier.weight(1f), enabled = !state.busy) { Text("Save as ${draft.numberLabel}") }
                }
                selected != null -> {
                    CopiesButton(copies, enabled = !state.busy) { showCopies = true }
                    when {
                        // Saving comes first: the sheet prints what was saved.
                        edit.changed -> PrimaryButton(viewModel::saveChanges, Modifier.weight(1f), enabled = !state.busy) { Text("Save changes") }
                        queued == null -> PrimaryButton({ viewModel.addToPrint(copies) }, Modifier.weight(1f), enabled = !state.busy) { Text("Add to sheet") }
                        else -> PrimaryButton({ showCopies = true }, Modifier.weight(1f), tone = ButtonTone.Ok, enabled = !state.busy) { Text("On the sheet") }
                    }
                }
            }
        }
    }

    if (showCopies && selected != null) {
        ModalBottomSheet(onDismissRequest = { showCopies = false }, containerColor = c.surface) {
            CopiesSheet(selected, queued != null, copies, chooseCopies, viewModel, onDone = { showCopies = false })
        }
    }
}

@Composable
private fun TopBar(
    state: CustomersState,
    edit: EditSession,
    viewModel: CustomersViewModel,
    pickPhoto: () -> Unit,
    takePhoto: (() -> Unit)?,
    onBack: () -> Unit,
    onAddDetails: () -> Unit,
) {
    val c = StudioTheme.colors
    val draft = state.draft
    val selected = state.selected
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to customers") }
        Column(Modifier.weight(1f)) {
            Text(
                draft?.numberLabel ?: selected?.label.orEmpty(),
                Modifier.semantics { contentDescription = "Number ${draft?.numberLabel ?: selected?.label.orEmpty()}" },
                style = StudioTheme.type.number,
                color = c.text,
            )
            when {
                draft != null && draft.name.isEmpty() && draft.phone.isEmpty() ->
                    // Busy days run on numbers alone, so name and phone are only a tap away, never asked for.
                    Text(
                        "New photo · add name or phone",
                        Modifier.clickable(role = Role.Button, onClick = onAddDetails).padding(vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.accentText,
                    )
                draft != null -> Text("New photo, not saved yet", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                else -> Text(
                    listOfNotNull(selected?.name, viewModel.presetOf(edit.recipe).name).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textMuted,
                    maxLines = 1,
                )
            }
        }
        if (state.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        IconButton(viewModel::undo, enabled = edit.canUndo) { Icon(EditorIcons.Undo, contentDescription = "Undo") }
        IconButton(viewModel::redo, enabled = edit.canRedo) { Icon(EditorIcons.Redo, contentDescription = "Redo") }
        IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            takePhoto?.let { DropdownMenuItem(text = { Text("Take a new photo") }, onClick = { menu = false; it() }, enabled = !state.busy) }
            DropdownMenuItem(text = { Text("Choose another photo") }, onClick = { menu = false; pickPhoto() }, enabled = !state.busy)
        }
    }
}

/**
 * The latest message over the bottom of the photo. Good news fades after a few seconds so it
 * doesn't cover the chin for long; a problem stays until tapped.
 */
@Composable
private fun FloatingNotice(state: CustomersState, modifier: Modifier) {
    val message = state.message
    var hidden by remember(message) { mutableStateOf(false) }
    LaunchedEffect(message) {
        if (message != null && !state.messageIsError) {
            delay(NOTICE_MILLIS)
            hidden = true
        }
    }
    AnimatedVisibility(message != null && !hidden, modifier, enter = fadeIn(), exit = fadeOut()) {
        Notice(message.orEmpty(), state.messageIsError, Modifier.clickable { hidden = true })
    }
}

/** A square button with the copies count, which opens the copies sheet. */
@Composable
private fun CopiesButton(copies: Int, enabled: Boolean, onClick: () -> Unit) {
    val c = StudioTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .size(60.dp)
            .border(1.dp, c.outline, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$copies copies. Change" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("$copies", style = StudioTheme.type.number, color = c.text)
        Text("copies", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
    }
}

@Composable
private fun CopiesSheet(
    customer: Customer,
    queued: Boolean,
    shown: Int,
    choose: (Int) -> Unit,
    viewModel: CustomersViewModel,
    onDone: () -> Unit,
) {
    val c = StudioTheme.colors
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Copies for ${customer.label}", style = MaterialTheme.typography.titleLarge, color = c.text)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CopyChoices(shown, CommonCopies, choose, Modifier.weight(1f))
            CopyStepper(shown, choose)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (queued) {
                TextButton({ viewModel.removeFromPrint(); onDone() }) { Text("Remove from sheet", color = c.warn) }
            }
            PrimaryButton(onDone, Modifier.weight(1f)) { Text(if (queued) "Done" else "Done · $shown copies") }
        }
    }
}

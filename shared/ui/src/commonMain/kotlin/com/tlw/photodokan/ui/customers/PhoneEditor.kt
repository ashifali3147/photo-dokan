package com.tlw.photodokan.ui.customers

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.library.Customer
import com.tlw.photodokan.core.model.PrintQueue
import com.tlw.photodokan.ui.components.CopyChoices
import com.tlw.photodokan.ui.components.CopyNudge
import com.tlw.photodokan.ui.components.Notice
import com.tlw.photodokan.ui.components.PrimaryButton
import com.tlw.photodokan.ui.components.QuietButton
import com.tlw.photodokan.ui.components.scrollFade
import com.tlw.photodokan.ui.editor.EditorIcons
import com.tlw.photodokan.ui.theme.StudioTheme
import kotlinx.coroutines.delay

/**
 * The most the open tool's controls take on a phone. Each tool is laid out to fit below this, so
 * nothing hides under a scroll; the scroll is only a safety net for very short screens.
 */
private val TOOL_MAX_HEIGHT = 300.dp

/** How long a good-news message stays over the photo on a phone before it fades. */
private const val NOTICE_MILLIS = 3_000L

/**
 * The editor on a phone, where height is short: a slim top bar (back, number, whether the photo
 * is on the sheet, undo and redo), the photo on the grey stage taking all the room it can, the
 * open tool's controls, the tool dock with a button that folds the controls away, and one main
 * button at the bottom. Copies open in a sheet from the bottom, so they don't cost height while
 * editing. The head and chin guide shows only while cropping, so it never covers the face
 * while the skin or background is judged.
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
        TopBar(state, edit, viewModel, queued?.copies, pickPhoto, takePhoto, onBack, onAddDetails = { showDetails = true })

        if (draft != null && (showDetails || draft.name.isNotEmpty() || draft.phone.isNotEmpty())) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(draft.name, { viewModel.editDraft(name = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(
                    draft.phone, { viewModel.editDraft(phone = it) }, label = { Text("Phone") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.weight(1f),
                )
            }
        }

        EditorStage(
            edit,
            viewModel,
            tools,
            Modifier.weight(1f).heightIn(min = 220.dp).fillMaxWidth().padding(horizontal = 8.dp),
            inset = 6.dp,
            showGuide = tools.tab == EditorTab.Crop,
        ) {
            FloatingNotice(state, Modifier.align(Alignment.BottomCenter).padding(10.dp))
        }

        AnimatedVisibility(tools.showControls, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            // Each tool takes the height it needs; the photo above eases to the new size.
            AnimatedContent(tools.tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tool") { tab ->
                val scroll = rememberScrollState()
                Column(
                    // A choice that adds or takes away a row (Plain colour, a border) eases the height.
                    Modifier.fillMaxWidth().animateContentSize(tween(220, easing = FastOutSlowInEasing)).heightIn(max = TOOL_MAX_HEIGHT)
                        .scrollFade(scroll, c.base).verticalScroll(scroll)
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ToolControls(edit, viewModel, tools, tab, compact = true)
                }
            }
        }

        EditorDock(
            viewModel,
            tools,
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            // Tapping the open tool again folds its controls away, as does the button at the end.
            onTab = { t ->
                if (t == tools.tab) {
                    tools.showControls = !tools.showControls
                } else {
                    tools.tab = t
                    tools.showControls = true
                }
            },
            tabHeight = 52.dp,
            trailing = { FoldButton(tools.showControls) { tools.showControls = !tools.showControls } },
        )

        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                draft != null -> {
                    QuietButton(viewModel::discardDraft, enabled = !state.busy, tall = true) { Text("Discard") }
                    PrimaryButton(viewModel::saveDraft, Modifier.weight(1f), enabled = !state.busy) { Text("Save as ${draft.numberLabel}") }
                }
                selected != null -> {
                    CopiesButton(copies, enabled = !state.busy) { showCopies = true }
                    when {
                        // Saving comes first: the sheet prints what was saved.
                        edit.changed -> PrimaryButton(viewModel::saveChanges, Modifier.weight(1f), enabled = !state.busy) { Text("Save changes") }
                        queued == null -> PrimaryButton({ viewModel.addToPrint(copies) }, Modifier.weight(1f), enabled = !state.busy) { Text("Add to sheet") }
                        // Already on the sheet (the top bar says so): what's left is the next customer.
                        else -> PrimaryButton(onBack, Modifier.weight(1f), enabled = !state.busy) { Text("Next customer") }
                    }
                }
            }
        }
    }

    if (showCopies && selected != null) {
        ModalBottomSheet(onDismissRequest = { showCopies = false }, containerColor = c.surface) {
            CopiesSheet(selected, queued != null, canAdd = !edit.changed && !state.busy, copies, chooseCopies, viewModel, onDone = { showCopies = false })
        }
    }
}

@Composable
private fun TopBar(
    state: CustomersState,
    edit: EditSession,
    viewModel: CustomersViewModel,
    queuedCopies: Int?,
    pickPhoto: () -> Unit,
    takePhoto: (() -> Unit)?,
    onBack: () -> Unit,
    onAddDetails: () -> Unit,
) {
    val c = StudioTheme.colors
    val draft = state.draft
    val selected = state.selected
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to customers") }
        Column(Modifier.weight(1f)) {
            Text(
                draft?.numberLabel ?: selected?.label.orEmpty(),
                Modifier.semantics { contentDescription = "Number ${draft?.numberLabel ?: selected?.label.orEmpty()}" },
                style = StudioTheme.type.number,
                color = c.text,
            )
            // One line, the same height whatever it says, so the bar doesn't change height on saving.
            Box(Modifier.heightIn(min = 28.dp), contentAlignment = Alignment.CenterStart) {
                when {
                    draft != null && draft.name.isEmpty() && draft.phone.isEmpty() ->
                        // Busy days run on numbers alone, so name and phone are only a tap away, never asked for.
                        Text(
                            "New photo · add name or phone",
                            Modifier.clickable(role = Role.Button, onClick = onAddDetails),
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.accentText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    draft != null -> Text("New photo, not saved yet", style = MaterialTheme.typography.bodyMedium, color = c.textMuted, maxLines = 1)
                    // Whether the photo is on the sheet, and how often: the one thing to know here.
                    // The photo size is not repeated; the Crop tool shows it.
                    queuedCopies != null -> Text(
                        listOfNotNull("✓ On the sheet · $queuedCopies copies", selected?.name).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.ok,
                        maxLines = 1,
                    )
                    else -> Text(
                        listOfNotNull(selected?.name, "Not on the sheet yet").joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textMuted,
                        maxLines = 1,
                    )
                }
            }
        }
        // Its place is kept while idle, so undo and redo don't shift when saving starts.
        Box(Modifier.size(22.dp)) { if (state.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }
        IconButton(viewModel::undo, enabled = edit.canUndo) { Icon(EditorIcons.Undo, contentDescription = "Undo") }
        IconButton(viewModel::redo, enabled = edit.canRedo) { Icon(EditorIcons.Redo, contentDescription = "Redo") }
        // In a Box, so the menu opens under the button rather than at the far side of the bar.
        Box {
            IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                // A new photo replaces an unsaved one, but next to a saved customer it starts the next one.
                val (take, choose) = if (draft != null) "Take it again" to "Choose a different photo" else "New customer · take photo" to "New customer · choose photo"
                takePhoto?.let { DropdownMenuItem(text = { Text(take) }, onClick = { menu = false; it() }, enabled = !state.busy) }
                DropdownMenuItem(text = { Text(choose) }, onClick = { menu = false; pickPhoto() }, enabled = !state.busy)
            }
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

/** The copies count with a down arrow, so it reads as something to open: the copies sheet. */
@Composable
private fun CopiesButton(copies: Int, enabled: Boolean, onClick: () -> Unit) {
    val c = StudioTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .height(56.dp)
            .clip(shape)
            .border(1.dp, c.outline, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$copies copies. Change" }
            .padding(start = 14.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$copies", style = StudioTheme.type.number, color = c.text)
        Text("copies", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
        Icon(EditorIcons.FoldDown, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(20.dp))
    }
}

/** Folds the open tool's controls away so the photo gets the room, or brings them back. */
@Composable
private fun FoldButton(open: Boolean, onClick: () -> Unit) {
    val c = StudioTheme.colors
    Box(
        Modifier
            .size(width = 44.dp, height = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (open) "Hide tools" else "Show tools" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (open) EditorIcons.FoldDown else EditorIcons.FoldUp, contentDescription = null, tint = c.textMuted)
    }
}

@Composable
private fun CopiesSheet(
    customer: Customer,
    queued: Boolean,
    canAdd: Boolean,
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
            CopyNudge(shown, choose)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (queued) {
                TextButton({ viewModel.removeFromPrint(); onDone() }) { Text("Remove from sheet", color = c.warn) }
            }
            // Not on the sheet yet: choosing the copies and adding them is one tap. Unsaved changes
            // must be saved first (the main button says so), so then it only closes.
            if (!queued && canAdd) {
                PrimaryButton({ viewModel.addToPrint(shown); onDone() }, Modifier.weight(1f)) { Text("Add to sheet · $shown copies") }
            } else {
                PrimaryButton(onDone, Modifier.weight(1f)) { Text("Done") }
            }
        }
    }
}

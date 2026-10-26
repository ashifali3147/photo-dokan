package com.tlw.photodokan.ui.customers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.ui.components.Notice
import com.tlw.photodokan.ui.components.PrimaryButton
import com.tlw.photodokan.ui.components.QuietButton
import com.tlw.photodokan.ui.components.StudioBackground
import com.tlw.photodokan.ui.navigation.FullScreen
import com.tlw.photodokan.ui.photo.IncomingPhotos
import com.tlw.photodokan.ui.photo.rememberCamera
import com.tlw.photodokan.ui.photo.rememberPhotoPicker
import com.tlw.photodokan.ui.platform.SystemBackHandler
import com.tlw.photodokan.ui.print.PrintOrders
import com.tlw.photodokan.ui.theme.StudioTheme
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** Today's customers as a contact sheet, with search, and filing and editing a customer's photo. */
@Composable
fun CustomersScreen(modifier: Modifier = Modifier, viewModel: CustomersViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val thumbnails by viewModel.thumbnails.collectAsState()
    val queue by koinInject<PrintOrders>().queue.collectAsState()
    val pickPhoto = rememberPhotoPicker(viewModel::import)
    val takePhoto = rememberCamera(viewModel::import)

    // A photo shared from another app is imported here.
    val incomingPhotos = koinInject<IncomingPhotos>()
    val incoming by incomingPhotos.pending.collectAsState()
    LaunchedEffect(incoming) {
        incomingPhotos.take()?.let(viewModel::import)
    }

    // Asked before Back or the back button throws away a new photo or unsaved changes.
    var confirmClose by remember { mutableStateOf(false) }
    val close = { if (state.hasUnsavedWork) confirmClose = true else viewModel.closeEditor() }
    if (confirmClose) {
        CloseEditorDialog(state, onDiscard = { confirmClose = false; viewModel.closeEditor() }, onKeep = { confirmClose = false })
    }

    StudioBackground(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 720.dp
            // On a phone Back closes the editor and shows the grid again; side by side the grid is
            // already showing, so Back only steps in to protect unsaved work.
            SystemBackHandler(enabled = state.edit != null && !state.busy && (!wide || state.hasUnsavedWork), onBack = close)

            val grid = @Composable { columns: GridCells, gridModifier: Modifier, onImport: (() -> Unit)? ->
                CustomerGrid(
                    state = state,
                    thumbnails = thumbnails,
                    onLoadThumbnail = viewModel::loadThumbnail,
                    onSelect = viewModel::select,
                    onSearch = viewModel::search,
                    onPreviousDay = viewModel::previousDay,
                    onNextDay = viewModel::nextDay,
                    onToday = viewModel::showToday,
                    isQueued = { queue.orderFor(it) != null },
                    columns = columns,
                    modifier = gridModifier,
                    onImport = onImport,
                )
            }

            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    grid(GridCells.Adaptive(148.dp), Modifier.weight(1f).fillMaxHeight().padding(start = 28.dp, top = 28.dp, end = 12.dp, bottom = 12.dp), pickPhoto)
                    SidePanel(Modifier.width(420.dp).fillMaxHeight()) {
                        CustomerPanel(state, viewModel, queue, pickPhoto, takePhoto, Modifier.fillMaxSize())
                    }
                }
            } else if (state.edit == null) {
                // On a phone the editor needs the whole screen; the grid comes back when it closes.
                Column(Modifier.fillMaxSize()) {
                    grid(GridCells.Fixed(3), Modifier.weight(1f).padding(start = 12.dp, top = 20.dp, end = 12.dp), null)
                    PhoneNewPhoto(state, pickPhoto, takePhoto)
                }
            } else {
                FullScreen()
                PhoneEditor(state, viewModel, queue, pickPhoto, takePhoto, onBack = close)
            }
        }
    }
}

/** The editor's side of a wide window: a white column with a hairline on its left. */
@Composable
private fun SidePanel(modifier: Modifier, content: @Composable () -> Unit) {
    val c = StudioTheme.colors
    Row(modifier.background(c.surface)) {
        androidx.compose.foundation.layout.Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
        content()
    }
}

/** On a phone, taking the next customer's photo is the main thing the grid screen offers. */
@Composable
private fun PhoneNewPhoto(state: CustomersState, pickPhoto: () -> Unit, takePhoto: (() -> Unit)?) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.busy) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.width(20.dp), strokeWidth = 2.dp)
                Text("Opening photo…")
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
        dismissButton = { PrimaryButton(onClick = onKeep) { Text("Keep editing") } },
    )
}

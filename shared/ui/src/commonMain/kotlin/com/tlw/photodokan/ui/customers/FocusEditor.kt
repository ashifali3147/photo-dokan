package com.tlw.photodokan.ui.customers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.model.PrintQueue
import com.tlw.photodokan.ui.components.CustomerNumber
import com.tlw.photodokan.ui.components.GlassIconButton
import com.tlw.photodokan.ui.components.Notice
import com.tlw.photodokan.ui.components.QuietButton
import com.tlw.photodokan.ui.components.scrollFade
import com.tlw.photodokan.ui.editor.EditorIcons
import com.tlw.photodokan.ui.theme.StudioTheme

/** The rail's width: wide enough for a slider and its label, narrow enough to leave the photo the room. */
private val RAIL_WIDTH = 380.dp

/** Below this height the whole rail scrolls as one, so a landscape phone still reaches every control. */
private val SHORT_WINDOW = 560.dp

/**
 * The editor with the whole window: the photo as large as it fits on the grey stage, and beside
 * it a rail with the tools, all of the open tool's controls in view, and the main action. [onShowCustomers]
 * gives the grid back without closing the photo.
 */
@Composable
internal fun FocusEditor(
    state: CustomersState,
    viewModel: CustomersViewModel,
    queue: PrintQueue,
    pickPhoto: () -> Unit,
    takePhoto: (() -> Unit)?,
    onShowCustomers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val edit = state.edit ?: return
    val c = StudioTheme.colors
    val tools = rememberEditorTools(edit.preview)
    val draft = state.draft
    val selected = state.selected

    Row(modifier.fillMaxSize()) {
        EditorStage(edit, viewModel, tools, Modifier.weight(1f).fillMaxHeight().padding(24.dp)) {
            GlassIconButton(onShowCustomers, "Show customers", Modifier.align(Alignment.TopEnd).padding(10.dp)) {
                Icon(EditorIcons.Shrink, contentDescription = null)
            }
        }
        Row(Modifier.width(RAIL_WIDTH + 1.dp).fillMaxHeight().background(c.surface)) {
            Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val short = maxHeight < SHORT_WINDOW
                val scroll = rememberScrollState()
                val controlsScroll = rememberScrollState()
                Column(
                    Modifier.fillMaxSize().then(if (short) Modifier.verticalScroll(scroll) else Modifier).padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CustomerNumber(draft?.numberLabel ?: selected?.label.orEmpty(), large = true)
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (draft != null) "New photo · ${draft.fileName}" else selected?.name ?: "Customer",
                                style = MaterialTheme.typography.bodyLarge,
                                color = c.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(viewModel.presetOf(edit.recipe).name, style = MaterialTheme.typography.bodyMedium, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (state.busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        UndoRedo(edit.canUndo, edit.canRedo, viewModel::undo, viewModel::redo)
                    }

                    EditorDock(viewModel, tools)

                    Column(
                        (if (short) Modifier else Modifier.weight(1f).scrollFade(controlsScroll, c.surface).verticalScroll(controlsScroll)).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) { ToolControls(edit, viewModel, tools) }

                    state.message?.let { Notice(it, state.messageIsError) }
                    if (draft != null) DraftDetails(draft, state, viewModel)
                    Actions(state, viewModel, queue, selected, draft != null, pickPhoto, takePhoto)
                }
            }
        }
    }
}

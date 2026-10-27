package com.tlw.photodokan.ui.customers

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.imaging.ColorAdjustment
import com.tlw.photodokan.core.library.Customer
import com.tlw.photodokan.core.model.BackgroundMode
import com.tlw.photodokan.core.model.BrushMode
import com.tlw.photodokan.core.model.QueuedOrder
import com.tlw.photodokan.ui.components.DockTab
import com.tlw.photodokan.ui.components.GreyStage
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
import com.tlw.photodokan.ui.editor.frameLine
import com.tlw.photodokan.ui.photo.photoDropTarget
import com.tlw.photodokan.ui.theme.StudioTheme

/** The editor's four tools, usable in any order. */
internal enum class EditorTab(val label: String) { Crop("Crop"), Look("Look"), Retouch("Retouch"), Backdrop("Backdrop") }

/** Copies to print: orders are usually 5 or 10, sometimes 4; the Print screen takes any number. */
internal val CommonCopies = listOf(4, 5, 10)

/**
 * Which tool is open, whether its controls show, and the touch-up brush in hand. Shared by the
 * stage (which paints with the brush) and the controls (which choose it).
 */
@Stable
internal class EditorTools {
    var tab by mutableStateOf(EditorTab.Crop)
    /** On a phone the controls fold away, so the photo has the whole height. */
    var showControls by mutableStateOf(true)
    var brushMode by mutableStateOf<BrushMode?>(null)
    var brushSize by mutableStateOf(SMALL_BRUSH)

    /** The brush paints only on the backdrop tab, while the background is being changed. */
    fun brushFor(edit: EditSession): BrushMode? =
        brushMode?.takeIf { tab == EditorTab.Backdrop && edit.recipe.background.mode != BackgroundMode.KEEP }
}

/** Fresh tools for each photo opened: [key] is the photo's preview. */
@Composable
internal fun rememberEditorTools(key: Any?): EditorTools = remember(key) { EditorTools() }

/**
 * The photo on the grey stage, cropped live, with a drop target for another photo. [overlay]
 * floats over the photo, e.g. undo and redo or a message.
 */
@Composable
internal fun EditorStage(
    edit: EditSession,
    viewModel: CustomersViewModel,
    tools: EditorTools,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val c = StudioTheme.colors
    var hovering by remember { mutableStateOf(false) }
    val brush = tools.brushFor(edit)
    GreyStage(
        modifier
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
            border = frameLine(edit.recipe.border, viewModel.presetOf(edit.recipe).size.width),
            brush = brush?.let { TouchUpBrush(it, tools.brushSize, viewModel::touchUp) },
        )
        overlay()
    }
}

/** The controls of [tab], by default the open tool. */
@Composable
internal fun ToolControls(edit: EditSession, viewModel: CustomersViewModel, tools: EditorTools, tab: EditorTab = tools.tab) {
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
            onBlackAndWhite = viewModel::setBlackAndWhite,
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
            customColour = viewModel.customBackdropColour,
            brush = tools.brushMode,
            brushSize = tools.brushSize,
            onBrush = { tools.brushMode = it },
            onBrushSize = { tools.brushSize = it },
            onClearTouchUps = viewModel::clearTouchUps,
            canChangeBackground = viewModel.canChangeBackground,
            border = edit.recipe.border,
            onBorder = viewModel::setBorder,
            onBorderPreview = viewModel::previewBorder,
            customBorderColour = viewModel.customBorderColour,
        )
    }
}

/**
 * The four tools in a recessed track. [onTab] decides what a tap does; by default it opens that
 * tool. Backdrop always shows: its border works even where the background can't be changed.
 */
@Composable
internal fun EditorDock(
    viewModel: CustomersViewModel,
    tools: EditorTools,
    modifier: Modifier = Modifier,
    onTab: (EditorTab) -> Unit = { tools.tab = it },
) {
    ToolDock(modifier) {
        for (t in EditorTab.entries) {
            DockTab(
                label = t.label,
                selected = tools.tab == t,
                onClick = { onTab(t) },
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
}

/**
 * How many copies [customer] gets: their queued count, or the number picked before adding them
 * (5 by default). Picking a number for a queued customer changes the queue straight away.
 */
@Composable
internal fun rememberCopies(customer: Customer, queued: QueuedOrder?, viewModel: CustomersViewModel): Pair<Int, (Int) -> Unit> {
    var picked by remember(customer) { mutableStateOf(5) }
    val shown = queued?.copies ?: picked
    return shown to { n -> if (queued != null) viewModel.addToPrint(n) else picked = n }
}

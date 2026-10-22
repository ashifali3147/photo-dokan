package com.tlw.photodokan.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.model.CropConfig
import com.tlw.photodokan.core.model.PhotoSizePreset
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CropEditorTest {

    private val photo = BufferedImage(300, 400, BufferedImage.TYPE_INT_RGB).toComposeImageBitmap()
    private val preset = PhotoSizePreset.PASSPORT_35X45

    @Test
    fun zoomButtonsAndDraggingChangeTheCrop() = runComposeUiTest {
        var crop by mutableStateOf(CropConfig())
        var commits = 0
        setContent {
            Column {
                CropControls(
                    presets = PhotoSizePreset.DEFAULTS, preset = preset, crop = crop,
                    onPreset = {}, onCropChange = { crop = it(photo.width, photo.height, preset.aspect, crop) }, onCommit = { commits++ }, onReset = {},
                )
                CropEditor(
                    photo, preset, crop,
                    onCropChange = { crop = it(photo.width, photo.height, preset.aspect, crop) },
                    onCommit = { commits++ },
                    modifier = Modifier.size(350.dp, 450.dp),
                )
            }
        }

        onNodeWithText("+").performClick()
        assertEquals(1.2, crop.scale, 1e-9)
        assertEquals(1, commits, "a button press is one undo step")

        val before = crop.centerX
        onNodeWithContentDescription("Crop frame").performTouchInput { swipeRight() }
        assertTrue(crop.centerX < before, "dragging right moves the photo right, so the frame sits further left")
        assertEquals(2, commits, "a whole drag is one undo step")

        onNodeWithText("−").performClick()
        onNodeWithText("−").performClick()
        assertEquals(1.0, crop.scale, 1e-9, "never zooms out past the full frame")
    }

    @Test
    fun amountsShowAsPlusOrMinusHundred() {
        assertEquals("0", formatAmount(0.0))
        assertEquals("+25", formatAmount(0.25))
        assertEquals("−100", formatAmount(-1.0))
    }

    @Test
    fun degreesShowToTheHalfWithARealMinus() {
        assertEquals("0°", formatDegrees(0.0))
        assertEquals("2.5°", formatDegrees(2.4))
        assertEquals("−0.5°", formatDegrees(-0.5))
        assertEquals("−12°", formatDegrees(-12.1))
    }
}

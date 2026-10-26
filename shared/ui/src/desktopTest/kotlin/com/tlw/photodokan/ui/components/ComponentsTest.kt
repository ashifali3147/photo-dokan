package com.tlw.photodokan.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.ui.theme.PhotoDokanTheme
import com.tlw.photodokan.ui.theme.ThemeMode
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ComponentsTest {

    @Test
    fun aDisabledMainButtonCannotBePressed() = runComposeUiTest {
        var prints = 0
        var enabled by mutableIntStateOf(1)
        setContent {
            PhotoDokanTheme { PrimaryButton(onClick = { prints++ }, enabled = enabled == 1) { Text("Print") } }
        }
        onNodeWithText("Print").performClick()
        assertEquals(1, prints)
        enabled = 0
        onNodeWithText("Print").assertIsNotEnabled()
        onNodeWithText("Print").performClick()
        assertEquals(1, prints)
    }

    @Test
    fun copiesStayWithinTheirRange() = runComposeUiTest {
        var copies by mutableIntStateOf(2)
        setContent { PhotoDokanTheme { CopyStepper(copies, { copies = it }, range = 1..3) } }

        onNodeWithContentDescription("2 copies").assertExists()
        onNodeWithContentDescription("Fewer copies").performClick()
        assertEquals(1, copies)
        onNodeWithContentDescription("Fewer copies").assertIsNotEnabled()

        onNodeWithContentDescription("More copies").performClick()
        onNodeWithContentDescription("More copies").performClick()
        assertEquals(3, copies)
        onNodeWithContentDescription("More copies").assertIsNotEnabled()
        onNodeWithContentDescription("Fewer copies").assertIsEnabled()
    }

    @Test
    fun onlyOneValueChipIsSelected() = runComposeUiTest {
        setContent {
            PhotoDokanTheme {
                var chosen by remember { mutableIntStateOf(0) }
                Row {
                    ValueChip("Light", "55", selected = chosen == 0, onClick = { chosen = 0 })
                    ValueChip("Fair skin", "40", selected = chosen == 1, onClick = { chosen = 1 })
                }
            }
        }
        onNodeWithText("Fair skin").performClick()
        onNode(hasText("Fair skin")).assertIsSelected()
        onNode(hasText("Light")).assertIsNotSelected()
    }

    @Test
    fun customerNumbersAreAnnounced() = runComposeUiTest {
        setContent { PhotoDokanTheme { CustomerNumber("0012") } }
        onNodeWithContentDescription("Number 0012").assertExists()
    }

    @Test
    fun theChosenCopyCountIsSelected() = runComposeUiTest {
        setContent {
            PhotoDokanTheme {
                var copies by remember { mutableIntStateOf(5) }
                CopyChoices(copies, listOf(4, 5, 10), { copies = it })
            }
        }
        onNodeWithContentDescription("5 copies").assertIsSelected()
        onNodeWithContentDescription("10 copies").performClick()
        onNodeWithContentDescription("10 copies").assertIsSelected()
        onNodeWithContentDescription("5 copies").assertIsNotSelected()
    }

    /** Saved to build/reports/components for checking by eye; fails only if something can't draw. */
    @Test
    fun galleryDrawsInBothThemes() {
        for (mode in listOf(ThemeMode.Dark, ThemeMode.Light)) runComposeUiTest {
            setContent { Box(Modifier.size(1180.dp, 560.dp)) { ComponentGallery(mode) } }
            val image = onRoot().captureToImage().toAwtImage()
            val folder = File("build/reports/components").apply { mkdirs() }
            ImageIO.write(image, "png", File(folder, "gallery-${mode.name.lowercase()}.png"))
        }
    }
}

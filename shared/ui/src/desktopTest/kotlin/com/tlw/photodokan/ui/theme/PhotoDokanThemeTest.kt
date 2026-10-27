package com.tlw.photodokan.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PhotoDokanThemeTest {

    @Test
    fun theChosenModeDecidesTheColours() = runComposeUiTest {
        var studio: StudioColors? = null
        var primary = Color.Unspecified
        setContent {
            PhotoDokanTheme(ThemeMode.Light) {
                studio = StudioTheme.colors
                primary = MaterialTheme.colorScheme.primary
            }
        }
        waitForIdle()
        assertEquals(StudioPalette.Light, studio)
        assertEquals(StudioPalette.Light.accent, primary)
    }

    @Test
    fun darkWinsOverALightDevice() = runComposeUiTest {
        var studio: StudioColors? = null
        setContent { PhotoDokanTheme(ThemeMode.Dark) { studio = StudioTheme.colors } }
        waitForIdle()
        assertEquals(StudioPalette.Dark, studio)
    }

    @Test
    fun choosingInSettingsSavesTheTheme() = runComposeUiTest {
        val store = InMemoryThemeModeStore()
        val settings = ThemeSettings(store)
        setContent { PhotoDokanTheme(settings.mode.value) { AppearanceSettings(settings) } }

        onNodeWithText("Auto").assertIsSelected()
        onNodeWithText("Light").performClick()
        onNodeWithText("Light").assertIsSelected()
        assertEquals(ThemeMode.Light, store.saved)
    }

    @Test
    fun choosingAStageLookInSettingsSavesIt() = runComposeUiTest {
        val store = InMemoryStageLookStore()
        val settings = StageSettings(store)
        setContent { PhotoDokanTheme(ThemeMode.Dark) { StageLookSettings(settings) } }

        onNodeWithText("Light table").assertIsSelected()
        onNodeWithText("Neutral frost").performClick()
        onNodeWithText("Neutral frost").assertIsSelected()
        assertEquals(StageLook(StageStyle.Frost), store.load())

        // A tinted colour is kept, and the operator is told it can change how skin looks.
        onNodeWithContentDescription("Warm").performClick()
        assertEquals(StageLook(StageStyle.Frost, 0xFF8C8172), store.load())
        onNodeWithText("This colour has a tint", substring = true).assertExists()
    }
}

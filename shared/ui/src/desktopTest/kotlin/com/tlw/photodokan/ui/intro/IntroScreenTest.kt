package com.tlw.photodokan.ui.intro

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.library.PhotoLibrary
import com.tlw.photodokan.core.model.PrintQueueStore
import com.tlw.photodokan.ui.App
import com.tlw.photodokan.ui.customers.InMemoryPhotoLibrary
import com.tlw.photodokan.ui.di.appModule
import com.tlw.photodokan.ui.print.InMemoryPrintQueueStore
import com.tlw.photodokan.ui.theme.InMemoryThemeModeStore
import com.tlw.photodokan.ui.theme.PhotoDokanTheme
import com.tlw.photodokan.ui.theme.ThemeMode
import com.tlw.photodokan.ui.theme.ThemeModeStore
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class IntroScreenTest {

    @Test
    fun nextWalksThroughEveryPageAndTheLastOneFinishes() = runComposeUiTest {
        var done = 0
        setContent { PhotoDokanTheme(ThemeMode.Light) { Box(Modifier.size(390.dp, 844.dp)) { IntroScreen(onDone = { done++ }) } } }

        onNodeWithText("Take the photo").assertExists()
        onNodeWithContentDescription("Page 1 of 5").assertExists()
        for (title in listOf("Make it right", "Fill the sheet", "Print and hand over", "Welcome to Photo Dokan")) {
            onNodeWithText("Next").performClick()
            onNodeWithText(title).assertExists()
        }
        onNodeWithContentDescription("Page 5 of 5").assertExists()
        onNodeWithText("Skip").assertDoesNotExist()
        assertEquals(0, done)

        onNodeWithText("Open the shop").performClick()
        assertEquals(1, done)
    }

    @Test
    fun skipFinishesAtOnce() = runComposeUiTest {
        var done = 0
        setContent { PhotoDokanTheme(ThemeMode.Dark) { Box(Modifier.size(390.dp, 844.dp)) { IntroScreen(onDone = { done++ }) } } }
        onNodeWithText("Skip").performClick()
        assertEquals(1, done)
    }

    @Test
    fun aWideWindowCanGoBack() = runComposeUiTest {
        setContent { PhotoDokanTheme(ThemeMode.Light) { Box(Modifier.size(1100.dp, 800.dp)) { IntroScreen(onDone = {}) } } }
        onNodeWithText("Back").assertDoesNotExist()
        onNodeWithText("Next").performClick()
        onNodeWithText("Back").performClick()
        onNodeWithText("Take the photo").assertExists()
    }

    @Test
    fun theAppOpensOnTheIntroOnceAndThenOnCustomers() = runComposeUiTest {
        val store = InMemoryIntroStore()
        val koin = koinApplication {
            modules(appModule, module {
                single<PhotoLibrary> { InMemoryPhotoLibrary() }
                single<PrintQueueStore> { InMemoryPrintQueueStore() }
                single<ThemeModeStore> { InMemoryThemeModeStore() }
                // Its own, not appModule's: Koin keeps a single on the module, shared with earlier tests.
                single { IntroSettings(store) }
            })
        }
        setContent { KoinIsolatedContext(context = koin) { Box(Modifier.size(390.dp, 844.dp)) { App() } } }
        onNodeWithText("Take the photo").assertExists()
        onNodeWithText("Skip").performClick()
        onNodeWithText("Choose photo").assertExists()
        assertEquals(true, store.saved)
    }
}

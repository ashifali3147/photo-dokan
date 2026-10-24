package com.tlw.photodokan.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.library.PhotoLibrary
import com.tlw.photodokan.core.model.PrintQueueStore
import com.tlw.photodokan.ui.customers.InMemoryPhotoLibrary
import com.tlw.photodokan.ui.di.appModule
import com.tlw.photodokan.ui.print.InMemoryPrintQueueStore
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppNavigationTest {

    /** The navigation item, not the screen title with the same text. */
    private fun SemanticsNodeInteractionsProvider.nav(label: String) = onNode(hasText(label) and hasClickAction())

    private fun launch(width: Int, height: Int) = runComposeUiTest {
        setContent {
            KoinApplication(application = { modules(appModule, module {
                single<PhotoLibrary> { InMemoryPhotoLibrary() }
                single<PrintQueueStore> { InMemoryPrintQueueStore() }
            }) }) {
                Box(Modifier.size(width.dp, height.dp)) { App() }
            }
        }

        nav("Customers").assertIsSelected()
        onNodeWithText("Choose photo").assertExists()

        nav("Print").performClick()
        nav("Print").assertIsSelected()
        onNodeWithText("Nothing to print", substring = true).assertExists()

        nav("Settings").performClick()
        nav("Settings").assertIsSelected()

        nav("Customers").performClick()
        onNodeWithText("Choose photo").assertExists()
    }

    @Test
    fun wideWindowNavigatesWithTheSideRail() = launch(1100, 800)

    @Test
    fun phoneNavigatesWithTheBottomBar() = launch(400, 800)
}

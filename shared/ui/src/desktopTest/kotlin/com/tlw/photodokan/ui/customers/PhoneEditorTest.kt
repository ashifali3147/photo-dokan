package com.tlw.photodokan.ui.customers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.library.PhotoLibrary
import com.tlw.photodokan.core.model.EditRecipe
import com.tlw.photodokan.core.model.PrintQueueStore
import com.tlw.photodokan.ui.App
import com.tlw.photodokan.ui.di.appModule
import com.tlw.photodokan.ui.platform.today
import com.tlw.photodokan.ui.print.InMemoryPrintQueueStore
import com.tlw.photodokan.ui.print.PrintOrders
import com.tlw.photodokan.ui.theme.InMemoryThemeModeStore
import com.tlw.photodokan.ui.theme.ThemeModeStore
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The editor on a phone-sized window, opened from the grid. */
@OptIn(ExperimentalTestApi::class)
class PhoneEditorTest {

    private val jpeg = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(300, 400, BufferedImage.TYPE_INT_RGB), "jpg", it) }.toByteArray()

    private fun ComposeUiTest.openFirstCustomerOnAPhone(): org.koin.core.KoinApplication {
        val library = InMemoryPhotoLibrary()
        // The sheet prints "studio-slot" photos, so this customer can go on it.
        library.create(today(), null, null, jpeg).also { library.saveEdit(it, EditRecipe(presetId = "studio-slot"), jpeg) }
        val koin = koinApplication {
            modules(appModule, module {
                single<PhotoLibrary> { library }
                single<PrintQueueStore> { InMemoryPrintQueueStore() }
                single<ThemeModeStore> { InMemoryThemeModeStore() }
            })
        }
        setContent { KoinIsolatedContext(context = koin) { Box(Modifier.size(390.dp, 844.dp)) { App() } } }
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithContentDescription("Customer 0001").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithContentDescription("Customer 0001").performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithContentDescription("Back to customers").fetchSemanticsNodes().isNotEmpty() }
        return koin
    }

    private fun ComposeUiTest.shows(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theEditorHasTheWholeScreenUntilBack() = runComposeUiTest {
        openFirstCustomerOnAPhone()
        assertTrue(onAllNodesWithText("Print").fetchSemanticsNodes().isEmpty(), "the bottom bar is hidden while editing")

        onNodeWithContentDescription("Back to customers").performClick()
        waitForIdle()
        onNode(hasText("Print") and hasClickAction()).assertExists()
    }

    @Test
    fun tappingTheOpenToolFoldsItsControlsAway() = runComposeUiTest {
        openFirstCustomerOnAPhone()
        assertTrue(shows("Zoom in"))
        onNodeWithText("Crop").performClick()
        waitForIdle()
        assertTrue(!shows("Zoom in"), "the crop controls fold away")
        onNodeWithText("Crop").performClick()
        waitForIdle()
        assertTrue(shows("Zoom in"), "and come back")
    }

    @Test
    fun copiesAreChosenAndAddedFromTheSheetInOneTap() = runComposeUiTest {
        val koin = openFirstCustomerOnAPhone()
        onNodeWithContentDescription("5 copies. Change").performClick()
        waitUntil(timeoutMillis = 5_000) { shows("Copies for 0001") }
        onNodeWithContentDescription("10 copies").performClick()
        // Choosing the copies and adding them is one tap from the sheet.
        onNodeWithText("Add to sheet · 10 copies").performClick()
        waitForIdle()
        assertEquals(listOf(10), koin.koin.get<PrintOrders>().queue.value.orders.map { it.copies })
        onNodeWithContentDescription("10 copies. Change").assertExists()
        onNodeWithText("On the sheet").assertExists()
    }
}

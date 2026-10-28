package com.tlw.photodokan.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.library.PhotoLibrary
import com.tlw.photodokan.core.model.PrintQueueStore
import com.tlw.photodokan.print.PhotoSettings
import com.tlw.photodokan.print.PrintCalibration
import com.tlw.photodokan.print.PrintDocument
import com.tlw.photodokan.print.PrintTone
import com.tlw.photodokan.print.SheetPrinter
import com.tlw.photodokan.ui.APP_VERSION
import com.tlw.photodokan.ui.App
import com.tlw.photodokan.ui.customers.InMemoryPhotoLibrary
import com.tlw.photodokan.ui.di.appModule
import com.tlw.photodokan.ui.print.InMemoryPrintQueueStore
import com.tlw.photodokan.ui.theme.InMemoryThemeModeStore
import com.tlw.photodokan.ui.theme.ThemeMode
import com.tlw.photodokan.ui.theme.ThemeModeStore
import com.tlw.photodokan.ui.theme.ThemeSettings
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/** Settings on the PC, where the printer is set up once. */
@OptIn(ExperimentalTestApi::class)
class SettingsScreenTest {

    /** A printer with photo paper saved and nothing measured yet. */
    private class SetUpPrinter : SheetPrinter {
        override fun printerName() = "EPSON L3360 Series"
        override fun photoDpi() = 300.0
        override fun print(document: PrintDocument, jobName: String) = Unit
        override fun photoSettings() = PhotoSettings.Saved
        override fun calibration() = PrintCalibration.NONE
        override fun tone() = PrintTone.NONE
    }

    private fun ComposeUiTest.startOnThePc(mode: ThemeMode = ThemeMode.Light, height: Int = 800) {
        val koin = koinApplication {
            modules(appModule, module {
                single<PhotoLibrary> { InMemoryPhotoLibrary() }
                single<PrintQueueStore> { InMemoryPrintQueueStore() }
                single<ThemeModeStore> { InMemoryThemeModeStore(mode) }
                // Its own, as appModule's single would keep the theme of an earlier test.
                single { ThemeSettings(get()) }
                single<SheetPrinter> { SetUpPrinter() }
            })
        }
        setContent { KoinIsolatedContext(context = koin) { Box(Modifier.size(1280.dp, height.dp)) { App() } } }
    }

    private fun ComposeUiTest.nav(label: String) = onNode(hasText(label) and hasClickAction())

    @Test
    fun thePrinterIsSetUpInSettingsEvenBeforePrintWasOpened() = runComposeUiTest {
        startOnThePc()
        nav("Settings").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("EPSON L3360 Series").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Photo paper settings saved").assertExists()
        onNodeWithText("Calibrate").assertExists()
        onNodeWithText("Match colours").assertExists()
    }

    @Test
    fun printerSettingsOnPrintOpensSettings() = runComposeUiTest {
        startOnThePc()
        nav("Print").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Printer settings").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Printer settings").performClick()
        waitForIdle()
        nav("Settings").assertIsSelected()
        onNodeWithText("Calibrate").assertExists()
    }

    @Test
    fun aboutShowsTheVersionAndWhoMadeIt() = runComposeUiTest {
        startOnThePc()
        nav("Settings").performClick()
        onNodeWithText("Version $APP_VERSION").performScrollTo().assertExists()
        onNodeWithText("Ashif Ali").assertExists()
        onNodeWithText("View portfolio").assertHasClickAction()
        onNodeWithText("Show the intro again").assertExists()
    }

    /** The whole page, saved to build/reports/screens for checking by eye. */
    private fun shoot(mode: ThemeMode) = runDesktopComposeUiTest(1280, 2000) {
        startOnThePc(mode, height = 2000)
        nav("Settings").performClick()
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("EPSON L3360 Series").fetchSemanticsNodes().isNotEmpty() }
        mainClock.advanceTimeBy(1_500)
        val folder = File("build/reports/screens").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(folder, "settings-wide-${mode.name.lowercase()}.png"))
    }

    @Test fun screenshotLight() = shoot(ThemeMode.Light)
    @Test fun screenshotDark() = shoot(ThemeMode.Dark)
}

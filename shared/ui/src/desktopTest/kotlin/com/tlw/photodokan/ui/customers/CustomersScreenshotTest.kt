package com.tlw.photodokan.ui.customers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.library.PhotoLibrary
import com.tlw.photodokan.core.library.StudioDate
import com.tlw.photodokan.core.model.EditRecipe
import com.tlw.photodokan.core.model.PrintQueueStore
import com.tlw.photodokan.ui.di.appModule
import com.tlw.photodokan.ui.platform.today
import com.tlw.photodokan.ui.print.InMemoryPrintQueueStore
import com.tlw.photodokan.ui.theme.InMemoryThemeModeStore
import com.tlw.photodokan.ui.theme.PhotoDokanTheme
import com.tlw.photodokan.ui.theme.ThemeMode
import com.tlw.photodokan.ui.theme.ThemeModeStore
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Draws the Customers screen with made-up customers (blue backdrop, a head and shoulders) on a
 * wide window and a phone, in both themes, into build/reports/screens for checking by eye. It
 * fails only if something can't draw.
 */
@OptIn(ExperimentalTestApi::class)
class CustomersScreenshotTest {

    private fun portrait(skin: Int, shirt: Int): ByteArray {
        val image = BufferedImage(300, 400, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color(0x3F6FB5); g.fillRect(0, 0, 300, 400)
        g.color = Color(shirt); g.fillOval(30, 280, 240, 260)
        g.color = Color(skin); g.fillOval(100, 100, 100, 130)
        g.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
    }

    private fun library(): InMemoryPhotoLibrary {
        val library = InMemoryPhotoLibrary()
        val looks = listOf(0xD9A27C to 0x2F3A4F, 0xC68B63 to 0x7B3B3B, 0xE2B796 to 0x33465E, 0xB9795A to 0x3C5A4A, 0xD5A07F to 0x5A4A6B, 0xEAC3A3 to 0x2D2D2D, 0xC28A67 to 0x6A4B2F, 0xDDAA85 to 0x2F4F6B)
        for ((skin, shirt) in looks) {
            val bytes = portrait(skin, shirt)
            val customer = library.create(today(), null, null, bytes)
            library.saveEdit(customer, EditRecipe(presetId = "passport"), bytes)
        }
        return library
    }

    private fun shoot(
        name: String,
        width: Dp,
        height: Dp,
        mode: ThemeMode,
        openCustomer: Boolean,
        then: androidx.compose.ui.test.ComposeUiTest.() -> Unit = {},
    ) = runComposeUiTest {
        val library = library()
        val koin = koinApplication {
            modules(appModule, module {
                single<PhotoLibrary> { library }
                single<PrintQueueStore> { InMemoryPrintQueueStore() }
                single<ThemeModeStore> { InMemoryThemeModeStore().also { it.save(mode) } }
            })
        }
        setContent { KoinIsolatedContext(context = koin) { Box(Modifier.size(width, height)) { com.tlw.photodokan.ui.App() } } }
        waitUntil(timeoutMillis = 5_000) { showsCustomers() }
        mainClock.advanceTimeBy(1_500)
        if (openCustomer) {
            onNodeWithContentDescription("Customer 0003").performClick()
            waitUntil(timeoutMillis = 10_000) { showsText("Crop") }
            mainClock.advanceTimeBy(1_000)
        }
        then()
        mainClock.advanceTimeBy(1_000)
        val folder = File("build/reports/screens").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(folder, "$name.png"))
    }

    private fun androidx.compose.ui.test.ComposeUiTest.showsCustomers() =
        onAllNodesWithContentDescription("Customer 0001").fetchSemanticsNodes().isNotEmpty()

    private fun androidx.compose.ui.test.ComposeUiTest.showsText(text: String) =
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test fun wideLight() = shoot("customers-wide-light", 1280.dp, 800.dp, ThemeMode.Light, openCustomer = true)
    @Test fun wideDark() = shoot("customers-wide-dark", 1280.dp, 800.dp, ThemeMode.Dark, openCustomer = true)
    @Test fun phoneGrid() = shoot("customers-phone-light", 390.dp, 844.dp, ThemeMode.Light, openCustomer = false)
    @Test fun phoneEditor() = shoot("customers-phone-editor", 390.dp, 844.dp, ThemeMode.Light, openCustomer = true)
    @Test fun wideBorder() = shoot("customers-wide-border", 1280.dp, 800.dp, ThemeMode.Light, openCustomer = true) {
        onAllNodesWithText("Backdrop").onFirst().performClick()
        onNodeWithText("Thick").performClick()
    }
    @Test fun wideBlackAndWhite() = shoot("customers-wide-black-and-white", 1280.dp, 800.dp, ThemeMode.Light, openCustomer = true) {
        onAllNodesWithText("Look").onFirst().performClick()
        onNodeWithText("Black & white").performClick()
    }
}

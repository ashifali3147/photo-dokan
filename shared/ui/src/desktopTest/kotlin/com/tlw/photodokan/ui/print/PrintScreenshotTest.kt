package com.tlw.photodokan.ui.print

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tlw.photodokan.core.library.PhotoLibrary
import com.tlw.photodokan.core.model.EditRecipe
import com.tlw.photodokan.core.model.PrintQueueStore
import com.tlw.photodokan.core.model.SheetState
import com.tlw.photodokan.core.model.SheetTemplate
import com.tlw.photodokan.ui.App
import com.tlw.photodokan.ui.customers.InMemoryPhotoLibrary
import com.tlw.photodokan.ui.di.appModule
import com.tlw.photodokan.ui.platform.today
import com.tlw.photodokan.ui.theme.InMemoryThemeModeStore
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
 * Draws the Print screen with three customers queued on a part-used sheet, one slot left blank, on a wide window and a
 * phone, into build/reports/screens for checking by eye. It fails only if something can't draw.
 */
@OptIn(ExperimentalTestApi::class)
class PrintScreenshotTest {

    private fun portrait(skin: Int, shirt: Int): ByteArray {
        val image = BufferedImage(350, 450, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color(0x3F6FB5); g.fillRect(0, 0, 350, 450)
        g.color = Color(shirt); g.fillOval(30, 330, 290, 300)
        g.color = Color(skin); g.fillOval(120, 110, 110, 140)
        g.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
    }

    private fun shoot(name: String, width: Dp, height: Dp, mode: ThemeMode) = runComposeUiTest {
        val library = InMemoryPhotoLibrary()
        val store = InMemoryPrintQueueStore()
        val looks = listOf(0xD9A27C to 0x2F3A4F, 0xC68B63 to 0x7B3B3B, 0xE2B796 to 0x33465E)
        val customers = looks.map { (skin, shirt) ->
            val bytes = portrait(skin, shirt)
            library.create(today(), null, null, bytes).also { library.saveEdit(it, EditRecipe(presetId = "passport"), bytes) }
        }
        val koin = koinApplication {
            modules(appModule, module {
                single<PhotoLibrary> { library }
                single<PrintQueueStore> { store }
                single<ThemeModeStore> { InMemoryThemeModeStore(mode) }
            })
        }
        val orders = koin.koin.get<PrintOrders>()
        orders.update { q -> customers.zip(listOf(10, 5, 4)).fold(q) { acc, (c, n) -> acc.add(c, n) }.copy(sheet = SheetState(usedSlots = (0 until 12).toSet())).toggleBlank(SheetTemplate.STUDIO_A4_5X8, 0, 22) }
        setContent { KoinIsolatedContext(context = koin) { Box(Modifier.size(width, height)) { App() } } }
        onNode(hasText("Print") and hasClickAction()).performClick()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithContentDescription("Remove 0001").fetchSemanticsNodes().isNotEmpty() }
        mainClock.advanceTimeBy(2_000)
        val folder = File("build/reports/screens").apply { mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(folder, "$name.png"))
    }

    @Test fun wideLight() = shoot("print-wide-light", 1280.dp, 800.dp, ThemeMode.Light)
    @Test fun wideDark() = shoot("print-wide-dark", 1280.dp, 800.dp, ThemeMode.Dark)
    @Test fun phone() = shoot("print-phone-light", 390.dp, 844.dp, ThemeMode.Light)
}

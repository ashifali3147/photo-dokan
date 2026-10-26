package com.tlw.photodokan.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.tlw.photodokan.core.library.PhotoLibrary
import com.tlw.photodokan.core.model.PrintQueueStore
import com.tlw.photodokan.ui.customers.InMemoryPhotoLibrary
import com.tlw.photodokan.ui.di.appModule
import com.tlw.photodokan.ui.photo.IncomingPhotos
import com.tlw.photodokan.ui.photo.PhotoSource
import com.tlw.photodokan.ui.print.InMemoryPrintQueueStore
import com.tlw.photodokan.ui.theme.InMemoryThemeModeStore
import com.tlw.photodokan.ui.theme.ThemeModeStore
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SharedPhotoTest {

    @Test
    fun sharedPhotoOpensOnCustomersAndIsImported() = runComposeUiTest {
        val koin = koinApplication { modules(appModule, module {
                single<PhotoLibrary> { InMemoryPhotoLibrary() }
                single<PrintQueueStore> { InMemoryPrintQueueStore() }
                single<ThemeModeStore> { InMemoryThemeModeStore() }
            }) }
        setContent { KoinIsolatedContext(context = koin) { App() } }

        onNode(hasText("Print") and hasClickAction()).performClick()

        val jpeg = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB), "jpg", it) }.toByteArray()
        koin.koin.get<IncomingPhotos>().send(PhotoSource("shared.jpg") { jpeg })

        // Decoding runs off the UI thread, so wait for the result.
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("shared.jpg · 40 × 20 px", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasText("Customers") and hasClickAction()).assertIsSelected()
    }
}

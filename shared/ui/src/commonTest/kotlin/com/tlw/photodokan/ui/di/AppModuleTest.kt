package com.tlw.photodokan.ui.di

import com.tlw.photodokan.core.library.LibraryLayout
import com.tlw.photodokan.core.model.PhotoSizePreset
import com.tlw.photodokan.core.model.PrintQueueStore
import com.tlw.photodokan.core.model.SheetTemplate
import com.tlw.photodokan.ui.photo.IncomingPhotos
import com.tlw.photodokan.ui.print.InMemoryPrintQueueStore
import com.tlw.photodokan.ui.print.PrintOrders
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class AppModuleTest {

    private val koin = koinApplication { modules(appModule, module { single<PrintQueueStore> { InMemoryPrintQueueStore() } }) }.koin

    @Test
    fun everyDefinitionResolves() {
        assertEquals(SheetTemplate.STUDIO_A4_5X8, koin.get<SheetTemplate>())
        assertEquals(PhotoSizePreset.DEFAULTS, koin.get<List<PhotoSizePreset>>())
        assertEquals(LibraryLayout.DEFAULT_PATTERN, koin.get<LibraryLayout>().dayPattern)
    }

    @Test
    fun singlesAreShared() {
        assertSame(koin.get<LibraryLayout>(), koin.get<LibraryLayout>())
        assertSame(koin.get<IncomingPhotos>(), koin.get<IncomingPhotos>())
        assertSame(koin.get<PrintOrders>(), koin.get<PrintOrders>(), "both screens share one queue")
    }
}

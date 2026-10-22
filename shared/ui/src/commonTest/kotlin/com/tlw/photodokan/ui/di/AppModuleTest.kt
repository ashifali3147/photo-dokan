package com.tlw.photodokan.ui.di

import com.tlw.photodokan.core.library.LibraryLayout
import com.tlw.photodokan.core.model.PhotoSizePreset
import com.tlw.photodokan.core.model.SheetTemplate
import com.tlw.photodokan.ui.photo.IncomingPhotos
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class AppModuleTest {

    private val koin = koinApplication { modules(appModule) }.koin

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
    }
}

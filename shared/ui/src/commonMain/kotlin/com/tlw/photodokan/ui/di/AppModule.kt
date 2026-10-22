package com.tlw.photodokan.ui.di

import com.tlw.photodokan.core.library.LibraryLayout
import com.tlw.photodokan.core.model.PhotoSizePreset
import com.tlw.photodokan.core.model.SheetTemplate
import com.tlw.photodokan.ui.photo.IncomingPhotos
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/** Shared definitions used by both apps. Platform services join through [initKoin]'s platform modules. */
val appModule = module {
    single { SheetTemplate.STUDIO_A4_5X8 }
    single { PhotoSizePreset.DEFAULTS }
    single { LibraryLayout() }
    single { IncomingPhotos() }
}

/** Starts Koin once per process: from `main` on desktop, from the Application on Android. */
fun initKoin(platformModules: List<Module> = emptyList(), config: KoinAppDeclaration = {}): KoinApplication =
    startKoin {
        config()
        modules(appModule + platformModules)
    }

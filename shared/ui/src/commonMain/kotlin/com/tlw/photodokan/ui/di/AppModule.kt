package com.tlw.photodokan.ui.di

import com.tlw.photodokan.core.library.LibraryLayout
import com.tlw.photodokan.core.model.PhotoSizePreset
import com.tlw.photodokan.core.model.SheetTemplate
import com.tlw.photodokan.ui.customers.CustomersViewModel
import com.tlw.photodokan.ui.photo.IncomingPhotos
import com.tlw.photodokan.ui.platform.today
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/**
 * Shared definitions used by both apps. Each app adds its platform services through [initKoin]'s
 * platform modules, including the [com.tlw.photodokan.core.library.PhotoLibrary] at its own folder.
 */
val appModule = module {
    single { SheetTemplate.STUDIO_A4_5X8 }
    single { PhotoSizePreset.DEFAULTS }
    single { LibraryLayout() }
    single { IncomingPhotos() }
    viewModel { CustomersViewModel(get(), ::today, presets = get()) }
}

/** Starts Koin once per process: from `main` on desktop, from the Application on Android. */
fun initKoin(platformModules: List<Module> = emptyList(), config: KoinAppDeclaration = {}): KoinApplication =
    startKoin {
        config()
        modules(appModule + platformModules)
    }

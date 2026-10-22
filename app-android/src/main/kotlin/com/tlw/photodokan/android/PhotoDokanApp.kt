package com.tlw.photodokan.android

import android.app.Application
import android.os.Environment
import com.tlw.photodokan.core.library.PhotoLibrary
import com.tlw.photodokan.core.model.PrintQueueStore
import com.tlw.photodokan.library.FilePhotoLibrary
import com.tlw.photodokan.library.FilePrintQueueStore
import com.tlw.photodokan.ui.di.initKoin
import kotlinx.io.files.Path
import org.koin.dsl.module

/** Starts Koin once per process, so activity recreation (e.g. rotation) doesn't start it twice. */
class PhotoDokanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // App-specific storage needs no permission. It is removed if the app is uninstalled; the phone-side
        // library task decides where phone photos should live long-term (the PC keeps the archive).
        val photos = (getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir).path
        initKoin(
            listOf(
                module {
                    single<PhotoLibrary> { FilePhotoLibrary(Path(photos, "Photo Dokan")) }
                    single<PrintQueueStore> { FilePrintQueueStore(Path(filesDir.path, "print-queue.json")) }
                },
            ),
        )
    }
}

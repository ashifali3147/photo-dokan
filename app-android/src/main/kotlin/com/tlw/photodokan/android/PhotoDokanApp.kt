package com.tlw.photodokan.android

import android.app.Application
import com.tlw.photodokan.core.library.PhotoLibrary
import com.tlw.photodokan.core.model.PrintQueueStore
import com.tlw.photodokan.library.FilePhotoLibrary
import com.tlw.photodokan.library.FilePrintQueueStore
import com.tlw.photodokan.ui.di.initKoin
import com.tlw.photodokan.ui.print.AndroidSheetSharer
import com.tlw.photodokan.ui.print.SheetSharer
import kotlinx.io.files.Path
import org.koin.dsl.module

/** Starts Koin once per process, so activity recreation (e.g. rotation) doesn't start it twice. */
class PhotoDokanApp : Application() {

    /** Where the photos live: a folder the operator picks, so they outlast the app (see MainActivity). */
    val photoFolder by lazy { PhotoFolder(this) }

    override fun onCreate() {
        super.onCreate()
        initKoin(
            listOf(
                module {
                    single<PhotoLibrary> { FilePhotoLibrary(photoFolder) }
                    single<PrintQueueStore> { FilePrintQueueStore(Path(filesDir.path, "print-queue.json")) }
                    // No printer on the phone: sheets go to the PC as PDFs.
                    single<SheetSharer> { AndroidSheetSharer(this@PhotoDokanApp) }
                },
            ),
        )
    }
}

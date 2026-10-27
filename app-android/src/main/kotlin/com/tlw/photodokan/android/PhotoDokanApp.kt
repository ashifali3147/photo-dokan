package com.tlw.photodokan.android

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import com.tlw.photodokan.core.library.PhotoLibrary
import com.tlw.photodokan.core.model.PrintQueueStore
import com.tlw.photodokan.library.FilePhotoLibrary
import com.tlw.photodokan.library.FilePrintQueueStore
import com.tlw.photodokan.station.FileStationLinkStore
import com.tlw.photodokan.station.FindingConnector
import com.tlw.photodokan.station.NsdStationFinder
import com.tlw.photodokan.station.StationClient
import com.tlw.photodokan.station.StationConnector
import com.tlw.photodokan.station.StationLinkStore
import com.tlw.photodokan.ui.editor.CustomColoursStore
import com.tlw.photodokan.ui.editor.FileCustomColoursStore
import com.tlw.photodokan.ui.theme.FileStageLookStore
import com.tlw.photodokan.ui.theme.FileThemeModeStore
import com.tlw.photodokan.ui.theme.StageLookStore
import com.tlw.photodokan.ui.theme.ThemeModeStore
import com.tlw.photodokan.ui.di.initKoin
import com.tlw.photodokan.ui.print.AndroidPhonePrinter
import com.tlw.photodokan.ui.print.AndroidSheetSharer
import com.tlw.photodokan.ui.print.PhonePrinter
import com.tlw.photodokan.ui.print.SheetSharer
import com.tlw.photodokan.ui.station.PhoneName
import com.tlw.photodokan.vision.AssetModelFiles
import com.tlw.photodokan.vision.FaceFinder
import com.tlw.photodokan.vision.ModelFiles
import com.tlw.photodokan.vision.OnnxFaceFinder
import com.tlw.photodokan.vision.OnnxPortraitMatter
import com.tlw.photodokan.vision.PortraitMatter
import com.tlw.photodokan.vision.VisionModels
import kotlinx.io.files.Path
import org.koin.dsl.module
import java.io.File

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
                    single<ThemeModeStore> { FileThemeModeStore(Path(filesDir.path, "theme.txt")) }
                    single<StageLookStore> { FileStageLookStore(Path(filesDir.path, "stage.txt")) }
                    single<CustomColoursStore> { FileCustomColoursStore(Path(filesDir.path, "colours.txt")) }
                    // Sheets usually go to the PC as PDFs.
                    single<SheetSharer> { AndroidSheetSharer(this@PhotoDokanApp) }
                    // Or straight to a Wi-Fi printer, on phones with Android's printing. Made at start-up
                    // so it already knows the open screen when the Print screen first asks for it.
                    if (packageManager.hasSystemFeature(PackageManager.FEATURE_PRINTING)) {
                        single<PhonePrinter>(createdAtStart = true) { AndroidPhonePrinter(this@PhotoDokanApp) }
                    }
                    // Or to the studio PC's Print Station over the shop's Wi-Fi, once paired; found
                    // again by its announcement when the router gives the PC a new address.
                    single<StationConnector> { FindingConnector(StationClient(), NsdStationFinder(this@PhotoDokanApp), get()) }
                    single<StationLinkStore> { FileStationLinkStore(File(filesDir, "print-station.json")) }
                    single { PhoneName(phoneName()) }
                    // Face detection and background matting, from the models inside the APK.
                    single<ModelFiles> { AssetModelFiles(this@PhotoDokanApp) }
                    single { VisionModels(get()) }
                    single<FaceFinder> { OnnxFaceFinder(get()) }
                    single<PortraitMatter> { OnnxPortraitMatter(get()) }
                },
            ),
        )
    }

    /** E.g. "Samsung Galaxy A15", as the PC lists the phone; the maker is left off when the model already says it. */
    private fun phoneName(): String {
        val maker = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model"
    }
}

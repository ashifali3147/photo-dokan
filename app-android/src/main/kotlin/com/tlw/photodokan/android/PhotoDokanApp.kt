package com.tlw.photodokan.android

import android.app.Application
import com.tlw.photodokan.ui.di.initKoin

/** Starts Koin once per process, so activity recreation (e.g. rotation) doesn't start it twice. */
class PhotoDokanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin()
    }
}

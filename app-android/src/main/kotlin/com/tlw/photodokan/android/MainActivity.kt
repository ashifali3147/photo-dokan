package com.tlw.photodokan.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import com.tlw.photodokan.ui.App
import com.tlw.photodokan.ui.photo.IncomingPhotos
import com.tlw.photodokan.ui.photo.photoSource
import org.koin.mp.KoinPlatform

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // On recreation (e.g. rotation) the shared photo was already received.
        if (savedInstanceState == null) receiveSharedPhoto(intent)
        setContent { App() }
    }

    /** singleTask: sharing while the app is open arrives here instead of starting a second copy. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveSharedPhoto(intent)
    }

    private fun receiveSharedPhoto(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) return
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return
        KoinPlatform.getKoin().get<IncomingPhotos>().send(contentResolver.photoSource(uri))
    }
}

package com.tlw.photodokan.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import com.tlw.photodokan.ui.App
import com.tlw.photodokan.ui.library.PhotoFolderScreen
import com.tlw.photodokan.ui.photo.IncomingPhotos
import com.tlw.photodokan.ui.photo.photoSource
import org.koin.mp.KoinPlatform

class MainActivity : ComponentActivity() {

    private val photoFolder get() = (application as PhotoDokanApp).photoFolder

    /** Whether the photo folder can be used; checked again each time the app comes to the front. */
    private var folderReady by mutableStateOf(false)
    private var folderLost by mutableStateOf(false)
    private var pickFailed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // On recreation (e.g. rotation) the shared photo was already received.
        if (savedInstanceState == null) receiveSharedPhoto(intent)
        setContent {
            if (folderReady) {
                App()
            } else {
                PhotoFolderScreen(lost = folderLost, failed = pickFailed) { tree ->
                    folderReady = photoFolder.use(tree)
                    pickFailed = !folderReady
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        folderReady = photoFolder.isReady()
        folderLost = !folderReady && photoFolder.wasPicked
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

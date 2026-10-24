package com.tlw.photodokan.ui.library

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Asks once where the phone keeps its photos. Shown before the app opens, and again if the folder
 * can no longer be used (its card was taken out, or it was deleted).
 *
 * [onPicked] gets the folder from Android's picker and says whether it can be used.
 */
@Composable
fun PhotoFolderScreen(lost: Boolean, failed: Boolean, onPicked: (Uri) -> Unit) {
    val pick = rememberLauncherForActivityResult(OpenDocumentTree()) { tree -> if (tree != null) onPicked(tree) }
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Text(
                    if (lost) "Photo Dokan can't open the photo folder" else "Where should Photo Dokan keep the photos?",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    if (lost) {
                        "It may have been moved or deleted, or its memory card taken out. Choose the folder again."
                    } else {
                        "Choose a folder on this phone, for example Documents. Photo Dokan makes its own folder inside it."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Photos stay there even if the app is removed, and can be copied to the PC with a USB cable.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (failed) {
                    Text(
                        "That folder can't be used. Choose another one, such as Documents.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Button(onClick = { pick.launch(DOCUMENTS) }, modifier = Modifier.widthIn(min = 200.dp)) { Text("Choose folder") }
            }
        }
    }
}

/** Where the picker opens: the phone's Documents folder. */
private val DOCUMENTS: Uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents")

package com.tlw.photodokan.ui.print

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Shares a sheet PDF through the Android share sheet. The file is kept in the app's cache, and
 * only the receiving app gets permission to read it.
 */
class AndroidSheetSharer(private val context: Context) : SheetSharer {

    override fun share(pdf: ByteArray, fileName: String) {
        val folder = File(context.cacheDir, "share").apply { mkdirs() }
        // Only the latest sheet is ever needed; older ones would just fill the cache.
        folder.listFiles()?.forEach { it.delete() }
        val file = File(folder, fileName).apply { writeBytes(pdf) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { clipData = ClipData.newRawUri(fileName, uri) }
        context.startActivity(Intent.createChooser(send, "Send the sheet PDF").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

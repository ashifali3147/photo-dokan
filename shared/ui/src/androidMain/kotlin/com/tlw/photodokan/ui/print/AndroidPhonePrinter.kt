package com.tlw.photodokan.ui.print

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.tlw.photodokan.print.NoticePage
import com.tlw.photodokan.print.PrintDocument
import com.tlw.photodokan.print.PrintException
import com.tlw.photodokan.print.PrinterPage
import com.tlw.photodokan.print.SimplePdfRenderer
import com.tlw.photodokan.print.onWholePoints
import com.tlw.photodokan.ui.platform.timed
import java.io.FileOutputStream
import java.io.IOException
import java.lang.ref.WeakReference

/**
 * Prints through Android's print dialog, to any printer a print service on the phone can reach
 * (Mopria, the default print service, or the printer maker's app).
 *
 * The PDF is made on paper of the whole points Android uses and asks not to be fitted to the page,
 * so Android prints it as it is, only clipped to the printer's margins. So for a paper size other than A4, or margins so wide that photos would
 * be cut off, the preview shows a page saying so, in the same words the PC uses, until the setting
 * is changed. (Failing the layout instead would lock the dialog's settings until it is closed.)
 */
class AndroidPhonePrinter(app: Application) : PhonePrinter {

    /** The dialog can only open over the screen the operator is looking at. */
    private var shown: WeakReference<Activity>? = null

    init {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                shown = WeakReference(activity)
            }

            override fun onActivityPaused(activity: Activity) {
                if (shown?.get() === activity) shown = null
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    override fun print(document: PrintDocument, jobName: String) {
        val activity = shown?.get() ?: throw PrintException("Open Photo Dokan on the screen, then press Print again.")
        val manager = activity.getSystemService(PrintManager::class.java) ?: throw PrintException("This phone can't print.")
        val start = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()
        manager.print(jobName, SheetAdapter(document, jobName), start)
    }

    private class SheetAdapter(
        private val document: PrintDocument,
        private val jobName: String,
    ) : PrintDocumentAdapter() {

        /** The PDF for the paper chosen: the sheet, or a notice of what to change. */
        private var shown: ByteArray? = null

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?,
        ) {
            if (cancellationSignal?.isCanceled == true) return callback.onLayoutCancelled()
            val pages = try {
                pagesFor(newAttributes)
            } catch (e: Exception) {
                return callback.onLayoutFailed(e.message)
            }
            shown = timed("Make print PDF") { SimplePdfRenderer().render(pages) }
            val info = PrintDocumentInfo.Builder("$jobName.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(pages.pages.size)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback,
        ) {
            if (cancellationSignal?.isCanceled == true) return callback.onWriteCancelled()
            try {
                val pdf = shown ?: return callback.onWriteFailed("Nothing to print yet.")
                FileOutputStream(destination.fileDescriptor).use { it.write(pdf) }
                // The whole document; the print system picks out the pages the operator chose.
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: IOException) {
                callback.onWriteFailed(e.message)
            }
        }

        /**
         * The sheet on the paper chosen, or, when that paper or printer can't take it at actual
         * size, a page saying why.
         */
        private fun pagesFor(attributes: PrintAttributes): PrintDocument {
            val media = attributes.mediaSize ?: PrintAttributes.MediaSize.ISO_A4 // no printer chosen yet
            val margins = attributes.minMargins ?: PrintAttributes.Margins.NO_MARGINS
            val page = PrinterPage.fromMils(
                media.widthMils, media.heightMils,
                margins.leftMils, margins.topMils, margins.rightMils, margins.bottomMils,
            )
            // Android's own rounding from mils to whole points (PdfManipulationService).
            val widthPt = (media.widthMils / 1000f * 72).toInt()
            val heightPt = (media.heightMils / 1000f * 72).toInt()
            val problem = document.pages.firstNotNullOfOrNull(page::problemWith) ?: return document.onWholePoints(widthPt, heightPt)
            val notice = NoticePage.page(document.pages.first().paper, problem, NOTICE_ADVICE)
            return PrintDocument(listOf(notice)).onWholePoints(widthPt, heightPt)
        }
    }

    private companion object {
        const val NOTICE_ADVICE = "The photo sheet appears here once that is changed. If this printer can't do it, use Share PDF and print on the PC."
    }
}

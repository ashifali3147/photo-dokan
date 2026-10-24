package com.tlw.photodokan.library

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

/**
 * A documents provider over a folder in the test app's storage, so [DocumentTreeFiles] can be
 * tested through the real DocumentsContract calls without the picker. It behaves like the
 * phone's own storage provider where it matters: ids are paths, and a name that is taken gets
 * " (1)" added instead of failing.
 */
class TestDocumentsProvider : DocumentsProvider() {

    override fun onCreate() = true

    override fun queryRoots(projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS)).apply {
            newRow().add(Root.COLUMN_ROOT_ID, ROOT_ID).add(Root.COLUMN_DOCUMENT_ID, ROOT_ID)
                .add(Root.COLUMN_TITLE, "Test").add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD)
        }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val file = fileOf(documentId)
        if (!file.exists()) throw FileNotFoundException(documentId)
        return cursor(projection).apply { addRow(this, documentId, file) }
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
        cursor(projection).apply {
            fileOf(parentDocumentId).listFiles()?.forEach { addRow(this, "$parentDocumentId/${it.name}", it) }
        }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        ParcelFileDescriptor.open(fileOf(documentId), ParcelFileDescriptor.parseMode(mode))

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val file = unique(fileOf(parentDocumentId), displayName)
        val made = if (mimeType == Document.MIME_TYPE_DIR) file.mkdir() else file.createNewFile()
        if (!made) throw IllegalStateException("Could not create $file")
        return "$parentDocumentId/${file.name}"
    }

    override fun deleteDocument(documentId: String) {
        if (!fileOf(documentId).deleteRecursively()) throw IllegalStateException("Could not delete $documentId")
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val before = fileOf(documentId)
        val after = unique(before.parentFile!!, displayName)
        if (!before.renameTo(after)) throw IllegalStateException("Could not rename $documentId")
        return documentId.substringBeforeLast('/') + "/" + after.name
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String) = documentId.startsWith("$parentDocumentId/")

    private fun cursor(projection: Array<out String>?) =
        MatrixCursor(projection ?: arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS))

    private fun addRow(cursor: MatrixCursor, id: String, file: File) {
        val flags = Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME or
            if (file.isDirectory) Document.FLAG_DIR_SUPPORTS_CREATE else Document.FLAG_SUPPORTS_WRITE
        cursor.newRow().add(Document.COLUMN_DOCUMENT_ID, id).add(Document.COLUMN_DISPLAY_NAME, file.name)
            .add(Document.COLUMN_MIME_TYPE, if (file.isDirectory) Document.MIME_TYPE_DIR else "application/octet-stream")
            .add(Document.COLUMN_FLAGS, flags)
    }

    private fun unique(parent: File, name: String): File {
        var file = File(parent, name)
        var n = 1
        while (file.exists()) file = File(parent, "$name (${n++})")
        return file
    }

    private fun fileOf(documentId: String) = File(base(context!!), documentId)

    companion object {
        const val AUTHORITY = "com.tlw.photodokan.library.test.documents"
        const val ROOT_ID = "root"

        fun base(context: Context) = File(context.filesDir, "documents")

        /** The provider's root folder on disk, emptied. */
        fun reset(context: Context): File = File(base(context), ROOT_ID).apply {
            deleteRecursively()
            mkdirs()
        }

        /** A tree for [documentId], as the picker would hand one over. */
        fun tree(documentId: String = ROOT_ID): Uri = DocumentsContract.buildTreeDocumentUri(AUTHORITY, documentId)
    }
}

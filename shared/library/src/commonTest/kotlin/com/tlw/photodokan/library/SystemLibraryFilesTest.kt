package com.tlw.photodokan.library

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random
import kotlin.test.AfterTest

class SystemLibraryFilesTest : LibraryFilesContract() {

    private val root = Path(SystemTemporaryDirectory, "photo-dokan-files-${Random.nextLong().toULong()}")
    override val files = SystemLibraryFiles(root)

    @AfterTest
    fun cleanUp() = deleteTree(root)

    private fun deleteTree(path: Path) {
        val fs = SystemFileSystem
        if (fs.metadataOrNull(path)?.isDirectory == true) fs.list(path).forEach(::deleteTree)
        if (fs.exists(path)) fs.delete(path)
    }
}

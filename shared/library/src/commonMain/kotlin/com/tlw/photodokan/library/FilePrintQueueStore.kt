package com.tlw.photodokan.library

import com.tlw.photodokan.core.model.PrintQueue
import com.tlw.photodokan.core.model.PrintQueueStore
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.json.Json

/** The print queue and the sheet in the printer, kept in one small JSON [file]. */
class FilePrintQueueStore(
    private val file: Path,
    private val fs: FileSystem = SystemFileSystem,
) : PrintQueueStore {

    /** A missing or damaged file starts over with an empty queue rather than stopping the app. */
    override fun load(): PrintQueue {
        if (!fs.exists(file)) return PrintQueue()
        return runCatching { json.decodeFromString<PrintQueue>(fs.readBytes(file).decodeToString()) }.getOrElse { PrintQueue() }
    }

    override fun save(queue: PrintQueue) {
        file.parent?.let(fs::createDirectories)
        fs.writeAtomically(file, json.encodeToString(queue).encodeToByteArray())
    }

    private companion object {
        val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    }
}

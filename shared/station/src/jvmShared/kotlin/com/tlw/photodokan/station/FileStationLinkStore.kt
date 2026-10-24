package com.tlw.photodokan.station

import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** The paired PC in one small JSON [file] in the phone app's private storage. */
class FileStationLinkStore(private val file: File) : StationLinkStore {

    /** A missing or damaged file means not paired; the operator can pair again. */
    override fun load(): StationLink? =
        if (file.isFile) runCatching { json.decodeFromString<StationLink>(file.readText()) }.getOrNull() else null

    override fun save(link: StationLink?) {
        if (link == null) {
            file.delete()
            return
        }
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, ".${file.name}.tmp")
        temp.writeText(json.encodeToString(link))
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

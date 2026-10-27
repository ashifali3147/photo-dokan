package com.tlw.photodokan.ui.editor

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString

/**
 * Where the Custom background colour starts before one is picked: a soft green, apart from every
 * named colour, so choosing Custom always opens the picker.
 */
const val FIRST_CUSTOM_BACKDROP = 0xFFD5E8D4

/** Where the Custom border colour starts before one is picked: a deep blue, apart from black, grey and white. */
const val FIRST_CUSTOM_BORDER = 0xFF1F3A93

/** The colours last picked by hand, which the Custom choices start from, as ARGB. */
data class CustomColours(val backdrop: Long = FIRST_CUSTOM_BACKDROP, val border: Long = FIRST_CUSTOM_BORDER)

/** Where this device keeps its [CustomColours], so they are there the next day too. */
interface CustomColoursStore {
    fun load(): CustomColours

    fun save(colours: CustomColours)
}

/** The colours as `name=AARRGGBB` lines in a small text [file] in the app's data. */
class FileCustomColoursStore(
    private val file: Path,
    private val fs: FileSystem = SystemFileSystem,
) : CustomColoursStore {

    /** A missing or unreadable file, or line, means the first colours. */
    override fun load(): CustomColours {
        if (!fs.exists(file)) return CustomColours()
        val text = runCatching { fs.source(file).buffered().use { it.readString() } }.getOrNull() ?: return CustomColours()
        val values = text.lines().mapNotNull { line ->
            val (name, hex) = line.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            hex.trim().toLongOrNull(16)?.takeIf { hex.trim().length == 8 }?.let { name.trim() to it }
        }.toMap()
        val first = CustomColours()
        return CustomColours(values["backdrop"] ?: first.backdrop, values["border"] ?: first.border)
    }

    override fun save(colours: CustomColours) {
        val folder = file.parent ?: Path(".")
        fs.createDirectories(folder)
        // Written beside the old file and moved over it, so a crash never leaves half a file.
        val temp = Path(folder, ".${file.name}.tmp")
        fs.sink(temp).buffered().use { it.writeString("backdrop=${hex(colours.backdrop)}\nborder=${hex(colours.border)}\n") }
        fs.atomicMove(temp, file)
    }

    private fun hex(argb: Long) = (argb and 0xFFFFFFFF).toString(16).uppercase().padStart(8, '0')
}

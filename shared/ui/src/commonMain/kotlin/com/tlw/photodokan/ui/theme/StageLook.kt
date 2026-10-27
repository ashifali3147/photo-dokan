package com.tlw.photodokan.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString

/**
 * What the photo lies on in the editor. Frost is a blurred copy of the photo itself carrying on
 * past the frame, seen through glass of the stage's colour; [veil] is how much of that colour
 * lies over it. [colourFrost] keeps the copy's colours, the others turn it black and white.
 */
enum class StageStyle(val label: String, val defaultColour: Long, val frost: Boolean, val colourFrost: Boolean, val veil: Float, val glass: Boolean) {
    Solid("Solid grey", STAGE_GREY, frost = false, colourFrost = false, veil = 0f, glass = false),
    Glass("Plain glass", STAGE_GREY, frost = false, colourFrost = false, veil = 0f, glass = true),
    Frost("Neutral frost", STAGE_GREY, frost = true, colourFrost = false, veil = 0.45f, glass = true),
    ColourFrost("Colour frost", STAGE_GREY, frost = true, colourFrost = true, veil = 0.35f, glass = true),
    Smoked("Smoked glass", 0xFF262628, frost = true, colourFrost = false, veil = 0.6f, glass = true),
    LightTable("Light table", 0xFFE6E6E4, frost = true, colourFrost = false, veil = 0.6f, glass = true),
}

/** The neutral mid grey the stage has always been. */
const val STAGE_GREY = 0xFF7A7A7A

/** The stage chosen in Settings: a [style], and a [colour] of its own or null for the style's. */
data class StageLook(val style: StageStyle = StageStyle.LightTable, val colour: Long? = null) {
    /** The colour of the glass. */
    val shownColour: Long get() = colour ?: style.defaultColour

    /** True grey, with no hue to shift how skin looks against it. */
    val neutral: Boolean
        get() {
            val c = shownColour
            val r = (c shr 16) and 0xff
            val g = (c shr 8) and 0xff
            val b = c and 0xff
            return maxOf(r, g, b) - minOf(r, g, b) <= 6
        }

    /** One line for the settings file: the style, then the colour in hex if one was chosen. */
    fun encode(): String = listOfNotNull(style.name, colour?.toString(16)?.uppercase()?.padStart(8, '0')).joinToString(" ")

    companion object {
        /** A line written by [encode]; anything unreadable gives the default look. */
        fun decode(line: String): StageLook {
            val parts = line.trim().split(' ')
            val style = StageStyle.entries.firstOrNull { it.name == parts.firstOrNull() } ?: return StageLook()
            val colour = parts.getOrNull(1)?.toLongOrNull(16)?.takeIf { it in 0xFF000000..0xFFFFFFFF }
            return StageLook(style, colour)
        }
    }
}

/** Where this device keeps its [StageLook]. */
interface StageLookStore {
    fun load(): StageLook

    fun save(look: StageLook)
}

/** The stage look as one line in a small text [file] in the app's data. */
class FileStageLookStore(
    private val file: Path,
    private val fs: FileSystem = SystemFileSystem,
) : StageLookStore {

    override fun load(): StageLook {
        if (!fs.exists(file)) return StageLook()
        val text = runCatching { fs.source(file).buffered().use { it.readString() } }.getOrNull() ?: return StageLook()
        return StageLook.decode(text)
    }

    override fun save(look: StageLook) {
        val folder = file.parent ?: Path(".")
        fs.createDirectories(folder)
        // Written beside the old file and moved over it, so a crash never leaves half a line.
        val temp = Path(folder, ".${file.name}.tmp")
        fs.sink(temp).buffered().use { it.writeString(look.encode()) }
        fs.atomicMove(temp, file)
    }
}

/** Keeps the look for this session only, where no file is given. */
class InMemoryStageLookStore(private var saved: StageLook = StageLook()) : StageLookStore {
    override fun load(): StageLook = saved

    override fun save(look: StageLook) {
        saved = look
    }
}

/**
 * The stage look chosen on this device, read once at start. [choose] saves at once; [preview]
 * shows a colour while a picker slider moves, and [commit] saves it when let go.
 */
class StageSettings(private val store: StageLookStore) {
    private val _look = MutableStateFlow(runCatching { store.load() }.getOrDefault(StageLook()))
    val look: StateFlow<StageLook> = _look.asStateFlow()

    fun choose(look: StageLook) {
        _look.value = look
        // The choice still applies for this session when it can't be saved.
        runCatching { store.save(look) }
    }

    fun preview(look: StageLook) {
        _look.value = look
    }

    fun commit() {
        runCatching { store.save(_look.value) }
    }
}

/** The stage look for the editors below, provided at the app's root. */
val LocalStageLook = staticCompositionLocalOf { StageLook() }

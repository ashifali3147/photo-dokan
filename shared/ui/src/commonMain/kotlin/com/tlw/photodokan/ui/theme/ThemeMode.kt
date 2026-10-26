package com.tlw.photodokan.ui.theme

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.io.buffered
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/** Dark suits a dim studio, light a bright shop counter; by default the device decides. */
enum class ThemeMode {
    System,
    Dark,
    Light;

    fun isDark(systemIsDark: Boolean): Boolean = when (this) {
        System -> systemIsDark
        Dark -> true
        Light -> false
    }
}

/** Where this device keeps its [ThemeMode]. */
interface ThemeModeStore {
    fun load(): ThemeMode

    fun save(mode: ThemeMode)
}

/** The theme as one word in a small text [file] in the app's data. */
class FileThemeModeStore(
    private val file: Path,
    private val fs: FileSystem = SystemFileSystem,
) : ThemeModeStore {

    /** A missing or unreadable file means the device decides. */
    override fun load(): ThemeMode {
        if (!fs.exists(file)) return ThemeMode.System
        val text = runCatching { fs.source(file).buffered().use { it.readString() }.trim() }.getOrNull()
        return ThemeMode.entries.firstOrNull { it.name == text } ?: ThemeMode.System
    }

    override fun save(mode: ThemeMode) {
        val folder = file.parent ?: Path(".")
        fs.createDirectories(folder)
        // Written beside the old file and moved over it, so a crash never leaves half a word.
        val temp = Path(folder, ".${file.name}.tmp")
        fs.sink(temp).buffered().use { it.writeString(mode.name) }
        fs.atomicMove(temp, file)
    }
}

/** The theme chosen on this device, read once at start and saved whenever it changes. */
class ThemeSettings(private val store: ThemeModeStore) {
    private val _mode = MutableStateFlow(runCatching { store.load() }.getOrDefault(ThemeMode.System))
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun choose(mode: ThemeMode) {
        if (mode == _mode.value) return
        _mode.value = mode
        // The choice still applies for this session when it can't be saved.
        runCatching { store.save(mode) }
    }
}

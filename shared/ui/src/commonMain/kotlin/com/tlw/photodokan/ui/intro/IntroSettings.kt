package com.tlw.photodokan.ui.intro

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.tlw.photodokan.ui.components.QuietButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import org.koin.compose.koinInject

/** Whether this device has been through the intro. */
interface IntroStore {
    fun seen(): Boolean

    fun markSeen()
}

/** Seen once a small [file] exists in the app's data; what it says doesn't matter. */
class FileIntroStore(
    private val file: Path,
    private val fs: FileSystem = SystemFileSystem,
) : IntroStore {

    override fun seen(): Boolean = fs.exists(file)

    override fun markSeen() {
        file.parent?.let(fs::createDirectories)
        fs.sink(file).buffered().use { it.writeString("seen") }
    }
}

/** Keeps whether the intro was seen for this session only. */
class InMemoryIntroStore(var saved: Boolean = false) : IntroStore {
    override fun seen(): Boolean = saved

    override fun markSeen() {
        saved = true
    }
}

/**
 * Shows the intro the first time the app opens on a device, and again when asked from Settings.
 * When the store can't be read the intro is left out: the app matters more than the tour.
 */
class IntroSettings(private val store: IntroStore) {
    private val _showing = MutableStateFlow(!runCatching { store.seen() }.getOrDefault(true))
    val showing: StateFlow<Boolean> = _showing.asStateFlow()

    fun finish() {
        _showing.value = false
        // Not saved means it shows once more next time, which is harmless.
        runCatching { store.markSeen() }
    }

    fun replay() {
        _showing.value = true
    }
}

/** Shows the intro again, from Settings → About, e.g. to show someone new how the shop works. */
@Composable
fun IntroReplayButton(settings: IntroSettings = koinInject()) {
    QuietButton(settings::replay) { Text("Show the intro again") }
}

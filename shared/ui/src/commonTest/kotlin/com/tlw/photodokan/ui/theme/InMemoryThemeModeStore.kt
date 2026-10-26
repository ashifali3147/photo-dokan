package com.tlw.photodokan.ui.theme

/** Keeps the theme in memory, or fails to save like a full disk when [failing]. */
class InMemoryThemeModeStore(var saved: ThemeMode? = null, var failing: Boolean = false) : ThemeModeStore {
    override fun load(): ThemeMode = saved ?: ThemeMode.System

    override fun save(mode: ThemeMode) {
        if (failing) throw IllegalStateException("Disk full")
        saved = mode
    }
}

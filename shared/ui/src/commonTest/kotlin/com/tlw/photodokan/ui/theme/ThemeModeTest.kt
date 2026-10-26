package com.tlw.photodokan.ui.theme

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.writeString
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeModeTest {

    private val folder = Path(SystemTemporaryDirectory, "photo-dokan-theme-${Random.nextLong().toULong()}")
    private val file = Path(folder, "settings", "theme.txt")

    @AfterTest
    fun cleanUp() {
        listOf(file, Path(folder, "settings"), folder).forEach { if (SystemFileSystem.exists(it)) SystemFileSystem.delete(it) }
    }

    @Test
    fun systemFollowsTheDeviceAndTheOthersDoNot() {
        assertTrue(ThemeMode.System.isDark(systemIsDark = true))
        assertFalse(ThemeMode.System.isDark(systemIsDark = false))
        assertTrue(ThemeMode.Dark.isDark(systemIsDark = false))
        assertFalse(ThemeMode.Light.isDark(systemIsDark = true))
    }

    @Test
    fun theDeviceDecidesUntilSomethingIsSaved() {
        assertEquals(ThemeMode.System, FileThemeModeStore(file).load())
    }

    @Test
    fun savesAndLoadsTheChoice() {
        FileThemeModeStore(file).save(ThemeMode.Light)
        assertEquals(ThemeMode.Light, FileThemeModeStore(file).load())
        FileThemeModeStore(file).save(ThemeMode.Dark)
        assertEquals(ThemeMode.Dark, FileThemeModeStore(file).load())
        assertFalse(SystemFileSystem.exists(Path(folder, "settings", ".theme.txt.tmp")))
    }

    @Test
    fun aDamagedFileMeansTheDeviceDecides() {
        SystemFileSystem.createDirectories(Path(folder, "settings"))
        SystemFileSystem.sink(file).buffered().use { it.writeString("Purple") }
        assertEquals(ThemeMode.System, FileThemeModeStore(file).load())
    }

    @Test
    fun settingsStartFromTheSavedChoiceAndSaveChanges() {
        val store = InMemoryThemeModeStore(saved = ThemeMode.Dark)
        val settings = ThemeSettings(store)
        assertEquals(ThemeMode.Dark, settings.mode.value)

        settings.choose(ThemeMode.Light)
        assertEquals(ThemeMode.Light, settings.mode.value)
        assertEquals(ThemeMode.Light, store.saved)
    }

    @Test
    fun aChoiceThatCannotBeSavedStillAppliesNow() {
        val settings = ThemeSettings(InMemoryThemeModeStore(failing = true))
        settings.choose(ThemeMode.Dark)
        assertEquals(ThemeMode.Dark, settings.mode.value)
    }
}

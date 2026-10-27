package com.tlw.photodokan.ui.intro

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntroSettingsTest {

    private val folder = Path(SystemTemporaryDirectory, "photo-dokan-intro-${Random.nextLong().toULong()}")
    private val file = Path(folder, "settings", "intro-seen.txt")

    @AfterTest
    fun cleanUp() {
        listOf(file, Path(folder, "settings"), folder).forEach { if (SystemFileSystem.exists(it)) SystemFileSystem.delete(it) }
    }

    @Test
    fun notSeenUntilMarked() {
        assertFalse(FileIntroStore(file).seen())
        FileIntroStore(file).markSeen()
        assertTrue(FileIntroStore(file).seen())
    }

    @Test
    fun showsOnFirstStartAndNotOnceFinished() {
        val store = InMemoryIntroStore()
        val settings = IntroSettings(store)
        assertTrue(settings.showing.value)

        settings.finish()
        assertFalse(settings.showing.value)
        assertTrue(store.saved)
        assertFalse(IntroSettings(store).showing.value)
    }

    @Test
    fun replayShowsItAgainWithoutForgettingItWasSeen() {
        val store = InMemoryIntroStore(saved = true)
        val settings = IntroSettings(store)
        assertFalse(settings.showing.value)

        settings.replay()
        assertTrue(settings.showing.value)
        assertTrue(store.saved)
    }

    @Test
    fun aStoreThatFailsNeitherShowsTheIntroNorStopsFinishing() {
        val broken = object : IntroStore {
            override fun seen(): Boolean = throw IllegalStateException("Unreadable")
            override fun markSeen() = throw IllegalStateException("Disk full")
        }
        val settings = IntroSettings(broken)
        assertEquals(false, settings.showing.value)

        settings.replay()
        settings.finish()
        assertFalse(settings.showing.value)
    }
}

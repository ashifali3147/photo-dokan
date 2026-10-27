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

class StageLookTest {

    private val folder = Path(SystemTemporaryDirectory, "photo-dokan-stage-${Random.nextLong().toULong()}")
    private val file = Path(folder, "settings", "stage.txt")

    @AfterTest
    fun cleanUp() {
        listOf(file, Path(folder, "settings"), folder).forEach { if (SystemFileSystem.exists(it)) SystemFileSystem.delete(it) }
    }

    @Test
    fun theLightTableIsTheDefault() {
        assertEquals(StageLook(StageStyle.LightTable), StageLook())
        assertEquals(StageLook(), FileStageLookStore(file).load(), "nothing saved yet")
    }

    @Test
    fun aLookIsWrittenAsOneLineAndReadBack() {
        val looks = listOf(StageLook(StageStyle.Solid), StageLook(StageStyle.Smoked, 0xFF5E6B7A), StageLook(StageStyle.ColourFrost, 0xFF000000))
        for (look in looks) assertEquals(look, StageLook.decode(look.encode()))
        assertEquals("Smoked FF5E6B7A", StageLook(StageStyle.Smoked, 0xFF5E6B7A).encode())
    }

    @Test
    fun anUnreadableLineGivesTheDefault() {
        assertEquals(StageLook(), StageLook.decode(""))
        assertEquals(StageLook(), StageLook.decode("Sparkly FF000000"))
        assertEquals(StageLook(StageStyle.Glass), StageLook.decode("Glass not-a-colour"), "a bad colour keeps the style")
    }

    @Test
    fun theColourIsTheStylesUnlessOneIsChosen() {
        assertEquals(0xFFE6E6E4, StageLook(StageStyle.LightTable).shownColour)
        assertEquals(STAGE_GREY, StageLook(StageStyle.Frost).shownColour)
        assertEquals(0xFF112233, StageLook(StageStyle.Frost, 0xFF112233).shownColour)
    }

    @Test
    fun onlyGreysAreNeutral() {
        assertTrue(StageStyle.entries.all { StageLook(it).neutral }, "every style's own colour is grey")
        assertTrue(StageLook(colour = 0xFFD6D6D4).neutral)
        assertFalse(StageLook(colour = 0xFF8C8172).neutral, "warm")
        assertFalse(StageLook(colour = 0xFF6E7886).neutral, "cool")
    }

    @Test
    fun savesAndLoadsTheChoice() {
        FileStageLookStore(file).save(StageLook(StageStyle.Frost, 0xFF3C3C3C))
        assertEquals(StageLook(StageStyle.Frost, 0xFF3C3C3C), FileStageLookStore(file).load())
        assertFalse(SystemFileSystem.exists(Path(folder, "settings", ".stage.txt.tmp")))
    }

    @Test
    fun aDamagedFileGivesTheDefault() {
        SystemFileSystem.createDirectories(Path(folder, "settings"))
        SystemFileSystem.sink(file).buffered().use { it.writeString("\u0000garbage") }
        assertEquals(StageLook(), FileStageLookStore(file).load())
    }

    @Test
    fun aPreviewIsSavedOnlyWhenCommitted() {
        val store = InMemoryStageLookStore()
        val settings = StageSettings(store)
        settings.preview(StageLook(StageStyle.Glass, 0xFF5E6B7A))
        assertEquals(StageLook(StageStyle.Glass, 0xFF5E6B7A), settings.look.value, "shown at once")
        assertEquals(StageLook(), store.load(), "not saved yet")
        settings.commit()
        assertEquals(StageLook(StageStyle.Glass, 0xFF5E6B7A), store.load())
        settings.choose(StageLook(StageStyle.Solid))
        assertEquals(StageLook(StageStyle.Solid), store.load(), "a choice is saved at once")
    }
}

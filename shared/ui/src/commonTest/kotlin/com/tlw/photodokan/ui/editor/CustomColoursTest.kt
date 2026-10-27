package com.tlw.photodokan.ui.editor

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.writeString
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CustomColoursTest {

    private val folder = Path(SystemTemporaryDirectory, "photo-dokan-colours-${Random.nextLong().toULong()}")
    private val file = Path(folder, "settings", "colours.txt")

    @AfterTest
    fun cleanUp() {
        listOf(file, Path(folder, "settings"), folder).forEach { if (SystemFileSystem.exists(it)) SystemFileSystem.delete(it) }
    }

    @Test
    fun noFileMeansTheFirstColours() {
        assertEquals(CustomColours(), FileCustomColoursStore(file).load())
    }

    @Test
    fun savedColoursComeBack() {
        val picked = CustomColours(backdrop = 0xFFF0D0E0, border = 0xFF00000A)
        FileCustomColoursStore(file).save(picked)
        assertEquals(picked, FileCustomColoursStore(file).load())
        FileCustomColoursStore(file).save(CustomColours(backdrop = 0xFF123456))
        assertEquals(CustomColours(backdrop = 0xFF123456), FileCustomColoursStore(file).load())
    }

    @Test
    fun aDamagedLineFallsBackOnItsOwn() {
        SystemFileSystem.createDirectories(Path(folder, "settings"))
        SystemFileSystem.sink(file).buffered().use { it.writeString("backdrop=not a colour\nborder=FF336699\nsomething else\n") }
        assertEquals(CustomColours(border = 0xFF336699), FileCustomColoursStore(file).load())
    }
}

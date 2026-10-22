package com.tlw.photodokan.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditModelTest {

    private val base = EditRecipe(presetId = PhotoSizePreset.STUDIO_SLOT.id)

    @Test
    fun defaultBackgroundBlursTheStudioBackdrop() {
        assertEquals(BackgroundMode.BLUR, base.background.mode)
        assertEquals(0.0, base.retouch)
    }

    @Test
    fun rejectsAdjustmentsOutOfRange() {
        assertFailsWith<IllegalArgumentException> { base.copy(exposure = 1.5) }
        assertFailsWith<IllegalArgumentException> { base.copy(retouch = -0.1) }
        assertFailsWith<IllegalArgumentException> { CropConfig(scale = 0.5) }
    }

    @Test
    fun undoAndRedoWalkThroughEdits() {
        val brighter = base.copy(exposure = 0.2)
        val warmer = brighter.copy(warmth = 0.1)
        var history = EditHistory.start(base).apply(brighter).apply(warmer)

        history = history.undo()
        assertEquals(brighter, history.present)
        assertTrue(history.canRedo)

        history = history.redo()
        assertEquals(warmer, history.present)
        assertFalse(history.canRedo)
    }

    @Test
    fun newEditClearsRedo() {
        val history = EditHistory.start(base)
            .apply(base.copy(exposure = 0.2))
            .undo()
            .apply(base.copy(contrast = 0.3))
        assertFalse(history.canRedo)
    }

    @Test
    fun resetIsUndoable() {
        val edited = base.copy(saturation = -0.4)
        val history = EditHistory.start(base).apply(edited).reset(base)
        assertEquals(base, history.present)
        assertEquals(edited, history.undo().present)
    }

    @Test
    fun historyKeepsOnlyTheLimit() {
        var history = EditHistory.start(0, limit = 3)
        for (i in 1..10) history = history.apply(i)
        assertEquals(listOf(7, 8, 9), history.past)
    }

    @Test
    fun ordersNeedAtLeastOneCopy() {
        assertFailsWith<IllegalArgumentException> { Order("A", "A", 0) }
    }
}

package com.tlw.photodokan.core.matte

import com.tlw.photodokan.core.model.BrushMode
import com.tlw.photodokan.core.model.MaskPoint
import com.tlw.photodokan.core.model.MaskStroke
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TouchUpTest {

    private fun filled(value: Int, width: Int = 100, height: Int = 100) = Matte(width, height, ByteArray(width * height) { value.toByte() })

    private fun dab(mode: BrushMode, x: Double, y: Double, radius: Double = 0.1) = MaskStroke(mode, radius, listOf(MaskPoint(x, y)))

    @Test
    fun keepPaintsThePersonBack() {
        val matte = filled(0).touchedUp(listOf(dab(BrushMode.KEEP, 0.5, 0.5)), 1.0)
        assertEquals(255, matte[50, 50], "the middle of the brush")
        assertEquals(255, matte[50, 43], "inside its soft rim")
        assertTrue(matte[50, 58] in 1..254, "its soft rim: ${matte[50, 58]}")
        assertEquals(0, matte[50, 62], "past the brush")
        assertEquals(0, matte[10, 10])
    }

    @Test
    fun eraseMakesItBackground() {
        val matte = filled(255).touchedUp(listOf(dab(BrushMode.ERASE, 0.5, 0.5)), 1.0)
        assertEquals(0, matte[50, 50])
        assertTrue(matte[58, 50] in 1..254)
        assertEquals(255, matte[90, 90])
    }

    @Test
    fun theBrushNeverUndoesWhatIsAlreadyThere() {
        // Keeping over the person, or erasing over the background, changes nothing.
        val half = Matte(100, 100, ByteArray(100 * 100) { if (it % 100 < 50) 0 else -1 })
        assertEquals(half, half.touchedUp(listOf(dab(BrushMode.KEEP, 0.8, 0.5), dab(BrushMode.ERASE, 0.2, 0.5)), 1.0))
    }

    @Test
    fun laterStrokesPaintOverEarlierOnes() {
        val keepThenErase = filled(128).touchedUp(listOf(dab(BrushMode.KEEP, 0.5, 0.5), dab(BrushMode.ERASE, 0.5, 0.5)), 1.0)
        assertEquals(0, keepThenErase[50, 50])
        val eraseThenKeep = filled(128).touchedUp(listOf(dab(BrushMode.ERASE, 0.5, 0.5), dab(BrushMode.KEEP, 0.5, 0.5)), 1.0)
        assertEquals(255, eraseThenKeep[50, 50])
    }

    @Test
    fun aDragPaintsAllAlongItsPath() {
        val stroke = MaskStroke(BrushMode.KEEP, 0.03, listOf(MaskPoint(0.1, 0.2), MaskPoint(0.9, 0.2), MaskPoint(0.9, 0.8)))
        val matte = filled(0).touchedUp(listOf(stroke), 1.0)
        for (x in 10 until 90) assertEquals(255, matte[x, 19], "along the top at $x")
        for (y in 20 until 80) assertEquals(255, matte[89, y], "down the side at $y")
        assertEquals(0, matte[50, 50], "not across the corner")
    }

    @Test
    fun theBrushIsRoundOnThePhotoWhateverTheMattesShape() {
        // A photo 3 wide by 4 high, its matte square: a round brush covers more matte columns.
        val matte = filled(0, 80, 80).touchedUp(listOf(dab(BrushMode.KEEP, 0.5, 0.5, radius = 0.2)), 0.75)
        val across = (0 until 80).count { matte[it, 40] == 255 }
        val down = (0 until 80).count { matte[40, it] == 255 }
        // 0.2 × 0.7 of the height across both ways: 11.2 matte rows, and 4/3 of that in columns.
        assertEquals(down * 4.0 / 3, across.toDouble(), 2.0)
    }

    @Test
    fun noStrokesIsTheMatteItself() {
        val matte = filled(100)
        assertSame(matte, matte.touchedUp(emptyList(), 1.0))
        val before = matte.alpha.copyOf()
        matte.touchedUp(listOf(dab(BrushMode.KEEP, 0.5, 0.5)), 1.0)
        assertTrue(before.contentEquals(matte.alpha), "the matte itself is never changed")
    }

    @Test
    fun aStrokeNeedsAPointAndASensibleBrush() {
        assertFailsWith<IllegalArgumentException> { MaskStroke(BrushMode.KEEP, 0.1, emptyList()) }
        assertFailsWith<IllegalArgumentException> { MaskStroke(BrushMode.KEEP, 0.0, listOf(MaskPoint(0.5, 0.5))) }
        assertFailsWith<IllegalArgumentException> { MaskStroke(BrushMode.KEEP, 0.9, listOf(MaskPoint(0.5, 0.5))) }
    }
}

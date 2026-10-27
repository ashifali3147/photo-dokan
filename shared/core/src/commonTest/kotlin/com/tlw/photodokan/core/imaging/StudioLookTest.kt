package com.tlw.photodokan.core.imaging

import com.tlw.photodokan.core.face.Face
import com.tlw.photodokan.core.face.PointPx
import com.tlw.photodokan.core.model.EditRecipe
import com.tlw.photodokan.core.model.Portrait
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StudioLookTest {

    private fun rgb(r: Int, g: Int, b: Int, a: Int = 255) = (a shl 24) or (r shl 16) or (g shl 8) or b
    private fun red(c: Int) = (c shr 16) and 0xff
    private fun green(c: Int) = (c shr 8) and 0xff
    private fun blue(c: Int) = c and 0xff
    private fun luma(c: Int) = 0.2126 * red(c) + 0.7152 * green(c) + 0.0722 * blue(c)

    /** A photo of one colour, [n] × [n]. */
    private fun flat(colour: Int, n: Int = 40) = IntArray(n * n) { colour }

    /** Brown skin in dim light: its middle at 0.4. */
    private val dim = Portrait(skinLevel = 0.4, faceHeight = 0.3)

    private fun look(colour: Int, portrait: Portrait? = dim, strength: Double = 1.0, person: Int? = null): Int {
        val n = 40
        val mask = person?.let { p -> ByteArray(n * n) { p.toByte() } }
        return StudioLook.apply(flat(colour, n), n, n, strength, portrait, mask)[20 * n + 20]
    }

    @Test
    fun offChangesNothing() {
        val photo = IntArray(100) { rgb(it, 2 * it, 255 - it) }
        assertContentEquals(photo, StudioLook.apply(photo, 10, 10, 0.0, dim))
    }

    @Test
    fun theCurveLightsTheSkinAndLeavesShadowsAndWhite() {
        val curve = StudioLook.curve(0.4, StudioLook.SKIN_TARGET)
        assertEquals(StudioLook.SKIN_TARGET.toFloat(), curve.at(0.4f), 0.002f)
        assertEquals(0f, curve.at(0f))
        assertEquals(0.05f, curve.at(0.05f), 0.001f, "shadows as shot")
        assertEquals(1f, curve.at(1f), 0.002f)
        var last = -1f
        for (i in 0..100) {
            val v = curve.at(i / 100f)
            assertTrue(v >= last, "always rising, at $i")
            last = v
        }
    }

    @Test
    fun dimSkinIsLitWhileBlackAndWhiteStay() {
        val skin = rgb(150, 98, 72) // brown skin, about 0.4
        val lit = look(skin)
        assertTrue(luma(lit) > luma(skin) * 1.25, "brighter: ${luma(skin)} to ${luma(lit)}")
        assertEquals(rgb(0, 0, 0), look(rgb(0, 0, 0)))
        assertEquals(rgb(255, 255, 255), look(rgb(255, 255, 255)))
        val shadow = rgb(14, 14, 14)
        assertEquals(luma(shadow), luma(look(shadow)), 1.0)
    }

    @Test
    fun wellLitSkinIsNotBrightened() {
        val skin = rgb(220, 160, 130)
        val fair = Portrait(skinLevel = 0.68, faceHeight = 0.3)
        assertEquals(luma(skin), luma(look(skin, fair)), 3.0)
    }

    @Test
    fun halfStrengthLightsHalfway() {
        val skin = rgb(150, 98, 72)
        val full = luma(look(skin)) - luma(skin)
        val half = luma(look(skin, strength = 0.5)) - luma(skin)
        assertTrue(half in 0.3 * full..0.7 * full, "$half of $full")
    }

    @Test
    fun fullStrengthStopsShortOfTheTarget() {
        // All the way to the target looked over-processed: full strength goes most of the way.
        val skin = rgb(150, 98, 72)
        val full = luma(look(skin)) / 255
        val all = StudioLook.SKIN_TARGET
        val from = dim.skinLevel
        assertEquals(from + StudioLook.FULL * (all - from), full, 0.02)
    }

    @Test
    fun withoutAFaceThereIsNoCurve() {
        val skin = rgb(150, 98, 72)
        assertEquals(luma(skin), luma(look(skin, portrait = null)), 3.0)
    }

    @Test
    fun darkHairAndBeardLoseTheLightsColour() {
        // Near-black hair tinted orange by a wall: neutral after.
        val hair = look(rgb(52, 36, 26))
        assertTrue(red(hair) - blue(hair) <= 12, "mostly neutral, from 26: hair ${(hair and 0xffffff).toString(16)}")
        // A navy suit or a maroon shirt is clothing, not a cast: it keeps its colour.
        val navy = look(rgb(20, 30, 70))
        assertTrue(blue(navy) - red(navy) >= 35, "navy ${(navy and 0xffffff).toString(16)}")
        val maroon = look(rgb(110, 18, 30))
        assertTrue(red(maroon) - green(maroon) >= 60, "maroon ${(maroon and 0xffffff).toString(16)}")
    }

    @Test
    fun skinLosesSomeOfItsCastButOnlyOnThePerson() {
        val orange = rgb(200, 120, 70) // skin under an orange wall's light
        val fair = Portrait(skinLevel = 0.75, faceHeight = 0.3) // no lift, so only the clean-up shows
        val cleaned = look(orange, fair, person = 255)
        fun spread(c: Int) = red(c) - blue(c)
        assertTrue(spread(cleaned) < spread(orange) - 20, "less orange: ${(cleaned and 0xffffff).toString(16)}")
        val backdrop = look(orange, fair, person = 0)
        assertTrue(abs(spread(orange) - spread(backdrop)) <= 2, "the backdrop isn't skin")
    }

    @Test
    fun aCutOutPersonStaysCutOut() {
        val photo = IntArray(400) { if (it % 20 < 10) rgb(150, 98, 72, a = 255) else rgb(150, 98, 72, a = 0) }
        val out = StudioLook.apply(photo, 20, 20, 1.0, dim)
        for (i in photo.indices) assertEquals(photo[i] ushr 24, out[i] ushr 24, "alpha at $i")
    }

    @Test
    fun edgesGetCrisper() {
        // Grey cloth meeting darker grey: after, each side of the line moves away from the other.
        val n = 40
        val photo = IntArray(n * n) { if (it % n < 20) rgb(90, 90, 90) else rgb(130, 130, 130) }
        val out = StudioLook.apply(photo, n, n, 1.0, portrait = null)
        assertTrue(luma(out[20 * n + 19]) < 90, "the dark side darker: ${luma(out[20 * n + 19])}")
        assertTrue(luma(out[20 * n + 20]) > 130, "the light side lighter: ${luma(out[20 * n + 20])}")
        assertEquals(90.0, luma(out[20 * n + 2]), 2.0, "flat areas stay")
    }

    // ---- Fair skin ----

    private fun fair(colour: Int, fairness: Double = 1.0, portrait: Portrait? = dim): Int {
        val n = 40
        return StudioLook.apply(flat(colour, n), n, n, strength = 0.0, portrait = portrait, fairness = fairness)[20 * n + 20]
    }

    @Test
    fun fairSkinIsLighterAndPaler() {
        val skin = rgb(150, 98, 72) // the face's own skin, at its measured level
        val fairer = fair(skin)
        assertTrue(luma(fairer) > luma(skin) + 30, "lighter: ${luma(skin)} to ${luma(fairer)}")
        fun colour(c: Int) = (red(c) - blue(c)).toDouble() / red(c)
        assertTrue(colour(fairer) < colour(skin) - 0.05, "paler: ${(fairer and 0xffffff).toString(16)}")
        assertTrue(luma(fair(skin, fairness = 0.5)) in luma(skin)..luma(fairer), "half way at half")
    }

    @Test
    fun beardBrowsAndHairStayDark() {
        // Much darker than the face's skin, though skin-coloured: left as it is.
        val beard = rgb(70, 46, 34)
        assertEquals(luma(beard), luma(fair(beard)), 1.0)
        assertEquals(rgb(10, 10, 10), fair(rgb(10, 10, 10)))
    }

    @Test
    fun onlySkinIsMadeFair() {
        val blueShirt = rgb(40, 80, 190)
        assertEquals(blueShirt, fair(blueShirt))
        val n = 40
        val notThePerson = StudioLook.apply(flat(rgb(150, 98, 72), n), n, n, 0.0, dim, person = ByteArray(n * n), fairness = 1.0)
        assertEquals(rgb(150, 98, 72), notThePerson[20 * n + 20], "skin-coloured backdrop")
    }

    @Test
    fun fairSkinNeedsAFace() {
        assertEquals(rgb(150, 98, 72), fair(rgb(150, 98, 72), portrait = null))
    }

    @Test
    fun darkerSkinIsMadeFairerByDefault() {
        assertEquals(0.7, StudioLook.fairnessFor(0.3), 1e-9)
        assertEquals(0.0, StudioLook.fairnessFor(0.7))
        val medium = StudioLook.fairnessFor(0.45)
        assertTrue(medium > StudioLook.fairnessFor(0.55) && medium < StudioLook.fairnessFor(0.4), "$medium")
    }

    // ---- Smoothing ----

    /** Skin of [colour] with fine texture, ±[grain] in brightness, and a dark line (a brow) across row 40. */
    private fun texturedSkin(colour: Int, grain: Int = 8, n: Int = 80) = IntArray(n * n) { i ->
        val x = i % n
        val y = i / n
        val d = if ((x * 7 + y * 13) % 5 < 2) grain else -grain
        if (y in 39..41) rgb(40, 28, 22) else rgb(red(colour) + d, green(colour) + d, blue(colour) + d)
    }

    private fun smoothed(photo: IntArray, retouch: Double = 1.0, portrait: Portrait? = dim, n: Int = 80) =
        StudioLook.apply(photo, n, n, strength = 0.0, portrait = portrait, retouch = retouch)

    /** How much brightness varies over rows [rows], away from the brow. */
    private fun grain(photo: IntArray, rows: IntRange = 10..30, n: Int = 80): Double {
        val l = rows.flatMap { y -> (10 until 70).map { x -> luma(photo[y * n + x]) } }
        val mean = l.average()
        return kotlin.math.sqrt(l.sumOf { (it - mean) * (it - mean) } / l.size)
    }

    @Test
    fun smoothingSoftensTheSkinsTexture() {
        val skin = texturedSkin(rgb(150, 98, 72))
        val before = grain(skin)
        assertTrue(grain(smoothed(skin)) < 0.5 * before, "at full: ${grain(smoothed(skin))} of $before")
        val light = grain(smoothed(skin, retouch = 0.4))
        assertTrue(light < 0.85 * before && light > grain(smoothed(skin)), "a light touch: $light of $before")
    }

    @Test
    fun smoothingKeepsEdgesSharp() {
        val photo = smoothed(texturedSkin(rgb(150, 98, 72)))
        // The brow stays dark against the skin beside it.
        assertTrue(luma(photo[40 * 80 + 40]) < 50, "the brow: ${luma(photo[40 * 80 + 40])}")
        assertTrue(luma(photo[44 * 80 + 40]) > 85, "the skin below it: ${luma(photo[44 * 80 + 40])}")
    }

    @Test
    fun onlySkinWithAFaceIsSmoothed() {
        val cloth = texturedSkin(rgb(40, 80, 190))
        assertContentEquals(cloth, smoothed(cloth), "a blue shirt keeps its weave")
        val skin = texturedSkin(rgb(150, 98, 72))
        assertContentEquals(skin, smoothed(skin, portrait = null))
    }

    @Test
    fun aPortraitIsMeasuredFromTheFace() {
        val w = 300
        val h = 300
        val face = Face(
            x = 80.0, y = 60.0, width = 140.0, height = 150.0,
            eyeLeft = PointPx(110.0, 100.0), eyeRight = PointPx(190.0, 100.0), nose = PointPx(150.0, 140.0),
            mouthLeft = PointPx(120.0, 180.0), mouthRight = PointPx(180.0, 180.0), score = 0.9,
        )
        val photo = IntArray(w * h) { rgb(150, 98, 72) }
        val portrait = assertNotNull(StudioLook.measure(photo, w, h, face))
        assertEquals(luma(rgb(150, 98, 72)) / 255, portrait.skinLevel, 0.01)
        assertEquals(0.5, portrait.faceHeight, 1e-9)
    }

    @Test
    fun theRecipeKeepsEnhanceInRange() {
        assertFailsWith<IllegalArgumentException> { EditRecipe("studio-slot", enhance = 1.5) }
        assertFailsWith<IllegalArgumentException> { EditRecipe("studio-slot", fairness = -0.1) }
        assertFailsWith<IllegalArgumentException> { EditRecipe("studio-slot", retouch = 1.1) }
        assertFailsWith<IllegalArgumentException> { Portrait(skinLevel = 1.2, faceHeight = 0.3) }
        assertFailsWith<IllegalArgumentException> { Portrait(skinLevel = 0.5, faceHeight = 0.0) }
        assertTrue(abs(EditRecipe("studio-slot").enhance) == 0.0, "off unless asked for")
    }
}

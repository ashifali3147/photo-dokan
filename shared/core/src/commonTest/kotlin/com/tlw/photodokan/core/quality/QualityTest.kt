package com.tlw.photodokan.core.quality

import com.tlw.photodokan.core.face.Face
import com.tlw.photodokan.core.face.PointPx
import com.tlw.photodokan.core.imaging.AutoLight
import com.tlw.photodokan.core.matte.Matte
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QualityTest {

    /** A face of [size] pixels with its eyes at y 100 and its mouth at y 140, centred on x 200. */
    private fun face(size: Double = 100.0, x: Double = 200.0) = Face(
        x = x - size / 2, y = 60.0, width = size, height = size * 1.2,
        eyeLeft = PointPx(x - 20, 100.0), eyeRight = PointPx(x + 20, 100.0), nose = PointPx(x, 120.0),
        mouthLeft = PointPx(x - 15, 140.0), mouthRight = PointPx(x + 15, 140.0), score = 0.9,
    )

    private val good = Findings(
        faces = listOf(face()),
        headFits = true,
        tiltDegrees = 2.0,
        cropPixels = 800.0,
        neededPixels = 402.0,
        light = AutoLight.Suggestion(0.1, 0.0, -0.2),
        cutout = CutoutStats(faceCovered = 1.0, cornersKept = 0.0, edgeWidth = 0.004),
        millis = 900,
    )

    private fun problems(found: Findings) = QualityChecks.problems(found)

    @Test
    fun aGoodPhotoHasNoProblems() = assertEquals(emptyList(), problems(good))

    @Test
    fun eachKindOfTroubleIsNamed() {
        assertEquals(listOf(Problem.NO_FACE), problems(Findings(faces = emptyList())))
        assertEquals(listOf(Problem.SEVERAL_FACES), problems(good.copy(faces = listOf(face(), face(60.0, x = 400.0)))))
        assertEquals(listOf(Problem.HEAD_AT_EDGE), problems(good.copy(headFits = false)))
        assertEquals(listOf(Problem.HEAD_TILTED), problems(good.copy(tiltDegrees = -12.0)))
        assertEquals(listOf(Problem.LOW_RESOLUTION), problems(good.copy(cropPixels = 300.0)))
        assertEquals(listOf(Problem.SKIN_UNREADABLE), problems(good.copy(light = null)))
        assertEquals(listOf(Problem.POOR_LIGHT), problems(good.copy(light = AutoLight.Suggestion(-0.6, 0.0, 0.0))))
        assertEquals(listOf(Problem.COLOUR_CAST), problems(good.copy(light = AutoLight.Suggestion(0.0, 0.0, 0.7))))
        assertEquals(listOf(Problem.CUTOUT_MISSES_FACE), problems(good.copy(cutout = good.cutout!!.copy(faceCovered = 0.7))))
        assertEquals(listOf(Problem.CUTOUT_KEEPS_BACKGROUND), problems(good.copy(cutout = good.cutout!!.copy(cornersKept = 0.5))))
        assertEquals(listOf(Problem.ROUGH_EDGE), problems(good.copy(cutout = good.cutout!!.copy(edgeWidth = 0.03))))
        assertEquals(listOf(Problem.SLOW), problems(good.copy(millis = 4_000)))
    }

    @Test
    fun smallFacesBehindTheCustomerDontCount() {
        assertEquals(emptyList(), problems(good.copy(faces = listOf(face(), face(40.0, x = 400.0)))))
    }

    @Test
    fun aPhotoCanHaveSeveralProblems() {
        val bad = good.copy(headFits = false, light = AutoLight.Suggestion(0.8, -0.6, 0.0), millis = 5_000)
        assertEquals(listOf(Problem.HEAD_AT_EDGE, Problem.POOR_LIGHT, Problem.COLOUR_CAST, Problem.SLOW), problems(bad))
    }

    @Test
    fun withoutAFaceOnlyTheCutoutsBackgroundIsJudged() {
        val found = Findings(faces = emptyList(), cutout = CutoutStats(faceCovered = 0.0, cornersKept = 0.6, edgeWidth = 0.0))
        assertEquals(listOf(Problem.NO_FACE, Problem.CUTOUT_KEEPS_BACKGROUND), problems(found))
    }

    // ---- Measuring the cut-out ----

    /** A 400 × 400 photo's matte, 100 × 100: the person is a block from x 25 to 74, y 20 down. */
    private fun person(soft: Int = 0) = Matte(100, 100, ByteArray(100 * 100) { i ->
        val x = i % 100
        val y = i / 100
        val inside = minOf(x - 25, 74 - x, y - 20)
        when {
            inside >= soft -> -1
            inside < -soft -> 0
            else -> (128 + 127 * inside / (soft + 1)).toByte()
        }
    })

    @Test
    fun aCleanCutoutCoversTheFaceAndLeavesTheCorners() {
        val stats = CutoutStats.of(person(), face(), 400, 400)
        assertEquals(1.0, stats.faceCovered)
        assertEquals(0.0, stats.cornersKept)
        assertEquals(0.0, stats.edgeWidth)
    }

    @Test
    fun aSoftEdgeIsMeasuredAsAShareOfTheHeight() {
        val stats = CutoutStats.of(person(soft = 4), face(), 400, 400)
        // About 8 unsure matte pixels across the outline, of 100.
        assertTrue(stats.edgeWidth in 0.05..0.1, "${stats.edgeWidth}")
    }

    @Test
    fun aCutoutThatMissesTheFaceOrKeepsTheBackdropShows() {
        val holed = Matte(100, 100, person().alpha.copyOf().also { a -> for (y in 25..35) for (x in 45..55) a[y * 100 + x] = 0 })
        assertTrue(CutoutStats.of(holed, face(), 400, 400).faceCovered < 0.7)
        val everything = Matte(100, 100, ByteArray(100 * 100) { -1 })
        assertEquals(1.0, CutoutStats.of(everything, face(), 400, 400).cornersKept)
    }
}

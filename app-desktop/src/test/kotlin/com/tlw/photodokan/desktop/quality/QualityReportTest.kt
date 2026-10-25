package com.tlw.photodokan.desktop.quality

import com.tlw.photodokan.core.face.Face
import com.tlw.photodokan.core.face.PointPx
import com.tlw.photodokan.core.imaging.AutoLight
import com.tlw.photodokan.core.matte.Matte
import com.tlw.photodokan.core.quality.CutoutStats
import com.tlw.photodokan.core.quality.Findings
import com.tlw.photodokan.core.quality.Problem
import com.tlw.photodokan.vision.FaceFinder
import com.tlw.photodokan.vision.PortraitMatter
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QualityReportTest {

    private val folder: File = Files.createTempDirectory("photo-dokan-quality").toFile()
    private val photos = File(folder, "photos").apply { mkdirs() }
    private val out = File(photos, "quality-report")

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    /** A face in the middle of a 1200 × 1800 photo, its eyes 120 pixels above its mouth; or of a photo [k] times that size. */
    private fun face(k: Double = 1.0) = Face(
        x = 450 * k, y = 420 * k, width = 300 * k, height = 360 * k,
        eyeLeft = PointPx(540 * k, 540 * k), eyeRight = PointPx(660 * k, 540 * k), nose = PointPx(600 * k, 600 * k),
        mouthLeft = PointPx(555 * k, 660 * k), mouthRight = PointPx(645 * k, 660 * k), score = 0.9,
    )

    /** Finds the faces it is told to on every photo. */
    private class FakeFinder(var found: List<Face>) : FaceFinder {
        override fun find(argb: IntArray, width: Int, height: Int) = found
    }

    /** The middle of every photo is the person, from a little above the face down. */
    private class FakeMatter : PortraitMatter {
        override fun matte(argb: IntArray, width: Int, height: Int) =
            Matte(40, 60, ByteArray(40 * 60) { i -> if (i % 40 in 10 until 30 && i / 40 >= 10) -1 else 0 })
    }

    /** A [width] × [height] photo of brown skin, written as [name]. */
    private fun photo(name: String, width: Int = 1200, height: Int = 1800) = File(photos, name).also { file ->
        file.parentFile.mkdirs()
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) for (x in 0 until width) image.setRGB(x, y, (232 shl 16) or (160 shl 8) or 122)
        ImageIO.write(image, if (name.endsWith("png")) "png" else "jpg", file)
    }

    private val finder = FakeFinder(emptyList())
    private val runner = QualityRunner(finder, FakeMatter())

    @Test
    fun everyPhotoInTheFolderIsFoundInOrderButNotTheReport() {
        photo("b.jpg"); photo("A.PNG"); photo("day 2/c.jpeg")
        File(photos, "notes.txt").writeText("not a photo")
        photo("quality-report/pictures/photo-1-original.jpg") // last run's report
        val found = QualityRunner.photosIn(photos, skip = out).map { it.relativeTo(photos).path }
        assertEquals(listOf("A.PNG", "b.jpg", "day 2/c.jpeg"), found)
    }

    @Test
    fun aPhotoIsCheckedAsTheEditorOpensIt() {
        finder.found = listOf(face())
        val result = runner.check(photo("customer.jpg"))
        val findings = result.findings!!
        assertEquals(1, findings.faces.size)
        assertEquals(true, findings.headFits)
        assertEquals(AutoLight.Suggestion(0.0, 0.0, 0.0), findings.light, "natural skin, well lit")
        assertEquals(1.0, findings.cutout!!.faceCovered)
        assertTrue(findings.cropPixels!! > findings.neededPixels!!, "${findings.cropPixels} of ${findings.neededPixels}")
        assertEquals(emptyList(), result.problems)
        val pictures = result.pictures!!
        assertEquals(QualityRunner.THUMBNAIL * 2, pictures.original.height)
        assertEquals(QualityRunner.THUMBNAIL * 2, pictures.onWhite!!.height)
        // On white, the backdrop beside the person is white.
        assertEquals(0xffffff, pictures.onWhite!!.getRGB(2, pictures.onWhite!!.height / 2) and 0xffffff)
    }

    @Test
    fun aSmallPhotoHasTooFewPixelsToPrint() {
        // The same photo a third the size: its crop is too few pixels for a 300 dpi print.
        finder.found = listOf(face(k = 1.0 / 3))
        assertEquals(listOf(Problem.LOW_RESOLUTION), runner.check(photo("small.jpg", 400, 600)).problems)
    }

    @Test
    fun aPhotoWithoutAFaceOrNotAPhotoIsReported() {
        assertEquals(listOf(Problem.NO_FACE), runner.check(photo("back.jpg")).problems)
        val broken = File(photos, "broken.jpg").apply { writeText("not a photo") }
        assertEquals(listOf(Problem.UNREADABLE), runner.check(broken).problems)
    }

    @Test
    fun theReportListsProblemsByTypeAndEveryPhoto() {
        finder.found = listOf(face())
        val good = runner.check(photo("0001.jpg"))
        finder.found = emptyList()
        val noFace = runner.check(photo("0002 <Ramesh>.jpg"))
        val broken = runner.check(File(photos, "0003.jpg").apply { writeText("x") })

        val report = QualityReport(out).write(photos, listOf(good, noFace, broken), LocalDateTime.of(2026, 10, 25, 14, 30))

        val html = report.readText()
        assertTrue("3 photos checked: 1 passed, 2 with problems." in html)
        assertTrue("<b>No face found</b></td><td class=n>1</td>" in html, "the problem, counted")
        assertTrue("<a href=\"#photo-2\">0002 &lt;Ramesh&gt;.jpg</a>" in html, "linked, and names escaped")
        assertTrue("25 Oct 2026, 14:30" in html)
        assertTrue(File(out, "pictures/photo-1-white.jpg").isFile)
        assertTrue(!File(out, "pictures/photo-3-original.jpg").exists(), "no pictures of a photo that wouldn't open")

        val csv = File(out, "results.csv").readLines()
        assertEquals(4, csv.size)
        assertTrue(csv[1].startsWith("0001.jpg,,1,true,"), csv[1])
        assertTrue(csv[2].startsWith("0002 <Ramesh>.jpg,NO_FACE,0,"), csv[2])
        assertTrue(csv[3].startsWith("0003.jpg,UNREADABLE,"), csv[3])
    }

    @Test
    fun theCsvQuotesWhatWouldBreakIt() {
        val result = PhotoResult(File(photos, "a, b.jpg"), Findings(faces = emptyList(), cutout = CutoutStats(0.0, 0.0, 0.0)), listOf(Problem.FAILED), error = "said \"no\"")
        val row = QualityReport(out).csv(photos, listOf(result)).lines()[1]
        assertTrue(row.startsWith("\"a, b.jpg\",FAILED,0,"), row)
        assertTrue(row.endsWith(",\"said \"\"no\"\"\""), row)
    }
}

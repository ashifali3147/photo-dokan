package com.tlw.photodokan.desktop.quality

import com.tlw.photodokan.vision.OnnxFaceFinder
import com.tlw.photodokan.vision.OnnxPortraitMatter
import com.tlw.photodokan.vision.ResourceModelFiles
import com.tlw.photodokan.vision.VisionModels
import java.io.File
import kotlin.system.exitProcess

/**
 * Checks every photo in a folder the way the editor would open it, and writes a quality report:
 *
 *     ./gradlew :app-desktop:qualityReport -Pphotos=/path/to/test-photos [-Preport=/path/to/report]
 *
 * The report goes into a quality-report folder inside the photo folder unless told otherwise.
 * Use only photos customers have agreed to; the report shows them, and it stays on this computer.
 */
fun main(args: Array<String>) {
    val photos = args.getOrNull(0)?.let(::File)
    if (photos == null || !photos.isDirectory) {
        System.err.println("Give a folder of photos: ./gradlew :app-desktop:qualityReport -Pphotos=/path/to/photos")
        exitProcess(2)
    }
    val out = args.getOrNull(1)?.let(::File) ?: File(photos, "quality-report")
    val files = QualityRunner.photosIn(photos, skip = out)
    if (files.isEmpty()) {
        System.err.println("No JPEG or PNG photos in ${photos.path}.")
        exitProcess(2)
    }
    out.deleteRecursively()
    out.mkdirs()
    VisionModels(ResourceModelFiles()).use { models ->
        val runner = QualityRunner(OnnxFaceFinder(models), OnnxPortraitMatter(models))
        val results = files.mapIndexed { i, file ->
            runner.check(file).also { println("${i + 1}/${files.size} ${file.relativeTo(photos).path}: ${it.problems.joinToString { p -> p.title }.ifEmpty { "passed" }}") }
        }
        val report = QualityReport(out).write(photos, results)
        println("Report: ${report.path}")
    }
}

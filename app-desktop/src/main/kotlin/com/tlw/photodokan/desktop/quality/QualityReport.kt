package com.tlw.photodokan.desktop.quality

import com.tlw.photodokan.core.quality.Findings
import com.tlw.photodokan.core.quality.Problem
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Writes the quality report for a run into [folder]: report.html to read (it works offline, with
 * its pictures beside it in pictures/), and results.csv to compare one run with the next.
 * Customer photos stay on this computer: nothing here goes anywhere else.
 */
class QualityReport(private val folder: File) {

    fun write(photos: File, results: List<PhotoResult>, made: LocalDateTime = LocalDateTime.now()): File {
        val pictures = File(folder, "pictures").apply { mkdirs() }
        results.forEachIndexed { i, result ->
            val p = result.pictures ?: return@forEachIndexed
            for ((name, image) in listOf("original" to p.original, "cutout" to p.cutout, "opened" to p.opened, "white" to p.onWhite)) {
                if (image != null) ImageIO.write(image, "jpg", File(pictures, "${id(i)}-$name.jpg"))
            }
        }
        File(folder, "results.csv").writeText(csv(photos, results))
        return File(folder, "report.html").apply { writeText(html(photos, results, made)) }
    }

    /** One row per photo: what was found and the problems it makes. */
    fun csv(photos: File, results: List<PhotoResult>): String = buildString {
        appendLine("photo,problems,faces,head_fits,tilt_degrees,crop_pixels,needed_pixels,exposure,warmth,tint,face_covered,corners_kept,edge_width,millis,error")
        for (r in results) {
            val f = r.findings
            val cells = listOf(
                r.file.relativeTo(photos).path,
                r.problems.joinToString(";") { it.name },
                f?.faces?.size,
                f?.headFits,
                f?.tiltDegrees?.let { number(it, 1) },
                f?.cropPixels?.let { number(it, 0) },
                f?.neededPixels?.let { number(it, 0) },
                f?.light?.exposure,
                f?.light?.warmth,
                f?.light?.tint,
                f?.cutout?.faceCovered?.let { number(it, 3) },
                f?.cutout?.cornersKept?.let { number(it, 3) },
                f?.cutout?.edgeWidth?.let { number(it, 4) },
                f?.millis,
                r.error,
            )
            appendLine(cells.joinToString(",") { cell(it?.toString() ?: "") })
        }
    }

    fun html(photos: File, results: List<PhotoResult>, made: LocalDateTime): String = buildString {
        val failed = results.count { it.problems.isNotEmpty() }
        appendLine("<!doctype html><html lang=en><head><meta charset=utf-8><meta name=viewport content=\"width=device-width,initial-scale=1\">")
        appendLine("<title>Photo Dokan quality report</title><style>$STYLE</style></head><body><main>")
        appendLine("<h1>Quality report</h1>")
        appendLine("<p class=muted>${escape(photos.path)} · ${made.format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH))}</p>")
        appendLine("<p class=lead>${results.size} photos checked: ${results.size - failed} passed, $failed with problems.</p>")

        // Problems by type, the most common first, each with links to its photos.
        val byProblem = Problem.entries.map { p -> p to results.withIndex().filter { p in it.value.problems } }.filter { it.second.isNotEmpty() }
            .sortedByDescending { it.second.size }
        appendLine("<h2>Problems by type</h2>")
        if (byProblem.isEmpty()) {
            appendLine("<p>No problems found.</p>")
        } else {
            appendLine("<table><thead><tr><th>Problem</th><th class=n>Photos</th><th>What it means</th><th>Which</th></tr></thead><tbody>")
            for ((problem, hits) in byProblem) {
                val links = hits.joinToString(" ") { (i, r) -> "<a href=\"#${id(i)}\">${escape(r.file.name)}</a>" }
                appendLine("<tr><td><b>${escape(problem.title)}</b></td><td class=n>${hits.size}</td><td>${escape(problem.meaning)}</td><td class=links>$links</td></tr>")
            }
            appendLine("</tbody></table>")
        }
        val times = results.mapNotNull { it.findings?.millis }.sorted()
        if (times.isNotEmpty()) {
            appendLine("<p class=muted>Time per photo on this computer: median ${times[times.size / 2]} ms, slowest ${times.last()} ms.</p>")
        }

        appendLine("<h2>Every photo</h2>")
        appendLine("<p class=muted>Each row: the photo with the crop (blue) and faces found (yellow), the cut-out, the crop as it opens, and on white, where cut-out mistakes show most.</p>")
        results.forEachIndexed { i, r ->
            appendLine("<section id=\"${id(i)}\"><h3>${escape(r.file.relativeTo(photos).path)}</h3>")
            if (r.problems.isEmpty()) {
                appendLine("<p><span class=\"pill ok\">Passed</span></p>")
            } else {
                appendLine("<p>" + r.problems.joinToString(" ") { "<span class=\"pill bad\" title=\"${escape(it.meaning)}\">${escape(it.title)}</span>" } + "</p>")
            }
            r.error?.let { appendLine("<p class=error>${escape(it)}</p>") }
            r.pictures?.let { p ->
                appendLine("<div class=pictures>")
                for ((name, label, present) in listOf(
                    Triple("original", "Photo", true), Triple("cutout", "Cut-out", p.cutout != null),
                    Triple("opened", "As it opens", true), Triple("white", "On white", p.onWhite != null),
                )) {
                    if (present) appendLine("<figure><img src=\"pictures/${id(i)}-$name.jpg\" alt=\"$label\" loading=lazy><figcaption>$label</figcaption></figure>")
                }
                appendLine("</div>")
            }
            r.findings?.let { f -> appendLine("<p class=muted>${escape(details(f))}</p>") }
            appendLine("</section>")
        }
        appendLine("</main></body></html>")
    }

    private fun details(f: Findings): String = listOfNotNull(
        "${f.faces.size} face${if (f.faces.size == 1) "" else "s"}",
        f.tiltDegrees?.let { "turned ${number(it, 1)}°" },
        f.cropPixels?.let { "crop ${number(it, 0)} px high of ${number(f.neededPixels ?: 0.0, 0)} needed" },
        f.light?.let { "light: brightness ${slider(it.exposure)}, warmth ${slider(it.warmth)}, tint ${slider(it.tint)}" },
        f.cutout?.let { "cut-out: face ${percent(it.faceCovered)}, corners ${percent(it.cornersKept)}, edge ${number(it.edgeWidth * 100, 2)} % of height" },
        "${f.millis} ms",
    ).joinToString(" · ")

    private fun id(i: Int) = "photo-${i + 1}"

    private fun number(value: Double, places: Int): String {
        val scale = 10.0.pow(places)
        // Rounded first, and + 0.0 turns −0.0 into 0.0, so a straight photo isn't "turned −0.0°".
        return String.format(Locale.ROOT, "%.${places}f", (value * scale).roundToLong() / scale + 0.0)
    }
    private fun percent(value: Double) = "${(value * 100).toInt()} %"
    private fun slider(value: Double) = (value * 100).toInt().let { if (it > 0) "+$it" else "$it" }

    private fun cell(text: String) = if (text.any { it == ',' || it == '"' || it == '\n' }) "\"" + text.replace("\"", "\"\"") + "\"" else text

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private companion object {
        const val STYLE = """
:root{--bg:#f6f7f9;--fg:#15191f;--muted:#5b6472;--line:#d9dee5;--card:#fff;--bad:#b3261e;--ok:#1f7a4d;--accent:#2457a6}
@media (prefers-color-scheme:dark){:root{--bg:#10141a;--fg:#e7ebf0;--muted:#9aa4b2;--line:#2b3442;--card:#171d26;--bad:#f2867e;--ok:#5cc18f;--accent:#7ea8ec}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.5 system-ui,-apple-system,"Segoe UI",sans-serif}
main{max-width:1100px;margin:0 auto;padding:24px 16px 64px}h1{margin:0 0 4px}h2{margin-top:32px}h3{margin:0 0 6px;font-size:16px;word-break:break-all}
a{color:var(--accent)}.muted{color:var(--muted)}.lead{font-size:18px}.error{color:var(--bad);font-family:ui-monospace,Consolas,monospace;font-size:13px}
table{width:100%;border-collapse:collapse;background:var(--card);border:1px solid var(--line)}th,td{padding:8px 10px;border-bottom:1px solid var(--line);text-align:left;vertical-align:top}
th{font-size:12px;text-transform:uppercase;letter-spacing:.05em;color:var(--muted)}.n{text-align:right;font-variant-numeric:tabular-nums}.links{font-size:13px}
section{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:14px 16px;margin:12px 0;scroll-margin-top:12px}
.pill{display:inline-block;padding:2px 8px;border-radius:999px;font-size:12px;font-weight:600;margin:0 4px 4px 0}
.bad{color:var(--bad);background:color-mix(in srgb,var(--bad) 14%,transparent)}.ok{color:var(--ok);background:color-mix(in srgb,var(--ok) 14%,transparent)}
.pictures{display:flex;flex-wrap:wrap;gap:10px}figure{margin:0}figure img{height:180px;width:auto;max-width:100%;display:block;border:1px solid var(--line);border-radius:4px;background:#fff}
figcaption{font-size:12px;color:var(--muted)}
"""
    }
}

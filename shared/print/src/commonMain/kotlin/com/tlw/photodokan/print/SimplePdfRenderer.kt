package com.tlw.photodokan.print

import com.tlw.photodokan.core.geometry.mmToPoints
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * A small PDF writer in plain Kotlin, the same on every platform. It draws only what a
 * [PrintDocument] needs: JPEG photos embedded exactly as they are (never decoded and squeezed
 * again), straight lines, outlines and one line of Helvetica text. Pages keep their exact size
 * in points, so A4 is 595.2756 × 841.8898, the same as the desktop's PDFBox output.
 *
 * PDF puts the origin at the bottom-left, so every y is flipped from the top-down mm layout.
 */
class SimplePdfRenderer : PdfRenderer {

    override fun render(document: PrintDocument): ByteArray {
        val pdf = PdfWriter()
        val catalog = pdf.reserve()
        val pages = pdf.reserve()
        val font = pdf.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")

        // A photo used on several pages (an overflowing order) is stored once.
        val images = mutableListOf<Pair<ByteArray, Int>>()
        fun imageFor(jpeg: ByteArray): Int = images.firstOrNull { (data, _) -> data === jpeg || data.contentEquals(jpeg) }?.second
            ?: pdf.addStream(Jpeg.dictionary(jpeg), jpeg).also { images += jpeg to it }

        val pageIds = document.pages.map { page ->
            val height = mmToPoints(page.paper.height)
            val used = mutableMapOf<Int, String>()
            val content = StringBuilder()
            for (item in page.items) when (item) {
                is PageItem.Image -> {
                    val name = used.getOrPut(imageFor(item.jpeg)) { "Im${used.size + 1}" }
                    val r = item.rect
                    content.append("q ").append(num(mmToPoints(r.width))).append(" 0 0 ").append(num(mmToPoints(r.height)))
                        .append(' ').append(num(mmToPoints(r.x))).append(' ').append(num(height - mmToPoints(r.bottom)))
                        .append(" cm /").append(name).append(" Do Q\n")
                }
                is PageItem.Line -> {
                    val l = item.line
                    content.append(num(mmToPoints(item.widthMm))).append(" w ").append(num(item.gray)).append(" G ")
                        .append(num(mmToPoints(l.x1))).append(' ').append(num(height - mmToPoints(l.y1))).append(" m ")
                        .append(num(mmToPoints(l.x2))).append(' ').append(num(height - mmToPoints(l.y2))).append(" l S\n")
                }
                is PageItem.Box -> {
                    val r = item.rect
                    content.append(num(mmToPoints(item.widthMm))).append(" w ").append(num(item.gray)).append(" G ")
                        .append(num(mmToPoints(r.x))).append(' ').append(num(height - mmToPoints(r.bottom))).append(' ')
                        .append(num(mmToPoints(r.width))).append(' ').append(num(mmToPoints(r.height))).append(" re S\n")
                }
                is PageItem.Text -> {
                    content.append("BT /F1 ").append(num(mmToPoints(item.sizeMm))).append(" Tf ").append(num(item.gray)).append(" g ")
                        .append(num(mmToPoints(item.x))).append(' ').append(num(height - mmToPoints(item.baselineY))).append(" Td (")
                        .append(pdfText(item.text)).append(") Tj ET\n")
                }
            }
            val bytes = content.toString().encodeToByteArray()
            val contents = pdf.addStream("<< /Length ${bytes.size} >>", bytes)
            val xObjects = used.entries.joinToString(" ") { (id, name) -> "/$name $id 0 R" }
            pdf.add(
                "<< /Type /Page /Parent $pages 0 R /MediaBox [0 0 ${num(mmToPoints(page.paper.width))} ${num(height)}]" +
                    " /Resources << /Font << /F1 $font 0 R >> /XObject << $xObjects >> >> /Contents $contents 0 R >>",
            )
        }
        pdf.set(pages, "<< /Type /Pages /Kids [${pageIds.joinToString(" ") { "$it 0 R" }}] /Count ${pageIds.size} >>")
        pdf.set(catalog, "<< /Type /Catalog /Pages $pages 0 R >>")
        return pdf.finish(root = catalog)
    }

    internal companion object {
        /** Up to four decimals, never an exponent: 595.2756, 0.6, 12. A ten-thousandth of a point is 35 nm. */
        fun num(value: Double): String {
            val scaled = (abs(value) * 10_000).roundToLong()
            if (scaled == 0L) return "0"
            val whole = scaled / 10_000
            val fraction = (scaled % 10_000).toString().padStart(4, '0').trimEnd('0')
            val sign = if (value < 0) "-" else ""
            return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole.$fraction"
        }

        /**
         * [text] as a PDF string in WinAnsi: Latin-1 characters such as × and ± keep their code,
         * anything beyond becomes "?", and the characters PDF strings treat specially are escaped.
         */
        fun pdfText(text: String): String = buildString {
            for (c in text) {
                when {
                    c == '\\' || c == '(' || c == ')' -> append('\\').append(c)
                    c.code in 32..126 -> append(c)
                    c.code in 160..255 -> append('\\').append(c.code.toString(8).padStart(3, '0'))
                    else -> append('?')
                }
            }
        }
    }
}

/** Collects numbered objects and writes them with the cross-reference table PDF readers need. */
private class PdfWriter {
    private val objects = mutableListOf<ByteArray?>()

    /** Numbers an object to be [set] later, e.g. one that lists objects not written yet. */
    fun reserve(): Int {
        objects += null
        return objects.size
    }

    fun set(id: Int, dictionary: String) {
        objects[id - 1] = dictionary.encodeToByteArray()
    }

    fun add(dictionary: String): Int = reserve().also { set(it, dictionary) }

    fun addStream(dictionary: String, data: ByteArray): Int = reserve().also {
        objects[it - 1] = dictionary.encodeToByteArray() + "\nstream\n".encodeToByteArray() + data + "\nendstream".encodeToByteArray()
    }

    fun finish(root: Int): ByteArray {
        val out = Bytes()
        // The comment of four high bytes tells transfer tools the file is binary.
        out.write("%PDF-1.4\n%".encodeToByteArray() + byteArrayOf(0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte()) + "\n".encodeToByteArray())
        val offsets = objects.mapIndexed { i, body ->
            out.size.also {
                out.write("${i + 1} 0 obj\n".encodeToByteArray())
                out.write(checkNotNull(body) { "PDF object ${i + 1} was reserved but never set" })
                out.write("\nendobj\n".encodeToByteArray())
            }
        }
        val xref = out.size
        out.write("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n".encodeToByteArray())
        for (offset in offsets) out.write("${offset.toString().padStart(10, '0')} 00000 n \n".encodeToByteArray())
        out.write("trailer\n<< /Size ${objects.size + 1} /Root $root 0 R >>\nstartxref\n$xref\n%%EOF\n".encodeToByteArray())
        return out.toByteArray()
    }
}

/** A growing byte buffer, since common Kotlin has no ByteArrayOutputStream. */
private class Bytes {
    private var buffer = ByteArray(64 * 1024)
    var size = 0
        private set

    fun write(bytes: ByteArray) {
        if (size + bytes.size > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, size + bytes.size))
        bytes.copyInto(buffer, size)
        size += bytes.size
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)
}

/** Reads what a PDF needs to know about a JPEG from its frame header. */
internal object Jpeg {

    /** The image dictionary for embedding [jpeg] as it is, decoded by the reader (DCTDecode). */
    fun dictionary(jpeg: ByteArray): String {
        val frame = frame(jpeg) ?: throw PrintException("A photo could not be read for printing.")
        val colorSpace = when (frame.components) {
            1 -> "/DeviceGray"
            4 -> "/DeviceCMYK /Decode [1 0 1 0 1 0 1 0]" // Adobe CMYK JPEGs store inverted values
            else -> "/DeviceRGB"
        }
        return "<< /Type /XObject /Subtype /Image /Width ${frame.width} /Height ${frame.height} /ColorSpace $colorSpace" +
            " /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>"
    }

    data class Frame(val width: Int, val height: Int, val components: Int)

    /** The size and colour channels from the first start-of-frame marker, or null when [jpeg] isn't a JPEG. */
    fun frame(jpeg: ByteArray): Frame? {
        fun byte(i: Int) = jpeg[i].toInt() and 0xFF
        fun word(i: Int) = byte(i) shl 8 or byte(i + 1)
        if (jpeg.size < 4 || byte(0) != 0xFF || byte(1) != 0xD8) return null
        var i = 2
        while (i + 4 <= jpeg.size) {
            if (byte(i) != 0xFF) return null
            val marker = byte(i + 1)
            when {
                marker == 0xFF -> { i++; continue } // padding
                marker == 0xD8 || marker in 0xD0..0xD7 || marker == 0x01 -> { i += 2; continue } // no length
                marker == 0xD9 || marker == 0xDA -> return null // end of image, or scan data before any frame
            }
            val length = word(i + 2)
            // SOF0..SOF15, except DHT (C4), JPG (C8) and DAC (CC), which share the range.
            if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                if (i + 9 >= jpeg.size) return null
                return Frame(width = word(i + 7), height = word(i + 5), components = byte(i + 9))
            }
            i += 2 + length
        }
        return null
    }
}

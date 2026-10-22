package com.tlw.photodokan.print

import com.tlw.photodokan.core.geometry.mmToPoints
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.io.ByteArrayOutputStream

/**
 * Desktop PDF renderer. Photos are embedded as the original JPEG data, never re-encoded.
 * PDF puts the origin at the bottom-left, so every y is flipped from the top-down mm layout.
 */
class PdfBoxRenderer : PdfRenderer {

    override fun render(document: PrintDocument): ByteArray =
        PDDocument().use { pdf ->
            for (page in document.pages) {
                val pageHeight = mmToPoints(page.paper.height)
                val pdPage = PDPage(PDRectangle(mmToPoints(page.paper.width).toFloat(), pageHeight.toFloat()))
                pdf.addPage(pdPage)
                PDPageContentStream(pdf, pdPage).use { content ->
                    for (item in page.items) {
                        when (item) {
                            is PageItem.Image -> {
                                val image = JPEGFactory.createFromByteArray(pdf, item.jpeg)
                                val r = item.rect
                                content.drawImage(
                                    image,
                                    mmToPoints(r.x).toFloat(),
                                    (pageHeight - mmToPoints(r.bottom)).toFloat(),
                                    mmToPoints(r.width).toFloat(),
                                    mmToPoints(r.height).toFloat(),
                                )
                            }
                            is PageItem.Line -> {
                                val l = item.line
                                content.setLineWidth(mmToPoints(item.widthMm).toFloat())
                                content.setStrokingColor(item.gray.toFloat())
                                content.moveTo(mmToPoints(l.x1).toFloat(), (pageHeight - mmToPoints(l.y1)).toFloat())
                                content.lineTo(mmToPoints(l.x2).toFloat(), (pageHeight - mmToPoints(l.y2)).toFloat())
                                content.stroke()
                            }
                        }
                    }
                }
            }
            ByteArrayOutputStream().also { pdf.save(it) }.toByteArray()
        }
}

package com.tlw.photodokan.print

import com.tlw.photodokan.core.model.SheetTemplate
import com.tlw.photodokan.core.packing.PackedSheet

/**
 * Builds the page for one packed sheet: every placement gets its order's photo.
 *
 * Cut lines are drawn only on a fresh sheet. A part-used sheet already has them from its
 * first print, and drawing them again would show any feed misalignment as double lines.
 */
fun sheetPage(template: SheetTemplate, sheet: PackedSheet, photoFor: (orderId: String) -> ByteArray): PrintPage {
    val photos = sheet.placements.map { it.orderId }.distinct().associateWith(photoFor)
    val images = sheet.placements.map { PageItem.Image(it.rect, photos.getValue(it.orderId)) }
    val cutLines = if (sheet.reusesPartialSheet) emptyList() else template.cutLineSegments().map { PageItem.Line(it) }
    return PrintPage(template.paper, cutLines + images)
}

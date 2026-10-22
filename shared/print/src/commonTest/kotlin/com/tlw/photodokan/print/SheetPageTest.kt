package com.tlw.photodokan.print

import com.tlw.photodokan.core.model.Order
import com.tlw.photodokan.core.model.SheetState
import com.tlw.photodokan.core.model.SheetTemplate
import com.tlw.photodokan.core.packing.PackingEngine
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SheetPageTest {

    private val template = SheetTemplate.STUDIO_A4_5X8
    private fun photo(orderId: String) = orderId.encodeToByteArray()

    @Test
    fun everyPlacementGetsItsOrdersPhotoInItsSlot() {
        val sheet = PackingEngine.pack(template, SheetState.FRESH, listOf(Order("A", "0001", 5), Order("B", "0002", 4))).sheets.single()
        val page = sheetPage(template, sheet, ::photo)

        val images = page.items.filterIsInstance<PageItem.Image>()
        assertEquals(sheet.placements.map { it.rect }, images.map { it.rect })
        for ((placement, image) in sheet.placements.zip(images)) {
            assertContentEquals(photo(placement.orderId), image.jpeg)
        }
        assertEquals(template.paper, page.paper)
    }

    @Test
    fun eachOrdersPhotoIsLoadedOnce() {
        val requested = mutableListOf<String>()
        val sheet = PackingEngine.pack(template, SheetState.FRESH, listOf(Order("A", "0001", 10), Order("B", "0002", 5))).sheets.single()
        sheetPage(template, sheet) { requested += it; photo(it) }
        assertEquals(listOf("A", "B"), requested)
    }

    @Test
    fun freshSheetGetsTheFullCutGrid() {
        val sheet = PackingEngine.pack(template, SheetState.FRESH, listOf(Order("A", "0001", 5))).sheets.single()
        val lines = sheetPage(template, sheet, ::photo).items.filterIsInstance<PageItem.Line>()
        assertEquals(template.cutLineSegments(), lines.map { it.line })
    }

    @Test
    fun reusedSheetPrintsOnlyPhotosIntoFreeSlots() {
        val partUsed = SheetState((0 until 14).toSet())
        val sheet = PackingEngine.pack(template, partUsed, listOf(Order("A", "0001", 5))).sheets.single()
        val page = sheetPage(template, sheet, ::photo)

        assertTrue(page.items.none { it is PageItem.Line })
        assertEquals((14 until 19).map(template::slotRect), page.items.map { (it as PageItem.Image).rect })
    }
}

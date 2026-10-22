package com.tlw.photodokan.print

import kotlin.test.Test
import kotlin.test.assertEquals

/** GDI itself runs only on Windows; these check the structures JNA hands to it. */
class WindowsGdiTest {

    @Test
    fun bitmapHeaderIsTheSizeWindowsExpects() {
        val header = BitmapInfoHeader(425, 402)
        assertEquals(40, header.size())
        assertEquals(40, header.biSize)
        assertEquals(-402, header.biHeight, "negative height: rows run top to bottom")
    }

    @Test
    fun docInfoIsTheSizeWindowsExpects() {
        // 64-bit DOCINFOW: an int, three pointers and an int, padded to 8 bytes.
        assertEquals(40, DocInfo("Photo Dokan").cbSize)
    }
}

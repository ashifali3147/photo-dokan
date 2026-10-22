package com.tlw.photodokan.print

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary

/** The few Windows GDI calls printing needs. Names and argument order follow the Win32 API. */
@Suppress("FunctionName")
internal interface Gdi32 : StdCallLibrary {
    fun CreateDCW(driver: WString?, device: WString, port: WString?, devMode: Pointer?): Pointer?
    fun CreateICW(driver: WString?, device: WString, port: WString?, devMode: Pointer?): Pointer?
    fun DeleteDC(hdc: Pointer): Boolean
    fun GetDeviceCaps(hdc: Pointer, index: Int): Int

    fun StartDocW(hdc: Pointer, docInfo: DocInfo): Int
    fun StartPage(hdc: Pointer): Int
    fun EndPage(hdc: Pointer): Int
    fun EndDoc(hdc: Pointer): Int
    fun AbortDoc(hdc: Pointer): Int

    fun SetStretchBltMode(hdc: Pointer, mode: Int): Int
    fun SetBrushOrgEx(hdc: Pointer, x: Int, y: Int, previous: Pointer?): Boolean
    fun StretchDIBits(
        hdc: Pointer,
        xDest: Int, yDest: Int, destWidth: Int, destHeight: Int,
        xSrc: Int, ySrc: Int, srcWidth: Int, srcHeight: Int,
        bits: IntArray, info: BitmapInfoHeader, usage: Int, rop: Int,
    ): Int

    fun CreatePen(style: Int, width: Int, color: Int): Pointer?
    fun GetStockObject(index: Int): Pointer?
    fun SelectObject(hdc: Pointer, obj: Pointer): Pointer?
    fun DeleteObject(obj: Pointer): Boolean
    fun MoveToEx(hdc: Pointer, x: Int, y: Int, previous: Pointer?): Boolean
    fun LineTo(hdc: Pointer, x: Int, y: Int): Boolean

    fun CreateFontW(
        height: Int, width: Int, escapement: Int, orientation: Int, weight: Int,
        italic: Int, underline: Int, strikeOut: Int, charSet: Int, outPrecision: Int,
        clipPrecision: Int, quality: Int, pitchAndFamily: Int, faceName: WString,
    ): Pointer?
    fun SetTextAlign(hdc: Pointer, align: Int): Int
    fun SetTextColor(hdc: Pointer, color: Int): Int
    fun SetBkMode(hdc: Pointer, mode: Int): Int
    fun TextOutW(hdc: Pointer, x: Int, y: Int, text: WString, length: Int): Boolean

    companion object {
        // GetDeviceCaps indexes
        const val HORZRES = 8
        const val VERTRES = 10
        const val LOGPIXELSX = 88
        const val LOGPIXELSY = 90
        const val PHYSICALWIDTH = 110
        const val PHYSICALHEIGHT = 111
        const val PHYSICALOFFSETX = 112
        const val PHYSICALOFFSETY = 113

        const val HALFTONE = 4
        const val DIB_RGB_COLORS = 0
        const val SRCCOPY = 0x00CC0020
        const val GDI_ERROR = -1
        const val PS_SOLID = 0
        const val NULL_BRUSH = 5
        const val FW_NORMAL = 400
        const val DEFAULT_CHARSET = 1
        const val TA_BASELINE = 24
        const val TRANSPARENT = 1
    }
}

@Suppress("FunctionName")
internal interface Winspool : StdCallLibrary {
    /** With a null buffer, sets [size] to the characters needed, including the terminating zero. */
    fun GetDefaultPrinterW(buffer: CharArray?, size: IntByReference): Boolean
}

internal object Win32 {
    val gdi: Gdi32 by lazy { Native.load("gdi32", Gdi32::class.java) }
    val spool: Winspool by lazy { Native.load("winspool.drv", Winspool::class.java) }
}

@Structure.FieldOrder("cbSize", "lpszDocName", "lpszOutput", "lpszDatatype", "fwType")
internal class DocInfo(name: String = "") : Structure() {
    @JvmField var cbSize: Int = 0
    @JvmField var lpszDocName: WString? = WString(name)
    @JvmField var lpszOutput: WString? = null
    @JvmField var lpszDatatype: WString? = null
    @JvmField var fwType: Int = 0

    init {
        cbSize = size()
    }
}

/** A 32-bit, top-down BITMAPINFOHEADER; with BI_RGB at 32 bits no colour table follows. */
@Structure.FieldOrder(
    "biSize", "biWidth", "biHeight", "biPlanes", "biBitCount", "biCompression",
    "biSizeImage", "biXPelsPerMeter", "biYPelsPerMeter", "biClrUsed", "biClrImportant",
)
internal class BitmapInfoHeader(width: Int = 0, height: Int = 0) : Structure() {
    @JvmField var biSize: Int = 0
    @JvmField var biWidth: Int = width
    /** Negative: rows run top to bottom, the same as a Java image. */
    @JvmField var biHeight: Int = -height
    @JvmField var biPlanes: Short = 1
    @JvmField var biBitCount: Short = 32
    @JvmField var biCompression: Int = 0
    @JvmField var biSizeImage: Int = 0
    @JvmField var biXPelsPerMeter: Int = 0
    @JvmField var biYPelsPerMeter: Int = 0
    @JvmField var biClrUsed: Int = 0
    @JvmField var biClrImportant: Int = 0

    init {
        biSize = size()
    }
}

package com.tlw.photodokan.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The "Calm workspace" colours. A warm paper-grey ground with white cards, and one ink-blue
 * accent. Colour is kept for meaning:
 * - Accent: what is selected, and the one main action on a screen.
 * - Ok: done, saved, on the sheet, printer ready.
 * - Warn: something needs the operator, and only that.
 * The accent and ok buttons are lit very slightly from above ([accentTop] to [accentBottom]) and
 * carry a soft glow, so the main action stands out without the rest of the screen being decorated.
 */
@Immutable
data class StudioColors(
    val isDark: Boolean,
    /** The window behind everything. */
    val base: Color,
    /** The side rail. */
    val rail: Color,
    /** Cards and panels. */
    val surface: Color,
    /** A recessed track: segmented controls, the surround of the sheet. */
    val well: Color,
    /** Dividers and card borders. */
    val line: Color,
    /** Outlines of fields and steppers, which must stay visible against [base] (3:1). */
    val outline: Color,
    val text: Color,
    val textMuted: Color,
    /** A solid dark (light theme) or light (dark theme) block, e.g. the logo. */
    val strong: Color,
    val onStrong: Color,
    val accent: Color,
    val accentTop: Color,
    val accentBottom: Color,
    val onAccent: Color,
    /** A selected chip or row: a quiet wash of [accent]. */
    val accentWash: Color,
    /** [accent] as text on [accentWash]. */
    val accentText: Color,
    /** The glow under the main action and around a selected photo. */
    val accentGlow: Color,
    val ok: Color,
    val okTop: Color,
    val okBottom: Color,
    val onOk: Color,
    val okWash: Color,
    val okGlow: Color,
    val warn: Color,
    val onWarn: Color,
    val warnWash: Color,
    /** The neutral surround of a photo being judged. The same in both themes. */
    val stage: Color,
    /** The A4 sheet. Paper is white in both themes. */
    val paper: Color,
    /** A slot on the sheet that is already printed. */
    val slotPrinted: Color,
    /** A free slot on the sheet. */
    val slotFree: Color,
    /** The outline of a slot left blank. */
    val slotBlank: Color,
    /** Round buttons floating over a photo (the camera is always dark). */
    val glass: Color,
)

/** Every colour pair a screen uses, by theme. */
object StudioPalette {
    private val Stage = Color(0xFF7A7A7A)
    private val Paper = Color(0xFFFFFFFF)
    private val SlotPrinted = Color(0xFFDAD6CE)
    private val SlotFree = Color(0xFFF1EFEA)
    private val SlotBlank = Color(0xFF8A8F98)

    val Dark = StudioColors(
        isDark = true,
        base = Color(0xFF111214),
        rail = Color(0xFF16171A),
        surface = Color(0xFF1B1C20),
        well = Color(0xFF17181B),
        line = Color(0xFF2A2C31),
        outline = Color(0xFF6B6F78),
        text = Color(0xFFEDEDEF),
        textMuted = Color(0xFFA3A7B0),
        strong = Color(0xFFEDEDEF),
        onStrong = Color(0xFF111214),
        accent = Color(0xFF5B7CFF),
        accentTop = Color(0xFF4A68EC),
        accentBottom = Color(0xFF3753D6),
        onAccent = Color.White,
        accentWash = Color(0xFF1D2542),
        accentText = Color(0xFFA9BCFF),
        accentGlow = Color(0x735B7CFF),
        ok = Color(0xFF8CD3AB),
        okTop = Color(0xFF247F52),
        okBottom = Color(0xFF1C6F46),
        onOk = Color.White,
        okWash = Color(0xFF17281F),
        okGlow = Color(0x612F9A66),
        warn = Color(0xFFE0C068),
        onWarn = Color(0xFF2A2000),
        warnWash = Color(0xFF2A2516),
        stage = Stage,
        paper = Paper,
        slotPrinted = SlotPrinted,
        slotFree = SlotFree,
        slotBlank = SlotBlank,
        glass = Color(0x24FFFFFF),
    )

    val Light = StudioColors(
        isDark = false,
        base = Color(0xFFF4F2EE),
        rail = Color(0xFFFBFAF8),
        surface = Color(0xFFFFFFFF),
        well = Color(0xFFE9E6DF),
        line = Color(0xFFE5E1DA),
        outline = Color(0xFF8C8880),
        text = Color(0xFF1B1D21),
        textMuted = Color(0xFF5F636B),
        strong = Color(0xFF1B1D21),
        onStrong = Color.White,
        accent = Color(0xFF2B50D8),
        accentTop = Color(0xFF3E62E6),
        accentBottom = Color(0xFF2447C9),
        onAccent = Color.White,
        accentWash = Color(0xFFE8EDFC),
        accentText = Color(0xFF1E3FB8),
        accentGlow = Color(0x4D2B50D8),
        ok = Color(0xFF17603B),
        okTop = Color(0xFF238053),
        okBottom = Color(0xFF1B6F44),
        onOk = Color.White,
        okWash = Color(0xFFE3F2EA),
        okGlow = Color(0x471F7A4D),
        warn = Color(0xFF7A5A00),
        onWarn = Color.White,
        warnWash = Color(0xFFFFF1D6),
        stage = Stage,
        paper = Paper,
        slotPrinted = SlotPrinted,
        slotFree = SlotFree,
        slotBlank = SlotBlank,
        glass = Color(0x99FFFFFF),
    )
}

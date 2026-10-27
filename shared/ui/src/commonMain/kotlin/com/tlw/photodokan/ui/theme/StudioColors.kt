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
    /**
     * The light that falls from the top of the window onto the ground, as if from a softbox. A
     * faint accent glow in dark, so the night theme has depth; nearly nothing in light.
     */
    val ambient: Color = Color.Transparent,
    /** A hairline of light along the top edge of raised things: cards, the dock, the main button. */
    val edgeLight: Color = Color.Transparent,
    /** The band of light that sweeps once across the main button when it changes. */
    val sheen: Color = Color.White.copy(alpha = 0.22f),
    /** How strongly glows show: fuller at night, where light reads, and quieter by day. */
    val glowStrength: Float = 1f,
)

/** Every colour pair a screen uses, by theme. */
object StudioPalette {
    private val Stage = Color(0xFF7A7A7A)
    private val Paper = Color(0xFFFFFFFF)
    private val SlotPrinted = Color(0xFFDAD6CE)
    private val SlotFree = Color(0xFFF1EFEA)
    private val SlotBlank = Color(0xFF8A8F98)

    /**
     * Night: deep ink with a little blue in it rather than flat black, so the accent's light has
     * something to fall on. Surfaces step up in the same hue; the accent is brighter and its glow
     * fuller than by day, where light reads best. The stage stays neutral grey for judging skin.
     */
    val Dark = StudioColors(
        isDark = true,
        base = Color(0xFF0B0D13),
        rail = Color(0xFF10131B),
        surface = Color(0xFF161A25),
        well = Color(0xFF0E1119),
        line = Color(0xFF262C3B),
        outline = Color(0xFF5E6882),
        text = Color(0xFFEEF1F8),
        textMuted = Color(0xFFA2AABC),
        strong = Color(0xFFEEF1F8),
        onStrong = Color(0xFF0B0D13),
        accent = Color(0xFF6E8CFF),
        // Deep enough for white text (4.5:1 even at the top); the glow around it carries the light.
        accentTop = Color(0xFF4A68EC),
        accentBottom = Color(0xFF3B56E6),
        onAccent = Color.White,
        accentWash = Color(0xFF1A2347),
        accentText = Color(0xFFB8C7FF),
        accentGlow = Color(0xA06E8CFF),
        ok = Color(0xFF7EE2AE),
        okTop = Color(0xFF1F8553),
        okBottom = Color(0xFF19744A),
        onOk = Color.White,
        okWash = Color(0xFF11271D),
        okGlow = Color(0x8C34C47E),
        warn = Color(0xFFF2C866),
        onWarn = Color(0xFF2A2000),
        warnWash = Color(0xFF2B2414),
        stage = Stage,
        paper = Paper,
        slotPrinted = SlotPrinted,
        slotFree = SlotFree,
        slotBlank = SlotBlank,
        glass = Color(0x29FFFFFF),
        ambient = Color(0x2E5B7CFF),
        edgeLight = Color(0x14FFFFFF),
        sheen = Color(0x47FFFFFF),
        glowStrength = 1f,
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
        ambient = Color(0x0F2B50D8),
        edgeLight = Color(0x99FFFFFF),
        sheen = Color(0x38FFFFFF),
        glowStrength = 0.6f,
    )
}

package com.tlw.photodokan.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The "Darkroom & Print" colours. The interface stays neutral grey so skin is judged truthfully;
 * colour appears only where it means something, each borrowed from the darkroom:
 * - Cyanotype, the first photographic print process: what is selected, active or ready to print.
 * - Fixer, the bath that makes a print permanent: done, saved, printed, checks passed.
 * - Safelight, the only light in a darkroom: the one physical action, shoot or print. At most
 *   one per screen.
 * - Caution: something needs the operator, and only that.
 * - Grey card, the 18% grey photographers meter against: the surround of a photo being judged.
 */
@Immutable
data class StudioColors(
    val isDark: Boolean,
    /** The window behind everything. */
    val base: Color,
    /** Cards and panels, from lowest to highest. */
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceHigh: Color,
    /** Dividers and card borders. */
    val line: Color,
    /** Outlines of controls, which must stay visible against [base] (3:1). */
    val outline: Color,
    val text: Color,
    val textMuted: Color,
    val cyanotype: Color,
    val onCyanotype: Color,
    /** A selected chip or row: a quiet wash of [cyanotype]. */
    val cyanotypeWash: Color,
    val fixer: Color,
    val onFixer: Color,
    val fixerWash: Color,
    val caution: Color,
    val onCaution: Color,
    val cautionWash: Color,
    /** The safelight lamp, lit from the top: a button's gradient runs from [safelightTop] to [safelightBottom]. */
    val safelight: Color,
    val safelightTop: Color,
    val safelightBottom: Color,
    val onSafelight: Color,
    /** The neutral surround of a photo being judged. The same in both themes. */
    val greyCard: Color,
    /** Film base under a contact sheet: dark in both themes, as film is. */
    val film: Color,
    /** Sprocket holes punched in the [film]. */
    val filmHole: Color,
    /** Frame numbers printed on the [film]'s edge. */
    val filmText: Color,
    /**
     * The button that moves on without printing ("Next", "Add to sheet"): the brightest thing
     * after the safelight, without its colour. Lit from the top, [goTop] to [goBottom].
     */
    val goTop: Color,
    val goBottom: Color,
    val onGo: Color,
    /** Panels are lit from above: [panelTop] to [panelBottom], with an [edgeHighlight] along the top. */
    val panelTop: Color,
    val panelBottom: Color,
    val edgeHighlight: Color,
    /** Round buttons floating over a photo or the window. */
    val glass: Color,
)

/** Every colour pair a screen uses, by theme. */
object StudioPalette {
    private val Safelight = Color(0xFFC23A1E)
    private val SafelightTop = Color(0xFFCE462A)
    private val SafelightBottom = Color(0xFFA9301A)
    private val GreyCard = Color(0xFF777777)

    val Dark = StudioColors(
        isDark = true,
        base = Color(0xFF0F0F0F),
        surface = Color(0xFF161616),
        surfaceRaised = Color(0xFF1C1C1C),
        surfaceHigh = Color(0xFF252525),
        line = Color(0xFF2C2C2C),
        outline = Color(0xFF6B6B6B),
        text = Color(0xFFEDEDED),
        textMuted = Color(0xFF9A9A9A),
        cyanotype = Color(0xFF8FB4DC),
        onCyanotype = Color(0xFF0B1B2B),
        cyanotypeWash = Color(0xFF1A232C),
        fixer = Color(0xFF8CCBA8),
        onFixer = Color(0xFF0C2017),
        fixerWash = Color(0xFF17241D),
        caution = Color(0xFFE0C068),
        onCaution = Color(0xFF2A2000),
        cautionWash = Color(0xFF2A2516),
        safelight = Safelight,
        safelightTop = SafelightTop,
        safelightBottom = SafelightBottom,
        onSafelight = Color.White,
        greyCard = GreyCard,
        film = Color(0xFF050505),
        filmHole = Color(0xFF1C1C1C),
        filmText = Color(0xFFBDBDBD),
        goTop = Color(0xFFF4F4F4),
        goBottom = Color(0xFFDADADA),
        onGo = Color(0xFF111111),
        panelTop = Color(0xFF1D1D1D),
        panelBottom = Color(0xFF161616),
        edgeHighlight = Color(0x12FFFFFF),
        glass = Color(0x0AFFFFFF),
    )

    val Light = StudioColors(
        isDark = false,
        base = Color(0xFFF7F7F7),
        surface = Color(0xFFFFFFFF),
        surfaceRaised = Color(0xFFFFFFFF),
        surfaceHigh = Color(0xFFF1F1F1),
        line = Color(0xFFE2E2E2),
        outline = Color(0xFF8A8A8A),
        text = Color(0xFF151515),
        textMuted = Color(0xFF5E5E5E),
        cyanotype = Color(0xFF1F4E79),
        onCyanotype = Color.White,
        cyanotypeWash = Color(0xFFEEF3F8),
        fixer = Color(0xFF2E6B4F),
        onFixer = Color.White,
        fixerWash = Color(0xFFE5F0EA),
        caution = Color(0xFF7A5A00),
        onCaution = Color.White,
        cautionWash = Color(0xFFFFF3DC),
        safelight = Safelight,
        safelightTop = SafelightTop,
        safelightBottom = SafelightBottom,
        onSafelight = Color.White,
        greyCard = GreyCard,
        film = Color(0xFF111111),
        filmHole = Color(0xFFF3F3F3),
        filmText = Color(0xFFBDBDBD),
        goTop = Color(0xFF2A2A2A),
        goBottom = Color(0xFF111111),
        onGo = Color.White,
        panelTop = Color(0xFFFFFFFF),
        panelBottom = Color(0xFFFAFAFA),
        edgeHighlight = Color(0xFFFFFFFF),
        glass = Color(0xFFFFFFFF),
    )
}

package com.tlw.photodokan.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The operator reads this app all day on a busy counter: every text colour must stay readable. */
class StudioPaletteTest {

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return (max(la, lb) / min(la, lb)).toDouble()
    }

    private fun assertReadable(text: Color, on: Color, what: String, minimum: Double = 4.5) {
        val ratio = contrast(text, on)
        assertTrue(ratio >= minimum, "$what: contrast ${(ratio * 100).toInt() / 100.0} is below $minimum")
    }

    private val both = listOf(StudioPalette.Dark, StudioPalette.Light)

    @Test
    fun textIsReadableOnEverySurface() {
        for (c in both) {
            for ((name, surface) in listOf("base" to c.base, "rail" to c.rail, "surface" to c.surface, "well" to c.well)) {
                assertReadable(c.text, surface, "text on $name (dark=${c.isDark})")
                assertReadable(c.textMuted, surface, "muted text on $name (dark=${c.isDark})")
            }
        }
    }

    @Test
    fun meaningColoursAreReadableAsTextAndUnderTheirOwnText() {
        for (c in both) {
            for ((name, colour) in listOf("accent" to c.accent, "ok" to c.ok, "warn" to c.warn)) {
                assertReadable(colour, c.base, "$name on base (dark=${c.isDark})")
                assertReadable(colour, c.surface, "$name on surface (dark=${c.isDark})")
            }
            assertReadable(c.accentText, c.accentWash, "accent on its wash (dark=${c.isDark})")
            assertReadable(c.ok, c.okWash, "ok on its wash (dark=${c.isDark})")
            assertReadable(c.warn, c.warnWash, "warn on its wash (dark=${c.isDark})")
            assertReadable(c.onWarn, c.warn, "on warn (dark=${c.isDark})")
            assertReadable(c.onStrong, c.strong, "on strong (dark=${c.isDark})")
        }
    }

    @Test
    fun theMainButtonIsReadableFromTopToBottom() {
        for (c in both) {
            assertReadable(c.onAccent, c.accentTop, "top of the main button (dark=${c.isDark})")
            assertReadable(c.onAccent, c.accentBottom, "bottom of the main button (dark=${c.isDark})")
            assertReadable(c.onOk, c.okTop, "top of the done button (dark=${c.isDark})")
            assertReadable(c.onOk, c.okBottom, "bottom of the done button (dark=${c.isDark})")
        }
    }

    @Test
    fun controlOutlinesStandOut() {
        for (c in both) {
            assertReadable(c.outline, c.base, "outline on base (dark=${c.isDark})", minimum = 3.0)
            assertReadable(c.outline, c.surface, "outline on surface (dark=${c.isDark})", minimum = 3.0)
        }
    }

    @Test
    fun theStageAndPaperAreNeutralSoSkinIsJudgedFairly() {
        for (colour in listOf(StudioPalette.Light.stage, StudioPalette.Dark.stage, StudioPalette.Light.paper)) {
            assertEquals(colour.red, colour.green, "no tint in $colour")
            assertEquals(colour.green, colour.blue, "no tint in $colour")
        }
        assertEquals(StudioPalette.Light.stage, StudioPalette.Dark.stage, "the stage is the same in both themes")
        assertEquals(StudioPalette.Light.paper, StudioPalette.Dark.paper, "paper is white in both themes")
    }

    @Test
    fun materialRolesFollowTheMeanings() {
        for (c in both) {
            val scheme = c.toColorScheme()
            assertEquals(c.accent, scheme.primary)
            assertEquals(c.ok, scheme.secondary)
            assertEquals(c.warn, scheme.error, "a problem must never look like the main button")
            assertEquals(c.accentWash, scheme.secondaryContainer, "what Material marks as selected is the accent, not ok")
            assertEquals(c.base, scheme.background)
            assertEquals(c.text, scheme.onSurface)
        }
    }
}

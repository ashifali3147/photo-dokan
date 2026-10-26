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
            for ((name, surface) in listOf("base" to c.base, "surface" to c.surface, "raised" to c.surfaceRaised, "high" to c.surfaceHigh)) {
                assertReadable(c.text, surface, "text on $name (dark=${c.isDark})")
                assertReadable(c.textMuted, surface, "muted text on $name (dark=${c.isDark})")
            }
        }
    }

    @Test
    fun meaningColoursAreReadableAsTextAndUnderTheirOwnText() {
        for (c in both) {
            for ((name, colour) in listOf("cyanotype" to c.cyanotype, "fixer" to c.fixer, "caution" to c.caution)) {
                assertReadable(colour, c.base, "$name on base (dark=${c.isDark})")
                assertReadable(colour, c.surface, "$name on surface (dark=${c.isDark})")
            }
            assertReadable(c.onCyanotype, c.cyanotype, "on cyanotype (dark=${c.isDark})")
            assertReadable(c.onFixer, c.fixer, "on fixer (dark=${c.isDark})")
            assertReadable(c.onCaution, c.caution, "on caution (dark=${c.isDark})")
            assertReadable(c.cyanotype, c.cyanotypeWash, "cyanotype on its wash (dark=${c.isDark})")
            assertReadable(c.fixer, c.fixerWash, "fixer on its wash (dark=${c.isDark})")
            assertReadable(c.caution, c.cautionWash, "caution on its wash (dark=${c.isDark})")
        }
    }

    @Test
    fun theSafelightButtonIsReadableFromTopToBottom() {
        for (c in both) {
            assertReadable(c.onSafelight, c.safelight, "safelight")
            assertReadable(c.onSafelight, c.safelightTop, "top of the lamp")
            assertReadable(c.onSafelight, c.safelightBottom, "bottom of the lamp")
        }
    }

    @Test
    fun controlOutlinesStandOut() {
        for (c in both) assertReadable(c.outline, c.base, "outline (dark=${c.isDark})", minimum = 3.0)
    }

    @Test
    fun surfacesAreTrulyNeutralSoSkinIsJudgedFairly() {
        for (c in both) {
            for (colour in listOf(c.base, c.surface, c.surfaceRaised, c.surfaceHigh, c.line, c.text, c.textMuted, c.greyCard)) {
                assertEquals(colour.red, colour.green, "no tint in $colour")
                assertEquals(colour.green, colour.blue, "no tint in $colour")
            }
        }
    }

    @Test
    fun materialRolesFollowTheMeanings() {
        for (c in both) {
            val scheme = c.toColorScheme()
            assertEquals(c.cyanotype, scheme.primary)
            assertEquals(c.fixer, scheme.secondary)
            assertEquals(c.safelight, scheme.tertiary)
            assertEquals(c.caution, scheme.error, "a problem must never look like the Print button")
            assertEquals(c.cyanotypeWash, scheme.secondaryContainer, "what Material marks as selected is Cyanotype, not Fixer")
            assertEquals(c.base, scheme.background)
            assertEquals(c.text, scheme.onSurface)
        }
    }
}

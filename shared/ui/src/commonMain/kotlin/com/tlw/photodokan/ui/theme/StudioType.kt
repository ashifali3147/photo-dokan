package com.tlw.photodokan.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tlw.photodokan.ui.resources.Res
import com.tlw.photodokan.ui.resources.bricolage_grotesque
import com.tlw.photodokan.ui.resources.figtree
import org.jetbrains.compose.resources.Font

/**
 * Figtree for everything the operator reads: friendly and open, clear at counter distance.
 * Bricolage Grotesque for the customer number and the large titles: it has more character than
 * the body face, so "0012" is the first thing the eye finds. Digits are tabular, so numbers line up.
 *
 * Both are bundled with the app (SIL Open Font License, see shared/ui/licences), so they work offline.
 */
@Immutable
data class StudioType(
    /** The customer number, large: "0012". */
    val numberLarge: TextStyle,
    /** The customer number under a photo, copies on a chip: "0012", "10". */
    val number: TextStyle,
    /** A small number beside a label, e.g. a slider's value or a counter. */
    val numberSmall: TextStyle,
)

@Composable
internal fun figtree(): FontFamily = variableFamily { w ->
    Font(Res.font.figtree, weight = FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
}

@Composable
internal fun bricolage(): FontFamily = variableFamily { w ->
    Font(Res.font.bricolage_grotesque, weight = FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
}

@Composable
private fun variableFamily(font: @Composable (Int) -> androidx.compose.ui.text.font.Font): FontFamily =
    // One variable font file; each weight picks its point on the weight axis.
    FontFamily(listOf(400, 500, 600, 700).map { font(it) })

/** Material's type scale in Figtree; titles and headlines in Bricolage Grotesque, set tight. */
internal fun studioTypography(sans: FontFamily, display: FontFamily): Typography {
    val base = Typography()
    fun TextStyle.sans(weight: FontWeight, tracking: Double = 0.0) =
        copy(fontFamily = sans, fontWeight = weight, letterSpacing = tracking.sp)
    fun TextStyle.display(tracking: Double) =
        copy(fontFamily = display, fontWeight = FontWeight.Bold, letterSpacing = tracking.sp, fontFeatureSettings = TABULAR)
    return Typography(
        displayLarge = base.displayLarge.display(-1.5),
        displayMedium = base.displayMedium.display(-1.0),
        displaySmall = base.displaySmall.display(-0.8),
        headlineLarge = base.headlineLarge.display(-0.8),
        headlineMedium = base.headlineMedium.display(-0.6),
        headlineSmall = base.headlineSmall.display(-0.4),
        titleLarge = base.titleLarge.sans(FontWeight.SemiBold, -0.2),
        titleMedium = base.titleMedium.sans(FontWeight.SemiBold),
        titleSmall = base.titleSmall.sans(FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.sans(FontWeight.Normal),
        bodyMedium = base.bodyMedium.sans(FontWeight.Normal),
        bodySmall = base.bodySmall.sans(FontWeight.Normal),
        labelLarge = base.labelLarge.sans(FontWeight.SemiBold),
        labelMedium = base.labelMedium.sans(FontWeight.SemiBold),
        labelSmall = base.labelSmall.sans(FontWeight.SemiBold),
    )
}

/** Tabular figures: every digit the same width, so numbers line up. */
private const val TABULAR = "tnum"

internal fun studioType(display: FontFamily) = StudioType(
    numberLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 56.sp, letterSpacing = (-1.5).sp, fontFeatureSettings = TABULAR),
    number = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.3).sp, fontFeatureSettings = TABULAR),
    numberSmall = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFeatureSettings = TABULAR),
)

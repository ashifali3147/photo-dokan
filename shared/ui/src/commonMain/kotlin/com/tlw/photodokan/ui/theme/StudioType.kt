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
import com.tlw.photodokan.ui.resources.dm_mono_medium
import com.tlw.photodokan.ui.resources.dm_mono_regular
import com.tlw.photodokan.ui.resources.instrument_sans
import org.jetbrains.compose.resources.Font

/**
 * Instrument Sans for everything the operator reads: narrow and upright like a camera's engraved
 * labels, so it reads at a glance and leaves room for the photo on a phone.
 * DM Mono for numbers ([StudioType.number]): customer numbers set like the frame numbers on the
 * edge of film (▸0012), sizes and copies. Every digit has the same width, so 0 and O or 1 and l
 * are never confused and numbers line up.
 *
 * Both are bundled with the app (SIL Open Font License, see shared/ui/licences), so they work offline.
 */
@Immutable
data class StudioType(
    /** Frame numbers, sizes, copies: "▸0012", "35 × 45 mm", "×5". */
    val number: TextStyle,
    /** A small number beside a label, e.g. a slider's value or a counter. */
    val numberSmall: TextStyle,
)

@Composable
internal fun instrumentSans(): FontFamily {
    // One variable font file; each weight picks its point on the weight axis.
    val weights = listOf(400, 500, 600, 700).map { w ->
        Font(Res.font.instrument_sans, weight = FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
    return FontFamily(weights)
}

@Composable
internal fun dmMono(): FontFamily = FontFamily(
    Font(Res.font.dm_mono_regular, FontWeight.Normal),
    Font(Res.font.dm_mono_medium, FontWeight.Medium),
)

/** Material's type scale in Instrument Sans, slightly tighter at display sizes. */
internal fun studioTypography(sans: FontFamily): Typography {
    val base = Typography()
    fun TextStyle.sans(weight: FontWeight, tracking: Double = 0.0) =
        copy(fontFamily = sans, fontWeight = weight, letterSpacing = tracking.sp)
    return Typography(
        displayLarge = base.displayLarge.sans(FontWeight.Bold, -1.5),
        displayMedium = base.displayMedium.sans(FontWeight.Bold, -1.0),
        displaySmall = base.displaySmall.sans(FontWeight.Bold, -0.8),
        headlineLarge = base.headlineLarge.sans(FontWeight.Bold, -0.6),
        headlineMedium = base.headlineMedium.sans(FontWeight.Bold, -0.5),
        headlineSmall = base.headlineSmall.sans(FontWeight.Bold, -0.3),
        titleLarge = base.titleLarge.sans(FontWeight.Bold, -0.2),
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

internal fun studioType(mono: FontFamily) = StudioType(
    number = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 17.sp, letterSpacing = 0.5.sp),
    numberSmall = TextStyle(fontFamily = mono, fontWeight = FontWeight.Normal, fontSize = 13.sp),
)

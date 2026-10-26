package com.tlw.photodokan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalStudioColors = staticCompositionLocalOf { StudioPalette.Dark }
private val LocalStudioType = staticCompositionLocalOf<StudioType> { error("No PhotoDokanTheme") }

/**
 * The app's "Darkroom & Print" theme in dark or light, as [mode] and the device decide. Material
 * components get the same colours and Instrument Sans, so existing screens follow it; new ones
 * also use [StudioTheme] for the roles Material has no name for.
 */
@Composable
fun PhotoDokanTheme(mode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val colors = if (mode.isDark(isSystemInDarkTheme())) StudioPalette.Dark else StudioPalette.Light
    val sans = instrumentSans()
    val mono = dmMono()
    val typography = remember(sans) { studioTypography(sans) }
    val type = remember(mono) { studioType(mono) }
    CompositionLocalProvider(LocalStudioColors provides colors, LocalStudioType provides type) {
        MaterialTheme(colorScheme = colors.toColorScheme(), typography = typography, content = content)
    }
}

/** The theme's own colours and number styles, inside [PhotoDokanTheme]. */
object StudioTheme {
    val colors: StudioColors
        @Composable @ReadOnlyComposable get() = LocalStudioColors.current

    val type: StudioType
        @Composable @ReadOnlyComposable get() = LocalStudioType.current
}

/**
 * Material's roles in Darkroom & Print colours: primary is Cyanotype, secondary Fixer, tertiary
 * Safelight, and error Caution, so a problem never looks like the Print button. Material marks
 * what is selected (rail item, segment, chip) with the secondary container; selection is
 * Cyanotype's job, so that container is a Cyanotype wash.
 */
internal fun StudioColors.toColorScheme(): ColorScheme {
    val scheme = if (isDark) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        primary = cyanotype,
        onPrimary = onCyanotype,
        primaryContainer = cyanotypeWash,
        onPrimaryContainer = cyanotype,
        inversePrimary = cyanotype,
        secondary = fixer,
        onSecondary = onFixer,
        secondaryContainer = cyanotypeWash,
        onSecondaryContainer = cyanotype,
        tertiary = safelight,
        onTertiary = onSafelight,
        tertiaryContainer = safelight,
        onTertiaryContainer = onSafelight,
        error = caution,
        onError = onCaution,
        errorContainer = cautionWash,
        onErrorContainer = caution,
        background = base,
        onBackground = text,
        surface = base,
        onSurface = text,
        surfaceVariant = surfaceHigh,
        onSurfaceVariant = textMuted,
        surfaceTint = base,
        surfaceDim = base,
        surfaceBright = surfaceHigh,
        surfaceContainerLowest = base,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceRaised,
        surfaceContainerHighest = surfaceHigh,
        outline = outline,
        outlineVariant = line,
        scrim = base.copy(alpha = 0.6f),
    )
}

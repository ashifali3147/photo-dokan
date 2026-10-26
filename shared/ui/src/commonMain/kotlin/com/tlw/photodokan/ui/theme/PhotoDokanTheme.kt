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
 * The app's "Calm workspace" theme in dark or light, as [mode] and the device decide. Material
 * components get the same colours and Figtree, so existing screens follow it; new ones also use
 * [StudioTheme] for the roles Material has no name for.
 */
@Composable
fun PhotoDokanTheme(mode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val colors = if (mode.isDark(isSystemInDarkTheme())) StudioPalette.Dark else StudioPalette.Light
    val sans = figtree()
    val display = bricolage()
    val typography = remember(sans, display) { studioTypography(sans, display) }
    val type = remember(display) { studioType(display) }
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
 * Material's roles in Calm workspace colours: primary is the accent, secondary is Ok, and error is
 * Warn, so a problem never looks like the main action. Material marks what is selected (rail item,
 * segment, chip) with the secondary container; selection is the accent's job, so that container is
 * an accent wash.
 */
internal fun StudioColors.toColorScheme(): ColorScheme {
    val scheme = if (isDark) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentWash,
        onPrimaryContainer = accentText,
        inversePrimary = accent,
        secondary = ok,
        onSecondary = onOk,
        secondaryContainer = accentWash,
        onSecondaryContainer = accentText,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = accentWash,
        onTertiaryContainer = accentText,
        error = warn,
        onError = onWarn,
        errorContainer = warnWash,
        onErrorContainer = warn,
        background = base,
        onBackground = text,
        surface = base,
        onSurface = text,
        surfaceVariant = well,
        onSurfaceVariant = textMuted,
        surfaceTint = base,
        surfaceDim = base,
        surfaceBright = surface,
        surfaceContainerLowest = base,
        surfaceContainerLow = rail,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = well,
        outline = outline,
        outlineVariant = line,
        scrim = base.copy(alpha = 0.6f),
    )
}

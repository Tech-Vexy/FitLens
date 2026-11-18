package com.fitlens.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Single source of truth for the FitLens dark "obsidian + lime" design system.
 * All screens reference [FitLensPalette] so theming stays consistent.
 */
object FitLensPalette {
    val Obsidian = Color(0xFF09090B)
    val Card = Color(0xFF121216)
    val Elevated = Color(0xFF1A1A20)
    val ElevatedAlt = Color(0xFF16161C)
    val Border = Color(0xFF27272A)
    val BorderLight = Color(0xFF3F3F46)

    val Lime = Color(0xFFA3E635)
    val LimeSoft = Color(0xFFD9F99D)
    val Emerald = Color(0xFF10B981)
    val EmeraldSoft = Color(0xFF34D399)
    val Sky = Color(0xFF38BDF8)
    val Rose = Color(0xFFF43F5E)
    val Amber = Color(0xFFF59E0B)

    val White = Color(0xFFFAFAFA)
    val Zinc = Color(0xFFA1A1AA)
    val ZincMuted = Color(0xFF71717A)
    val Black = Color(0xFF09090B)

    // Translucent tints used on dark surfaces
    val LimeTint = Lime.copy(alpha = 0.12f)
    val EmeraldTint = Emerald.copy(alpha = 0.12f)
    val SkyTint = Sky.copy(alpha = 0.12f)
    val RoseTint = Rose.copy(alpha = 0.12f)
}

private val FitLensDarkColorScheme = darkColorScheme(
    primary = FitLensPalette.Lime,
    onPrimary = FitLensPalette.Black,
    primaryContainer = FitLensPalette.LimeTint,
    onPrimaryContainer = FitLensPalette.Lime,
    secondary = FitLensPalette.Emerald,
    onSecondary = FitLensPalette.Black,
    secondaryContainer = FitLensPalette.EmeraldTint,
    onSecondaryContainer = FitLensPalette.EmeraldSoft,
    tertiary = FitLensPalette.Sky,
    onTertiary = FitLensPalette.Black,
    background = FitLensPalette.Obsidian,
    onBackground = FitLensPalette.White,
    surface = FitLensPalette.Card,
    onSurface = FitLensPalette.White,
    surfaceVariant = FitLensPalette.Elevated,
    onSurfaceVariant = FitLensPalette.Zinc,
    surfaceContainerLowest = FitLensPalette.Obsidian,
    surfaceContainerLow = FitLensPalette.Card,
    surfaceContainer = FitLensPalette.Elevated,
    surfaceContainerHigh = FitLensPalette.ElevatedAlt,
    surfaceContainerHighest = FitLensPalette.BorderLight,
    outline = FitLensPalette.Border,
    outlineVariant = FitLensPalette.BorderLight,
    error = FitLensPalette.Rose,
    onError = FitLensPalette.White,
)

private val FitLensLightColorScheme = lightColorScheme(
    primary = Color(0xFF4D7C0F),
    onPrimary = Color.White,
    background = Color(0xFFF4F4F5),
    onBackground = Color(0xFF18181B),
    surface = Color.White,
    onSurface = Color(0xFF18181B),
    surfaceVariant = Color(0xFFE4E4E7),
    onSurfaceVariant = Color(0xFF52525B),
    outline = Color(0xFFD4D4D8),
    outlineVariant = Color(0xFFE4E4E7),
    error = Color(0xFFDC2626),
    onError = Color.White,
)

/** Typography tuned for the fitness HUD aesthetic: tight, bold, athletic. */
val FitLensTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Black,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.8.sp,
    ),
)

@Composable
fun FitLensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) FitLensDarkColorScheme else FitLensLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = FitLensTypography,
        content = content,
    )
}

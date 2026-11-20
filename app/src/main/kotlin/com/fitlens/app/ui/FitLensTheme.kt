package com.fitlens.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Cohesive color system for FitLens supporting automatic Light and Dark mode theming.
 */
data class FitLensThemeColors(
    val isDark: Boolean,
    val background: Color,
    val card: Color,
    val elevated: Color,
    val elevatedAlt: Color,
    val border: Color,
    val borderLight: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val lime: Color,
    val limeSoft: Color,
    val emerald: Color,
    val emeraldSoft: Color,
    val sky: Color,
    val rose: Color,
    val amber: Color,
    val limeTint: Color,
    val emeraldTint: Color,
    val skyTint: Color,
    val roseTint: Color,
    val boneColor: Color,
    val floorLineColor: Color,
)

val DarkFitLensColors = FitLensThemeColors(
    isDark = true,
    background = Color(0xFF09090B),
    card = Color(0xFF121216),
    elevated = Color(0xFF1A1A20),
    elevatedAlt = Color(0xFF16161C),
    border = Color(0xFF27272A),
    borderLight = Color(0xFF3F3F46),
    textPrimary = Color(0xFFFAFAFA),
    textSecondary = Color(0xFFA1A1AA),
    textMuted = Color(0xFF71717A),
    lime = Color(0xFFA3E635),
    limeSoft = Color(0xFFD9F99D),
    emerald = Color(0xFF10B981),
    emeraldSoft = Color(0xFF34D399),
    sky = Color(0xFF38BDF8),
    rose = Color(0xFFF43F5E),
    amber = Color(0xFFF59E0B),
    limeTint = Color(0xFFA3E635).copy(alpha = 0.12f),
    emeraldTint = Color(0xFF10B981).copy(alpha = 0.12f),
    skyTint = Color(0xFF38BDF8).copy(alpha = 0.12f),
    roseTint = Color(0xFFF43F5E).copy(alpha = 0.12f),
    boneColor = Color(0xFF71717A),
    floorLineColor = Color.White.copy(alpha = 0.12f),
)

val LightFitLensColors = FitLensThemeColors(
    isDark = false,
    background = Color(0xFFF8FAFC), // Crisp slate-50 canvas
    card = Color(0xFFFFFFFF),       // Pure white card
    elevated = Color(0xFFF1F5F9),   // Slate-100 soft surface
    elevatedAlt = Color(0xFFE2E8F0),// Slate-200 pill/stage surface
    border = Color(0xFFE2E8F0),     // Slate-200 border
    borderLight = Color(0xFFCBD5E1),// Slate-300 border
    textPrimary = Color(0xFF0F172A),// Slate-900 high contrast
    textSecondary = Color(0xFF475569),// Slate-600 readable secondary
    textMuted = Color(0xFF64748B),  // Slate-500
    lime = Color(0xFF4D7C0F),       // Athletic vibrant green (WCAG AAA high contrast on white)
    limeSoft = Color(0xFF65A30D),
    emerald = Color(0xFF059669),    // Emerald-600
    emeraldSoft = Color(0xFF10B981),
    sky = Color(0xFF0284C7),        // Sky-600
    rose = Color(0xFFE11D48),       // Rose-600
    amber = Color(0xFFD97706),      // Amber-600
    limeTint = Color(0xFF84CC16).copy(alpha = 0.18f),
    emeraldTint = Color(0xFF10B981).copy(alpha = 0.18f),
    skyTint = Color(0xFF38BDF8).copy(alpha = 0.18f),
    roseTint = Color(0xFFF43F5E).copy(alpha = 0.18f),
    boneColor = Color(0xFF94A3B8),
    floorLineColor = Color(0xFF0F172A).copy(alpha = 0.12f),
)

val LocalFitLensColors = staticCompositionLocalOf { DarkFitLensColors }

/**
 * Accessor for dynamic colors across all FitLens UI components.
 * Automatically resolves to Light or Dark mode colors.
 */
object FitLensPalette {
    val Obsidian: Color @Composable @ReadOnlyComposable get() = LocalFitLensColors.current.background
    val Card: Color @Composable @ReadOnlyComposable get() = LocalFitLensColors.current.card
    val Elevated: Color @Composable @ReadOnlyComposable get() = LocalFitLensColors.current.elevated
    val ElevatedAlt: Color @Composable @ReadOnlyComposable get() = LocalFitLensColors.current.elevatedAlt
    val Border: Color @Composable @ReadOnlyComposable get() = LocalFitLensColors.current.border
    val BorderLight: Color @Composable @ReadOnlyComposable get() = LocalFitLensColors.current.borderLight

    val Lime: Color @Composable @ReadOnlyComposable get() = LocalFitLensColors.current.lime
    val LimeSoft: Color @Composable @ReadOnlyComposable get() = LocalFitLensColors.current.limeSoft
    val Emerald: Color @Composable @ReadOnlyComposable get() = LocalFitLensColors.current.emerald
    val EmeraldSoft: Color @Composable @ReadOnlyComposable get() = LocalFitLensColors.current.emeraldSoft
    val Sky: Color @Composable @ReadOnlyComposable get() = LocalFitLensColors.current.sky
    val Rose: Color @Composable @ReadOnlyComposable get() = LocalFitLensColors.current.rose
    val Amber: Color @Composable @ReadOnlyComposable get() = LocalFitLensColors.current.amber

    val White: Color @Composable @ReadOnlyComposable get() = LocalFitLensColors.current.textPrimary
    val Zinc: Color @Composable @ReadOnlyComposable get() = LocalFitLensColors.current.textSecondary
    val ZincMuted: Color @Composable @ReadOnlyComposable get() = LocalFitLensColors.current.textMuted
    val Black: Color @Composable @ReadOnlyComposable get() = if (LocalFitLensColors.current.isDark) Color(0xFF09090B) else Color.White

    val LimeTint: Color @Composable @ReadOnlyComposable get() = LocalFitLensColors.current.limeTint
    val EmeraldTint: Color @Composable @ReadOnlyComposable get() = LocalFitLensColors.current.emeraldTint
    val SkyTint: Color @Composable @ReadOnlyComposable get() = LocalFitLensColors.current.skyTint
    val RoseTint: Color @Composable @ReadOnlyComposable get() = LocalFitLensColors.current.roseTint
    val BoneColor: Color @Composable @ReadOnlyComposable get() = LocalFitLensColors.current.boneColor
    val FloorLineColor: Color @Composable @ReadOnlyComposable get() = LocalFitLensColors.current.floorLineColor

    // Static dark constants for non-composable initialization fallback
    val StaticObsidian = Color(0xFF09090B)
    val StaticCard = Color(0xFF121216)
    val StaticElevated = Color(0xFF1A1A20)
    val StaticElevatedAlt = Color(0xFF16161C)
    val StaticBorder = Color(0xFF27272A)
    val StaticBorderLight = Color(0xFF3F3F46)
    val StaticLime = Color(0xFFA3E635)
    val StaticEmerald = Color(0xFF10B981)
    val StaticSky = Color(0xFF38BDF8)
    val StaticRose = Color(0xFFF43F5E)
    val StaticAmber = Color(0xFFF59E0B)
    val StaticWhite = Color(0xFFFAFAFA)
    val StaticZinc = Color(0xFFA1A1AA)
    val StaticZincMuted = Color(0xFF71717A)
}

private val FitLensDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA3E635),
    onPrimary = Color(0xFF09090B),
    primaryContainer = Color(0xFFA3E635).copy(alpha = 0.12f),
    onPrimaryContainer = Color(0xFFA3E635),
    secondary = Color(0xFF10B981),
    onSecondary = Color(0xFF09090B),
    secondaryContainer = Color(0xFF10B981).copy(alpha = 0.12f),
    onSecondaryContainer = Color(0xFF34D399),
    tertiary = Color(0xFF38BDF8),
    onTertiary = Color(0xFF09090B),
    background = Color(0xFF09090B),
    onBackground = Color(0xFFFAFAFA),
    surface = Color(0xFF121216),
    onSurface = Color(0xFFFAFAFA),
    surfaceVariant = Color(0xFF1A1A20),
    onSurfaceVariant = Color(0xFFA1A1AA),
    surfaceContainerLowest = Color(0xFF09090B),
    surfaceContainerLow = Color(0xFF121216),
    surfaceContainer = Color(0xFF1A1A20),
    surfaceContainerHigh = Color(0xFF16161C),
    surfaceContainerHighest = Color(0xFF3F3F46),
    outline = Color(0xFF27272A),
    outlineVariant = Color(0xFF3F3F46),
    error = Color(0xFFF43F5E),
    onError = Color(0xFFFAFAFA),
)

private val FitLensLightColorScheme = lightColorScheme(
    primary = Color(0xFF4D7C0F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF84CC16).copy(alpha = 0.18f),
    onPrimaryContainer = Color(0xFF365314),
    secondary = Color(0xFF059669),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF10B981).copy(alpha = 0.18f),
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    surfaceContainerHighest = Color(0xFFCBD5E1),
    outline = Color(0xFFE2E8F0),
    outlineVariant = Color(0xFFCBD5E1),
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
    val fitLensColors = if (darkTheme) DarkFitLensColors else LightFitLensColors
    val colorScheme = if (darkTheme) FitLensDarkColorScheme else FitLensLightColorScheme

    CompositionLocalProvider(
        LocalFitLensColors provides fitLensColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FitLensTypography,
            content = content,
        )
    }
}

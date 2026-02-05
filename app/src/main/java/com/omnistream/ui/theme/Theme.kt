package com.omnistream.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// --- Color Scheme Definitions ---

enum class AppColorScheme(val label: String, val previewColor: Color) {
    PURPLE("Purple", Purple80),
    OCEAN("Ocean", Color(0xFF42A5F5)),
    EMERALD("Emerald", Color(0xFF66BB6A)),
    SUNSET("Sunset", Color(0xFFFF7043)),
    ROSE("Rose", Color(0xFFEC407A)),
    MIDNIGHT("Midnight", Color(0xFF5C6BC0)),
    CRIMSON("Crimson", Color(0xFFEF5350)),
    GOLD("Gold", Color(0xFFFFCA28)),
    SAIKOU("Saikou", Color(0xFFFF007F)); // Pink accent from Saikou/Dantotsu

    companion object {
        fun fromKey(key: String): AppColorScheme =
            entries.find { it.name.equals(key, ignoreCase = true) } ?: PURPLE
    }
}

enum class DarkModeOption(val label: String) {
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("System");

    companion object {
        fun fromKey(key: String): DarkModeOption =
            entries.find { it.name.equals(key, ignoreCase = true) } ?: DARK
    }
}

// --- Dark schemes per color ---

private fun purpleDark() = darkColorScheme(
    primary = Purple80, onPrimary = Color.White,
    primaryContainer = Purple40, onPrimaryContainer = Color.White,
    secondary = Coral80, onSecondary = Color.White,
    secondaryContainer = Coral40, onSecondaryContainer = Color.White,
    tertiary = Teal80, onTertiary = Color.White,
    tertiaryContainer = Teal40, onTertiaryContainer = Color.White,
    background = SurfaceDark, onBackground = OnSurfaceDark,
    surface = SurfaceDark, onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceContainerHighDark, onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainer = SurfaceContainerDark, surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    outline = Color(0xFF938F99), outlineVariant = Color(0xFF49454F),
    error = Error, onError = Color.White,
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6)
)

private fun oceanDark() = darkColorScheme(
    primary = Color(0xFF42A5F5), onPrimary = Color.White,
    primaryContainer = Color(0xFF1565C0), onPrimaryContainer = Color.White,
    secondary = Color(0xFF26C6DA), onSecondary = Color.White,
    secondaryContainer = Color(0xFF00838F), onSecondaryContainer = Color.White,
    tertiary = Color(0xFF80DEEA), onTertiary = Color.Black,
    background = SurfaceDark, onBackground = OnSurfaceDark,
    surface = SurfaceDark, onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceContainerHighDark, onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainer = SurfaceContainerDark, surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    outline = Color(0xFF938F99), outlineVariant = Color(0xFF49454F),
    error = Error, onError = Color.White,
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6)
)

private fun emeraldDark() = darkColorScheme(
    primary = Color(0xFF66BB6A), onPrimary = Color.White,
    primaryContainer = Color(0xFF2E7D32), onPrimaryContainer = Color.White,
    secondary = Color(0xFFA5D6A7), onSecondary = Color.Black,
    secondaryContainer = Color(0xFF388E3C), onSecondaryContainer = Color.White,
    tertiary = Color(0xFF81C784), onTertiary = Color.Black,
    background = SurfaceDark, onBackground = OnSurfaceDark,
    surface = SurfaceDark, onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceContainerHighDark, onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainer = SurfaceContainerDark, surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    outline = Color(0xFF938F99), outlineVariant = Color(0xFF49454F),
    error = Error, onError = Color.White,
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6)
)

private fun sunsetDark() = darkColorScheme(
    primary = Color(0xFFFF7043), onPrimary = Color.White,
    primaryContainer = Color(0xFFD84315), onPrimaryContainer = Color.White,
    secondary = Color(0xFFFFAB91), onSecondary = Color.Black,
    secondaryContainer = Color(0xFFBF360C), onSecondaryContainer = Color.White,
    tertiary = Color(0xFFFFCC80), onTertiary = Color.Black,
    background = SurfaceDark, onBackground = OnSurfaceDark,
    surface = SurfaceDark, onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceContainerHighDark, onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainer = SurfaceContainerDark, surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    outline = Color(0xFF938F99), outlineVariant = Color(0xFF49454F),
    error = Error, onError = Color.White,
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6)
)

private fun roseDark() = darkColorScheme(
    primary = Color(0xFFEC407A), onPrimary = Color.White,
    primaryContainer = Color(0xFFC2185B), onPrimaryContainer = Color.White,
    secondary = Color(0xFFF48FB1), onSecondary = Color.Black,
    secondaryContainer = Color(0xFFAD1457), onSecondaryContainer = Color.White,
    tertiary = Color(0xFFCE93D8), onTertiary = Color.Black,
    background = SurfaceDark, onBackground = OnSurfaceDark,
    surface = SurfaceDark, onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceContainerHighDark, onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainer = SurfaceContainerDark, surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    outline = Color(0xFF938F99), outlineVariant = Color(0xFF49454F),
    error = Error, onError = Color.White,
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6)
)

private fun midnightDark() = darkColorScheme(
    primary = Color(0xFF5C6BC0), onPrimary = Color.White,
    primaryContainer = Color(0xFF283593), onPrimaryContainer = Color.White,
    secondary = Color(0xFF9FA8DA), onSecondary = Color.Black,
    secondaryContainer = Color(0xFF3949AB), onSecondaryContainer = Color.White,
    tertiary = Color(0xFF7986CB), onTertiary = Color.White,
    background = Color(0xFF0A0A12), onBackground = OnSurfaceDark,
    surface = Color(0xFF0A0A12), onSurface = OnSurfaceDark,
    surfaceVariant = Color(0xFF1E1E2E), onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainer = Color(0xFF151520), surfaceContainerHigh = Color(0xFF1E1E2E),
    surfaceContainerLow = Color(0xFF101018),
    outline = Color(0xFF938F99), outlineVariant = Color(0xFF49454F),
    error = Error, onError = Color.White,
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6)
)

private fun crimsonDark() = darkColorScheme(
    primary = Color(0xFFEF5350), onPrimary = Color.White,
    primaryContainer = Color(0xFFC62828), onPrimaryContainer = Color.White,
    secondary = Color(0xFFEF9A9A), onSecondary = Color.Black,
    secondaryContainer = Color(0xFFB71C1C), onSecondaryContainer = Color.White,
    tertiary = Color(0xFFFFAB91), onTertiary = Color.Black,
    background = SurfaceDark, onBackground = OnSurfaceDark,
    surface = SurfaceDark, onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceContainerHighDark, onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainer = SurfaceContainerDark, surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    outline = Color(0xFF938F99), outlineVariant = Color(0xFF49454F),
    error = Error, onError = Color.White,
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6)
)

private fun goldDark() = darkColorScheme(
    primary = Color(0xFFFFCA28), onPrimary = Color.Black,
    primaryContainer = Color(0xFFF9A825), onPrimaryContainer = Color.Black,
    secondary = Color(0xFFFFE082), onSecondary = Color.Black,
    secondaryContainer = Color(0xFFF57F17), onSecondaryContainer = Color.White,
    tertiary = Color(0xFFFFD54F), onTertiary = Color.Black,
    background = SurfaceDark, onBackground = OnSurfaceDark,
    surface = SurfaceDark, onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceContainerHighDark, onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainer = SurfaceContainerDark, surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    outline = Color(0xFF938F99), outlineVariant = Color(0xFF49454F),
    error = Error, onError = Color.White,
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6)
)

/**
 * Saikou/Dantotsu theme - Pink and Violet accents
 * Based on actual Saikou color palette extracted from themes.xml and colors.xml
 * Primary: #FF007F (vibrant magenta-pink)
 * Secondary: #91A6FF (light periwinkle blue)
 * Background: #212738 (dark blue-gray, not pure black)
 */
private fun saikouDark() = darkColorScheme(
    primary = Color(0xFFFF007F), // pink_500 - vibrant magenta-pink
    onPrimary = Color(0xFFEEEEEE), // bg_white - soft white
    primaryContainer = Color(0xFFC50053), // pink_700 - deeper magenta
    onPrimaryContainer = Color(0xFFEEEEEE),
    secondary = Color(0xFF91A6FF), // violet_400 - light periwinkle
    onSecondary = Color(0xFF212738), // bg_black - dark blue-gray
    secondaryContainer = Color(0xFF3358FF), // violet_700 - rich royal blue
    onSecondaryContainer = Color(0xFFEEEEEE),
    tertiary = Color(0xFFFF5DAE), // pink_200 - lighter pink variant
    onTertiary = Color.White,
    background = Color(0xFF212738), // bg_black - NOT pure black, blue-gray
    onBackground = Color(0xFFEEEEEE), // bg_white - soft white text
    surface = Color(0xFF2A3142), // Slightly lighter than background for elevation
    onSurface = Color(0xFFEEEEEE),
    surfaceVariant = Color(0xFF323B4F), // Even lighter for high elevation
    onSurfaceVariant = Color(0xFFCCCCCC),
    surfaceContainer = Color(0xFF252D3F),
    surfaceContainerHigh = Color(0xFF323B4F),
    surfaceContainerLow = Color(0xFF1C2231),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    error = Color(0xFFE63956), // fav - Saikou's favorite/error red
    onError = Color.White,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

// --- Light schemes per color ---

private fun purpleLight() = lightColorScheme(
    primary = Purple40, onPrimary = Color.White,
    primaryContainer = Purple60, onPrimaryContainer = Color.White,
    secondary = Coral40, onSecondary = Color.White,
    secondaryContainer = Coral60, onSecondaryContainer = Color.White,
    tertiary = Teal40, onTertiary = Color.White,
    tertiaryContainer = Teal60, onTertiaryContainer = Color.White,
    background = SurfaceLight, onBackground = OnSurfaceLight,
    surface = SurfaceLight, onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceContainerLight, onSurfaceVariant = OnSurfaceVariantLight,
    outline = Color(0xFF79747E), outlineVariant = Color(0xFFCAC4D0),
    error = Error, onError = Color.White,
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B)
)

private fun oceanLight() = lightColorScheme(
    primary = Color(0xFF1565C0), onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB), onPrimaryContainer = Color(0xFF0D47A1),
    secondary = Color(0xFF00838F), onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2EBF2), onSecondaryContainer = Color(0xFF006064),
    tertiary = Color(0xFF00ACC1), onTertiary = Color.White,
    background = SurfaceLight, onBackground = OnSurfaceLight,
    surface = SurfaceLight, onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceContainerLight, onSurfaceVariant = OnSurfaceVariantLight,
    outline = Color(0xFF79747E), outlineVariant = Color(0xFFCAC4D0),
    error = Error, onError = Color.White,
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B)
)

private fun emeraldLight() = lightColorScheme(
    primary = Color(0xFF2E7D32), onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9), onPrimaryContainer = Color(0xFF1B5E20),
    secondary = Color(0xFF388E3C), onSecondary = Color.White,
    secondaryContainer = Color(0xFFA5D6A7), onSecondaryContainer = Color(0xFF1B5E20),
    tertiary = Color(0xFF43A047), onTertiary = Color.White,
    background = SurfaceLight, onBackground = OnSurfaceLight,
    surface = SurfaceLight, onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceContainerLight, onSurfaceVariant = OnSurfaceVariantLight,
    outline = Color(0xFF79747E), outlineVariant = Color(0xFFCAC4D0),
    error = Error, onError = Color.White,
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B)
)

private fun sunsetLight() = lightColorScheme(
    primary = Color(0xFFD84315), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFCCBC), onPrimaryContainer = Color(0xFFBF360C),
    secondary = Color(0xFFE64A19), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFAB91), onSecondaryContainer = Color(0xFFBF360C),
    tertiary = Color(0xFFF57C00), onTertiary = Color.White,
    background = SurfaceLight, onBackground = OnSurfaceLight,
    surface = SurfaceLight, onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceContainerLight, onSurfaceVariant = OnSurfaceVariantLight,
    outline = Color(0xFF79747E), outlineVariant = Color(0xFFCAC4D0),
    error = Error, onError = Color.White,
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B)
)

private fun roseLight() = lightColorScheme(
    primary = Color(0xFFC2185B), onPrimary = Color.White,
    primaryContainer = Color(0xFFF8BBD0), onPrimaryContainer = Color(0xFF880E4F),
    secondary = Color(0xFFAD1457), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF48FB1), onSecondaryContainer = Color(0xFF880E4F),
    tertiary = Color(0xFF8E24AA), onTertiary = Color.White,
    background = SurfaceLight, onBackground = OnSurfaceLight,
    surface = SurfaceLight, onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceContainerLight, onSurfaceVariant = OnSurfaceVariantLight,
    outline = Color(0xFF79747E), outlineVariant = Color(0xFFCAC4D0),
    error = Error, onError = Color.White,
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B)
)

private fun midnightLight() = lightColorScheme(
    primary = Color(0xFF283593), onPrimary = Color.White,
    primaryContainer = Color(0xFFC5CAE9), onPrimaryContainer = Color(0xFF1A237E),
    secondary = Color(0xFF3949AB), onSecondary = Color.White,
    secondaryContainer = Color(0xFF9FA8DA), onSecondaryContainer = Color(0xFF1A237E),
    tertiary = Color(0xFF5C6BC0), onTertiary = Color.White,
    background = SurfaceLight, onBackground = OnSurfaceLight,
    surface = SurfaceLight, onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceContainerLight, onSurfaceVariant = OnSurfaceVariantLight,
    outline = Color(0xFF79747E), outlineVariant = Color(0xFFCAC4D0),
    error = Error, onError = Color.White,
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B)
)

private fun crimsonLight() = lightColorScheme(
    primary = Color(0xFFC62828), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFCDD2), onPrimaryContainer = Color(0xFFB71C1C),
    secondary = Color(0xFFD32F2F), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEF9A9A), onSecondaryContainer = Color(0xFFB71C1C),
    tertiary = Color(0xFFE53935), onTertiary = Color.White,
    background = SurfaceLight, onBackground = OnSurfaceLight,
    surface = SurfaceLight, onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceContainerLight, onSurfaceVariant = OnSurfaceVariantLight,
    outline = Color(0xFF79747E), outlineVariant = Color(0xFFCAC4D0),
    error = Error, onError = Color.White,
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B)
)

private fun goldLight() = lightColorScheme(
    primary = Color(0xFFF9A825), onPrimary = Color.Black,
    primaryContainer = Color(0xFFFFF9C4), onPrimaryContainer = Color(0xFFF57F17),
    secondary = Color(0xFFFBC02D), onSecondary = Color.Black,
    secondaryContainer = Color(0xFFFFE082), onSecondaryContainer = Color(0xFFF57F17),
    tertiary = Color(0xFFFFB300), onTertiary = Color.Black,
    background = SurfaceLight, onBackground = OnSurfaceLight,
    surface = SurfaceLight, onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceContainerLight, onSurfaceVariant = OnSurfaceVariantLight,
    outline = Color(0xFF79747E), outlineVariant = Color(0xFFCAC4D0),
    error = Error, onError = Color.White,
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B)
)

/**
 * Saikou/Dantotsu light theme
 * Primary: #FF007F (vibrant magenta-pink)
 * Secondary: #3358FF (rich royal blue - darker variant for light mode)
 * Background: #EEEEEE (soft white, not pure white)
 */
private fun saikouLight() = lightColorScheme(
    primary = Color(0xFFFF007F), // pink_500 - vibrant pink
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFF5DAE), // pink_200 - lighter for container
    onPrimaryContainer = Color(0xFFC50053), // pink_700 - dark text on light container
    secondary = Color(0xFF3358FF), // violet_700 - rich blue (darker for light mode)
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF91A6FF), // violet_400 - lighter for container
    onSecondaryContainer = Color(0xFF3358FF),
    tertiary = Color(0xFFFF5DAE), // pink_200
    onTertiary = Color.White,
    background = Color(0xFFEEEEEE), // bg_white - soft white, NOT pure white
    onBackground = Color(0xFF212738), // bg_black - dark text
    surface = Color.White, // Pure white for cards in light mode
    onSurface = Color(0xFF212738),
    surfaceVariant = Color(0xFFF5F5F5), // Very light gray
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    error = Color(0xFFE63956), // fav - Saikou's favorite/error red
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

// --- Resolvers ---

fun getColorScheme(scheme: AppColorScheme, isDark: Boolean): ColorScheme {
    return if (isDark) {
        when (scheme) {
            AppColorScheme.PURPLE -> purpleDark()
            AppColorScheme.OCEAN -> oceanDark()
            AppColorScheme.EMERALD -> emeraldDark()
            AppColorScheme.SUNSET -> sunsetDark()
            AppColorScheme.ROSE -> roseDark()
            AppColorScheme.MIDNIGHT -> midnightDark()
            AppColorScheme.CRIMSON -> crimsonDark()
            AppColorScheme.GOLD -> goldDark()
            AppColorScheme.SAIKOU -> saikouDark()
        }
    } else {
        when (scheme) {
            AppColorScheme.PURPLE -> purpleLight()
            AppColorScheme.OCEAN -> oceanLight()
            AppColorScheme.EMERALD -> emeraldLight()
            AppColorScheme.SUNSET -> sunsetLight()
            AppColorScheme.ROSE -> roseLight()
            AppColorScheme.MIDNIGHT -> midnightLight()
            AppColorScheme.CRIMSON -> crimsonLight()
            AppColorScheme.GOLD -> goldLight()
            AppColorScheme.SAIKOU -> saikouLight()
        }
    }
}

@Composable
fun OmniStreamTheme(
    darkTheme: Boolean = true,
    appColorScheme: AppColorScheme = AppColorScheme.PURPLE,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> getColorScheme(appColorScheme, darkTheme)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OmniTypography,
        content = content
    )
}

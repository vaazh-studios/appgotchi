package com.vaazhstudios.appgotchi.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Tailwind palette values, so the app matches the HTML prototypes in design/
private object Zinc {
    val c50 = Color(0xFFFAFAFA)
    val c100 = Color(0xFFF4F4F5)
    val c200 = Color(0xFFE4E4E7)
    val c400 = Color(0xFFA1A1AA)
    val c500 = Color(0xFF71717A)
    val c600 = Color(0xFF52525B)
    val c800 = Color(0xFF27272A)
    val c900 = Color(0xFF18181B)
    val c950 = Color(0xFF09090B)
}

private object Lime {
    val c100 = Color(0xFFECFCCB)
    val c200 = Color(0xFFD9F99D)
    val c400 = Color(0xFFA3E635)
    val c500 = Color(0xFF84CC16)
    val c600 = Color(0xFF65A30D)
    val c900 = Color(0xFF365314)
    val c950 = Color(0xFF1A2E05)
}

private object Amber {
    val c50 = Color(0xFFFFFBEB)
    val c200 = Color(0xFFFDE68A)
    val c400 = Color(0xFFFBBF24)
    val c500 = Color(0xFFF59E0B)
    val c900 = Color(0xFF78350F)
    val c950 = Color(0xFF451A03)
}

private object Red {
    val c50 = Color(0xFFFEF2F2)
    val c200 = Color(0xFFFECACA)
    val c400 = Color(0xFFF87171)
    val c600 = Color(0xFFDC2626)
    val c900 = Color(0xFF7F1D1D)
    val c950 = Color(0xFF450A0A)
}

internal val LightColors = lightColorScheme(
    primary = Zinc.c950,
    onPrimary = Color.White,
    primaryContainer = Zinc.c100,
    onPrimaryContainer = Zinc.c950,
    secondary = Zinc.c600,
    onSecondary = Color.White,
    secondaryContainer = Zinc.c100,
    onSecondaryContainer = Zinc.c950,
    tertiary = Lime.c500,
    onTertiary = Zinc.c950,
    tertiaryContainer = Lime.c100,
    onTertiaryContainer = Lime.c900,
    background = Color.White,
    onBackground = Zinc.c950,
    surface = Color.White,
    onSurface = Zinc.c950,
    surfaceVariant = Zinc.c100,
    onSurfaceVariant = Zinc.c500,
    surfaceTint = Color.White,
    surfaceBright = Color.White,
    surfaceDim = Zinc.c200,
    primaryFixed = Zinc.c100,
    primaryFixedDim = Zinc.c200,
    onPrimaryFixed = Zinc.c950,
    onPrimaryFixedVariant = Zinc.c600,
    secondaryFixed = Zinc.c100,
    secondaryFixedDim = Zinc.c200,
    onSecondaryFixed = Zinc.c950,
    onSecondaryFixedVariant = Zinc.c600,
    tertiaryFixed = Lime.c100,
    tertiaryFixedDim = Lime.c200,
    onTertiaryFixed = Lime.c950,
    onTertiaryFixedVariant = Lime.c900,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Zinc.c50,
    surfaceContainer = Zinc.c100,
    surfaceContainerHigh = Zinc.c100,
    surfaceContainerHighest = Zinc.c200,
    outline = Zinc.c950.copy(alpha = 0.15f),
    outlineVariant = Zinc.c950.copy(alpha = 0.10f),
    error = Red.c600,
    onError = Color.White,
    errorContainer = Red.c50,
    onErrorContainer = Red.c900,
    inverseSurface = Zinc.c900,
    inverseOnSurface = Zinc.c50,
    inversePrimary = Lime.c400,
)

internal val DarkColors = darkColorScheme(
    primary = Zinc.c50,
    onPrimary = Zinc.c950,
    primaryContainer = Zinc.c800,
    onPrimaryContainer = Zinc.c50,
    secondary = Zinc.c400,
    onSecondary = Zinc.c950,
    secondaryContainer = Zinc.c800,
    onSecondaryContainer = Zinc.c50,
    tertiary = Lime.c400,
    onTertiary = Zinc.c950,
    tertiaryContainer = Lime.c950,
    onTertiaryContainer = Lime.c200,
    background = Zinc.c950,
    onBackground = Zinc.c50,
    surface = Zinc.c950,
    onSurface = Zinc.c50,
    surfaceVariant = Zinc.c900,
    onSurfaceVariant = Zinc.c400,
    surfaceTint = Zinc.c950,
    surfaceBright = Zinc.c800,
    surfaceDim = Zinc.c950,
    primaryFixed = Zinc.c100,
    primaryFixedDim = Zinc.c200,
    onPrimaryFixed = Zinc.c950,
    onPrimaryFixedVariant = Zinc.c600,
    secondaryFixed = Zinc.c100,
    secondaryFixedDim = Zinc.c200,
    onSecondaryFixed = Zinc.c950,
    onSecondaryFixedVariant = Zinc.c600,
    tertiaryFixed = Lime.c100,
    tertiaryFixedDim = Lime.c200,
    onTertiaryFixed = Lime.c950,
    onTertiaryFixedVariant = Lime.c900,
    surfaceContainerLowest = Zinc.c950,
    surfaceContainerLow = Zinc.c900,
    surfaceContainer = Zinc.c900,
    surfaceContainerHigh = Zinc.c800,
    surfaceContainerHighest = Zinc.c800,
    outline = Zinc.c50.copy(alpha = 0.15f),
    outlineVariant = Zinc.c50.copy(alpha = 0.10f),
    error = Red.c400,
    onError = Zinc.c950,
    errorContainer = Red.c950,
    onErrorContainer = Red.c200,
    inverseSurface = Zinc.c50,
    inverseOnSurface = Zinc.c950,
    inversePrimary = Lime.c500,
)

/** Colours Material 3 has no slot for. */
@Immutable
data class AppgotchiColors(
    val live: Color,
    val focus: Color,
    val pending: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val selectedSegment: Color,
    val cardFill: Color,
)

internal val LightAppgotchiColors = AppgotchiColors(
    live = Lime.c500,
    focus = Lime.c600,
    pending = Amber.c500,
    warningContainer = Amber.c50,
    onWarningContainer = Amber.c900,
    selectedSegment = Color.White,
    cardFill = Color.White,
)

internal val DarkAppgotchiColors = AppgotchiColors(
    live = Lime.c400,
    focus = Lime.c400,
    pending = Amber.c400,
    warningContainer = Amber.c950,
    onWarningContainer = Amber.c200,
    selectedSegment = Zinc.c800,
    cardFill = Zinc.c50.copy(alpha = 0.025f),
)

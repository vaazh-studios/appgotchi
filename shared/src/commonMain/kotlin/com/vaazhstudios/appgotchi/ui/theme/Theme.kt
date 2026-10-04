package com.vaazhstudios.appgotchi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

private val AppgotchiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

private val LocalAppgotchiColors = staticCompositionLocalOf { LightAppgotchiColors }

@Composable
fun AppgotchiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAppgotchiColors provides if (darkTheme) DarkAppgotchiColors else LightAppgotchiColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = appgotchiTypography(),
            shapes = AppgotchiShapes,
            content = content,
        )
    }
}

object AppgotchiTheme {
    val colors: AppgotchiColors
        @Composable @ReadOnlyComposable get() = LocalAppgotchiColors.current
}

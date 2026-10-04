package com.vaazhstudios.appgotchi.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import appgotchi.shared.generated.resources.Res
import appgotchi.shared.generated.resources.inter_medium
import appgotchi.shared.generated.resources.inter_regular
import appgotchi.shared.generated.resources.inter_semibold
import org.jetbrains.compose.resources.Font

@Composable
private fun interFontFamily() = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_medium, FontWeight.Medium),
    Font(Res.font.inter_semibold, FontWeight.SemiBold),
)

@Composable
internal fun appgotchiTypography(): Typography {
    val inter = interFontFamily()
    fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
        fontFamily = inter,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.sp,
    )
    return Typography(
        displaySmall = style(32, 40, FontWeight.SemiBold, -0.5),
        headlineLarge = style(28, 36, FontWeight.SemiBold, -0.4),
        headlineMedium = style(24, 32, FontWeight.SemiBold, -0.3),
        headlineSmall = style(20, 28, FontWeight.SemiBold, -0.2),
        titleLarge = style(18, 26, FontWeight.SemiBold),
        titleMedium = style(16, 24, FontWeight.SemiBold),
        titleSmall = style(14, 20, FontWeight.Medium),
        bodyLarge = style(16, 24, FontWeight.Normal),
        bodyMedium = style(14, 20, FontWeight.Normal),
        bodySmall = style(13, 18, FontWeight.Normal),
        labelLarge = style(14, 20, FontWeight.Medium),
        labelMedium = style(12, 16, FontWeight.Medium),
        labelSmall = style(11, 16, FontWeight.Medium),
    )
}

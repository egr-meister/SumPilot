package com.sumpilot.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object PilotColors {
    val Sky = Color(0xFFE4F0FA)          // pale blue background
    val SkyDeep = Color(0xFFC9E0F2)
    val Cream = Color(0xFFFFF8E8)        // instrument panels
    val CreamEdge = Color(0xFFEADFC4)
    val Navy = Color(0xFF213A5E)         // labels
    val NavySoft = Color(0xFF4A5F7E)
    val Teal = Color(0xFF1C7670)         // active controls (white text ≥ 4.5:1)
    val TealSoft = Color(0xFFCDEBE7)
    val Yellow = Color(0xFFF3C34E)       // warm accents
    val YellowSoft = Color(0xFFFCEBC0)
    val Coral = Color(0xFFB4532A)        // gentle "let's look" accent (not an alarm red)
    val White = Color(0xFFFFFFFF)
}

private val SumPilotColors = lightColorScheme(
    primary = PilotColors.Teal,
    onPrimary = PilotColors.White,
    primaryContainer = PilotColors.TealSoft,
    onPrimaryContainer = PilotColors.Navy,
    secondary = PilotColors.Navy,
    onSecondary = PilotColors.White,
    secondaryContainer = PilotColors.SkyDeep,
    onSecondaryContainer = PilotColors.Navy,
    tertiary = PilotColors.Yellow,
    onTertiary = PilotColors.Navy,
    tertiaryContainer = PilotColors.YellowSoft,
    onTertiaryContainer = PilotColors.Navy,
    background = PilotColors.Sky,
    onBackground = PilotColors.Navy,
    surface = PilotColors.Cream,
    onSurface = PilotColors.Navy,
    surfaceVariant = PilotColors.YellowSoft,
    onSurfaceVariant = PilotColors.NavySoft,
    surfaceContainer = PilotColors.Cream,
    surfaceContainerHigh = PilotColors.Cream,
    surfaceContainerHighest = PilotColors.YellowSoft,
    surfaceContainerLow = PilotColors.Cream,
    outline = PilotColors.NavySoft,
    outlineVariant = PilotColors.CreamEdge,
    error = PilotColors.Coral,
    onError = PilotColors.White,
)

private val base = Typography()

private val SumPilotTypography = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold, color = PilotColors.Navy),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp),
)

/** Large, clear style for arithmetic expressions. Uses sp so it follows system font scaling. */
val ExpressionStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 44.sp,
    lineHeight = 52.sp,
    color = PilotColors.Navy,
)

val SumPilotShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
)

/** Whether decorative animation is reduced (user setting). */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun SumPilotTheme(reducedMotion: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialTheme(
            colorScheme = SumPilotColors,
            typography = SumPilotTypography,
            shapes = SumPilotShapes,
            content = content,
        )
    }
}

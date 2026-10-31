package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Apple Design Tokens
object AppleSpacing {
    val Space4: Dp = 4.dp
    val Space8: Dp = 8.dp
    val Space12: Dp = 12.dp
    val Space16: Dp = 16.dp
    val Space24: Dp = 24.dp
    val Space32: Dp = 32.dp
    val Space48: Dp = 48.dp
    val Space96: Dp = 96.dp

    val ScreenPadding: Dp = 16.dp
    val GroupSpacing: Dp = 24.dp
    val MaxContentWidth: Dp = 680.dp
    val MinTouchTarget: Dp = 48.dp
    val CardCornerRadius: Dp = 18.dp
    val SheetCornerRadius: Dp = 28.dp
    val InputCornerRadius: Dp = 12.dp
    val ChipHeight: Dp = 36.dp
}

data class AppleColors(
    val bgPrimary: Color,
    val bgSecondary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val glassBg: Color,
    val glassBorder: Color,
    val cardBorder: Color,
    val destructive: Color = Color(0xFFFF3B30),
    val success: Color = Color(0xFF34C759),
    val warning: Color = Color(0xFFFF9500),
    val isDark: Boolean
)

val LightAppleColors = AppleColors(
    bgPrimary = Color(0xFFFFFFFF),
    bgSecondary = Color(0xFFF5F5F7),
    textPrimary = Color(0xFF1D1D1F),
    textSecondary = Color(0xFF86868B),
    accent = Color(0xFF0071E3),
    glassBg = Color(0xCCFFFFFF), // 80% opacity
    glassBorder = Color(0x14000000), // 8% black
    cardBorder = Color(0x0D000000), // 5% black
    isDark = false
)

val DarkAppleColors = AppleColors(
    bgPrimary = Color(0xFF000000),
    bgSecondary = Color(0xFF1D1D1F),
    textPrimary = Color(0xFFF5F5F7),
    textSecondary = Color(0xFF86868B),
    accent = Color(0xFF2997FF),
    glassBg = Color(0xB31D1D1F), // 70% opacity
    glassBorder = Color(0x24FFFFFF), // ~14% white
    cardBorder = Color(0x14FFFFFF), // 8% white
    isDark = true
)

val LocalAppleColors = compositionLocalOf { LightAppleColors }

object AppleTheme {
    val colors: AppleColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppleColors.current

    val typography: AppleTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalAppleTypography.current
}

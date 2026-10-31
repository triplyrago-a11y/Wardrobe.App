package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val DarkColorScheme = darkColorScheme(
    primary = DarkAppleColors.accent,
    onPrimary = DarkAppleColors.bgPrimary,
    background = DarkAppleColors.bgPrimary,
    onBackground = DarkAppleColors.textPrimary,
    surface = DarkAppleColors.bgSecondary,
    onSurface = DarkAppleColors.textPrimary,
    surfaceVariant = DarkAppleColors.bgSecondary,
    onSurfaceVariant = DarkAppleColors.textSecondary,
    outline = DarkAppleColors.cardBorder
)

private val LightColorScheme = lightColorScheme(
    primary = LightAppleColors.accent,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    background = LightAppleColors.bgPrimary,
    onBackground = LightAppleColors.textPrimary,
    surface = LightAppleColors.bgSecondary,
    onSurface = LightAppleColors.textPrimary,
    surfaceVariant = LightAppleColors.bgSecondary,
    onSurfaceVariant = LightAppleColors.textSecondary,
    outline = LightAppleColors.cardBorder
)

private val ColorWhite = androidx.compose.ui.graphics.Color.White

@Composable
fun WardrobeAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val appleColors = if (darkTheme) DarkAppleColors else LightAppleColors
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val appleTypography = AppleTypography()

    CompositionLocalProvider(
        LocalAppleColors provides appleColors,
        LocalAppleTypography provides appleTypography
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

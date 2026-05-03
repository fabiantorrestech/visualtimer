package com.fabiantorrestech.visualtimerplus.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = TimerRed,
    onPrimary = WarmWhite,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    error = TimerRed,
    background = WarmWhite,
    surface = WarmSurface,
    surfaceVariant = WarmSurfaceVariant,
    onBackground = WarmText,
    onSurface = WarmText,
    onSurfaceVariant = WarmText,
    outline = WarmOutline,
)

private val DarkColors = darkColorScheme(
    primary = TimerRed,
    onPrimary = OledText,
    primaryContainer = TimerRed,
    onPrimaryContainer = OledText,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = OledText,
    error = TimerRed,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = OledText,
    onSurface = OledText,
    onSurfaceVariant = OledText,
    outline = OledOutline,
)

private val OledColors = darkColorScheme(
    primary = TimerRed,
    onPrimary = OledBlack,
    primaryContainer = TimerRed,
    onPrimaryContainer = OledText,
    secondaryContainer = OledSurfaceVariant,
    onSecondaryContainer = OledText,
    error = TimerRed,
    background = OledBlack,
    surface = OledSurface,
    surfaceVariant = OledSurfaceVariant,
    onBackground = OledText,
    onSurface = OledText,
    onSurfaceVariant = OledText,
    outline = OledOutline,
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(40.dp),
)

@Composable
fun VisualTimerPlusTheme(
    isDark: Boolean,
    oledBlackEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        isDark && oledBlackEnabled -> OledColors
        isDark -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

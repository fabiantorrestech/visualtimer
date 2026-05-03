package io.github.fables.visualtimer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = TimerRed,
    background = WarmWhite,
    surface = WarmSurface,
    onPrimary = OledText,
    onBackground = WarmText,
    onSurface = WarmText,
    surfaceVariant = WarmSurface,
    onSurfaceVariant = WarmText,
)

private val OledColors = darkColorScheme(
    primary = TimerRed,
    onPrimary = OledText,
    background = OledBlack,
    surface = OledSurface,
    onBackground = OledText,
    onSurface = OledText,
    surfaceVariant = OledSurface,
    onSurfaceVariant = OledText,
    outline = Color(0xFF3A3A3A),
)

@Composable
fun VisualTimerTheme(
    isOledMode: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (isOledMode) OledColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

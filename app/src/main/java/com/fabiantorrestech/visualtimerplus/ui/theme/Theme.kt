package com.fabiantorrestech.visualtimerplus.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.io.File

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
    surfaceContainer = OledBlack,
    surfaceContainerHigh = OledSurface,
    surfaceContainerHighest = OledSurfaceVariant,
    surfaceContainerLow = OledBlack,
    surfaceContainerLowest = OledBlack,
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
    customFontPath: String? = null,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        isDark && oledBlackEnabled -> OledColors
        isDark -> DarkColors
        else -> LightColors
    }

    val typography = remember(customFontPath) {
        if (customFontPath != null && File(customFontPath).exists()) {
            try {
                val typeface = android.graphics.Typeface.createFromFile(customFontPath)
                typographyWithFont(FontFamily(typeface))
            } catch (_: Exception) {
                AppTypography
            }
        } else {
            AppTypography
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = AppShapes,
        content = content,
    )
}

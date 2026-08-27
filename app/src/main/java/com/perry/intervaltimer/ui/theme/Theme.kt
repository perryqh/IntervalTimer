package com.perry.intervaltimer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = AccentGreen,
    onPrimary = Color.Black,
    secondary = AccentGreenDark,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = Color(0xFFECEEF0),
    onSurface = Color(0xFFECEEF0)
)

private val LightColors = lightColorScheme(
    primary = AccentGreenDark,
    onPrimary = Color.White,
    secondary = AccentGreen,
    background = Color(0xFFF7F8FA),
    surface = Color.White
)

@Composable
fun IntervalTimerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}

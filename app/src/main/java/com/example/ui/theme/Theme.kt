package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentTeal,
    secondary = AccentTeal,
    tertiary = AccentTeal,
    background = BackgroundDark,
    surface = CardDark,
    error = DeclineRed,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = OnBackgroundDark,
    onSurface = OnSurfaceDark,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

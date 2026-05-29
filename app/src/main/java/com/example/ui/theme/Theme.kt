package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    secondary = EmeraldGreen,
    tertiary = SunsetOrange,
    background = DeepSlate,
    surface = CardBackground,
    onPrimary = SoftWhite,
    onSecondary = SoftWhite,
    onTertiary = SoftWhite,
    onBackground = SoftWhite,
    onSurface = SoftWhite
)

private val LightColorScheme = lightColorScheme(
    primary = AccentBlue,
    secondary = EmeraldGreen,
    tertiary = SunsetOrange,
    background = SoftWhite,
    surface = SoftWhite,
    onPrimary = SoftWhite,
    onSecondary = SoftWhite,
    onTertiary = SoftWhite,
    onBackground = DeepSlate,
    onSurface = DeepSlate
)

@Composable
fun MobileGalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

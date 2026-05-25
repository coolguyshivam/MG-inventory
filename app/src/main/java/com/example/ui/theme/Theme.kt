package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SleekDarkPrimary,
    secondary = SleekDarkSecondary,
    background = SleekDarkBg,
    surface = SleekDarkSurface,
    surfaceVariant = SleekDarkSurfaceVariant,
    onPrimary = Color(0xFF0F172A),
    onSecondary = Color(0xFF0F172A),
    onBackground = SleekDarkOnBg,
    onSurface = SleekDarkOnSurface,
    onSurfaceVariant = SleekDarkOnSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = SleekPrimary,
    secondary = SleekSecondary,
    background = SleekBg,
    surface = SleekSurface,
    surfaceVariant = SleekSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = SleekOnBg,
    onSurface = SleekOnSurface,
    onSurfaceVariant = SleekOnSurfaceVariant
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disable for consistent custom brand colors
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

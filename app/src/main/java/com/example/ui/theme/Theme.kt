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

fun getPrimaryColor(style: String, dark: Boolean): Color {
    return when (style) {
        "Sunset Glow" -> if (dark) Color(0xFFFB7185) else Color(0xFFE11D48)
        "Emerald Mint" -> if (dark) Color(0xFF2DD4BF) else Color(0xFF0D9488)
        "Golden Luxury" -> if (dark) Color(0xFFFBBF24) else Color(0xFFD97706)
        "Vibrant Indigo" -> if (dark) Color(0xFF818CF8) else Color(0xFF4F46E5)
        else -> if (dark) Color(0xFF60A5FA) else Color(0xFF2563EB) // Classic Slate
    }
}

fun getSecondaryColor(style: String, dark: Boolean): Color {
    return when (style) {
        "Sunset Glow" -> if (dark) Color(0xFFFDA4AF) else Color(0xFFFB7185)
        "Emerald Mint" -> if (dark) Color(0xFF34D399) else Color(0xFF10B981)
        "Golden Luxury" -> if (dark) Color(0xFFF59E0B) else Color(0xFFF59E0B)
        "Vibrant Indigo" -> if (dark) Color(0xFF9333EA) else Color(0xFF6366F1)
        else -> if (dark) Color(0xFF93C5FD) else Color(0xFF3B82F6) // Classic Slate
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    appIconStyle: String = "Classic Slate",
    dynamicColor: Boolean = false, // Disable for consistent custom brand colors
    content: @Composable () -> Unit,
) {
    val baseScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val colorScheme = baseScheme.copy(
        primary = getPrimaryColor(appIconStyle, darkTheme),
        secondary = getSecondaryColor(appIconStyle, darkTheme)
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

package com.example.plansync.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Light color scheme wired to PlanSync's coral brand palette.
// Dynamic color is intentionally disabled so the coral accent is consistent
// across all devices regardless of Android version or wallpaper.
private val LightColorScheme = lightColorScheme(
    primary = Coral,          // buttons, filled elements, links
    onPrimary = Color.White,  // text/icons drawn on top of primary
    secondary = CoralLight,   // secondary surfaces (e.g. chips)
    onSecondary = Coral,
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF1C1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F)
)

// Dark scheme kept minimal for now; can be expanded in a later milestone
private val DarkColorScheme = darkColorScheme(
    primary = CoralLight,
    onPrimary = Color(0xFF1C1B1F),
    secondary = Coral,
    onSecondary = Color.White
)

@Composable
fun PlanSyncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
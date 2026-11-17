package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StokVibeLightColorScheme = lightColorScheme(
    primary = StokVibeBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = StokVibeNavy,
    secondary = StokVibeCyan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F7FA),
    onSecondaryContainer = StokVibeNavy,
    tertiary = StokVibeIndigo,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0E7FF),
    onTertiaryContainer = StokVibeNavy,
    background = Color.White,
    onBackground = StokVibeNavy,
    surface = Color.White,
    onSurface = StokVibeNavy,
    surfaceVariant = StokVibeSurfaceVariant,
    onSurfaceVariant = StokVibeSlate,
    outline = Color(0xFF94A3B8),
    error = StokVibeError,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B)
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = StokVibeLightColorScheme,
        typography = Typography,
        content = content
    )
}

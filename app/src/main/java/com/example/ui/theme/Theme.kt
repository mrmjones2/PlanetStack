package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DeepSpaceColorScheme = darkColorScheme(
    primary = NebulaCyan,
    onPrimary = Color(0xFF001B2B),
    primaryContainer = Color(0xFF003755),
    onPrimaryContainer = Color(0xFFBFE9FF),

    secondary = StarGold,
    onSecondary = Color(0xFF261900),
    secondaryContainer = Color(0xFF4A3400),
    onSecondaryContainer = Color(0xFFFFDF9E),

    tertiary = HydrogenAlphaRose,
    onTertiary = Color(0xFF3B001B),
    tertiaryContainer = Color(0xFF5D002D),
    onTertiaryContainer = Color(0xFFFFD9E2),

    background = CosmosDark,
    onBackground = TextPrimary,

    surface = ObservatorySurface,
    onSurface = TextPrimary,

    surfaceVariant = ObservatorySurfaceVariant,
    onSurfaceVariant = TextSecondary,

    outline = ObservatoryCardBorder,
    outlineVariant = Color(0xFF2C3C63)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Astronomy / Planetary imaging apps are best in high-contrast dark room / observatory mode
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DeepSpaceColorScheme,
        typography = Typography,
        content = content
    )
}


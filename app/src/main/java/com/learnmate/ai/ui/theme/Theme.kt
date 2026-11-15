package com.learnmate.ai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = PrimaryIndigo,
    onPrimary = Color.White,
    primaryContainer = PrimaryIndigoContainer,
    onPrimaryContainer = TextSlate900,
    secondary = SecondaryTeal,
    onSecondary = Color.White,
    background = BackgroundSlate,
    onBackground = TextSlate900,
    surface = SurfaceWhite,
    onSurface = TextSlate900,
    outline = BorderSubtle
)

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = MidnightNavy,
    primaryContainer = ElevatedSurfaceNavy,
    onPrimaryContainer = TextLightSlate,
    secondary = AiViolet,
    onSecondary = Color.White,
    background = MidnightNavy,
    onBackground = TextLightSlate,
    surface = ElevatedSurfaceNavy,
    onSurface = TextLightSlate,
    outline = DarkBorder
)

@Composable
fun LearnMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}


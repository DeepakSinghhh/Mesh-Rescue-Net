package com.example.offgridbridge.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BrutalColorScheme = darkColorScheme(
    primary = BrutalYellow,
    onPrimary = BrutalBlack,
    secondary = BrutalRed,
    onSecondary = BrutalWhite,
    tertiary = BrutalGreen,
    onTertiary = BrutalBlack,
    background = BrutalBlack,
    onBackground = BrutalWhite,
    surface = BrutalGray,
    onSurface = BrutalWhite,
    surfaceVariant = BrutalGrayMid,
    onSurfaceVariant = BrutalGrayLight,
    error = BrutalRed,
    onError = BrutalWhite,
)

@Composable
fun OffGridBridgeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BrutalColorScheme,
        typography = Typography,
        content = content
    )
}
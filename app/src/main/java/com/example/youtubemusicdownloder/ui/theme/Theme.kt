package com.example.youtubemusicdownloder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ModernLightColorScheme = lightColorScheme(
    primary = AccentRed,
    onPrimary = PureWhite,
    primaryContainer = AccentRedContainer,
    onPrimaryContainer = OnAccentRedContainer,
    secondary = TextPrimaryDark,
    onSecondary = PureWhite,
    secondaryContainer = SurfaceVariantLight,
    onSecondaryContainer = TextPrimaryDark,
    background = OffWhiteBackground,
    onBackground = TextPrimaryDark,
    surface = SurfaceLight,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderLight
)

@Composable
fun YoutubeMusicDownloderTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ModernLightColorScheme,
        typography = Typography,
        content = content
    )
}

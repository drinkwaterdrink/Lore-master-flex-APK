package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ObsidianColorScheme = darkColorScheme(
    primary = IrisPrimary,
    onPrimary = TextPrimary,
    primaryContainer = IrisGlow,
    onPrimaryContainer = TextPrimary,
    secondary = LuminaCyan,
    onSecondary = ObsidianCanvas,
    secondaryContainer = ObsidianGlass,
    onSecondaryContainer = TextPrimary,
    tertiary = LuminaGold,
    onTertiary = ObsidianCanvas,
    tertiaryContainer = ObsidianElevated,
    onTertiaryContainer = LuminaGold,
    background = ObsidianCanvas,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = TextSecondary,
    outline = ObsidianBorder,
    outlineVariant = ObsidianBorderHighlight,
    error = RoseError,
    onError = TextPrimary
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LoreMasterTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ObsidianColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}

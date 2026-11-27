package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val RoboXRColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ObsidianVoid,
    primaryContainer = ElectricCyanContainer,
    onPrimaryContainer = ElectricCyan,
    secondary = SafetyAmber,
    onSecondary = ObsidianVoid,
    secondaryContainer = SafetyAmberContainer,
    onSecondaryContainer = SafetyAmber,
    tertiary = VerifiedEmerald,
    onTertiary = ObsidianVoid,
    tertiaryContainer = VerifiedEmeraldContainer,
    onTertiaryContainer = VerifiedEmerald,
    error = LockoutCrimson,
    onError = Color.White,
    errorContainer = LockoutCrimsonContainer,
    onErrorContainer = LockoutCrimson,
    background = ObsidianVoid,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianVoid,
    surfaceContainerLow = SlateSurface,
    surfaceContainer = SlateSurfaceElevated,
    surfaceContainerHigh = SlateSurfaceBright,
    outline = HudOutline,
    outlineVariant = HudOutlineSubtle
)

val RoboXRShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp)
)

@Composable
fun RoboXRTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RoboXRColorScheme,
        typography = RoboXRTypography,
        shapes = RoboXRShapes,
        content = content
    )
}

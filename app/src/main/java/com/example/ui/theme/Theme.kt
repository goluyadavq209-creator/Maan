package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val GyanverseDarkColorScheme =
  darkColorScheme(
    primary = ElectricViolet,
    onPrimary = Color.White,
    primaryContainer = CosmicPurpleSurface,
    onPrimaryContainer = SoftLavender,
    secondary = NeonCyan,
    onSecondary = DeepNavyBg,
    secondaryContainer = Color(0xFF0D2E3F),
    onSecondaryContainer = NeonCyan,
    tertiary = RoseMagenta,
    onTertiary = Color.White,
    background = DeepNavyBg,
    onBackground = TextPrimary,
    surface = MidnightNavySurface,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF161E3D),
    onSurfaceVariant = TextSecondary,
    outline = GlassBorderSubtle,
    error = StatusError,
    onError = Color.White,
  )

private val StudioShapes =
  Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
  )

@Composable
fun GyanverseStudioTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = GyanverseDarkColorScheme,
    typography = StudioTypography,
    shapes = StudioShapes,
    content = content,
  )
}

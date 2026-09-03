package com.example.upaos.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = UpaoBlueLight,
    onPrimary = Color(0xFF071A45),
    primaryContainer = Color(0xFF163478),
    onPrimaryContainer = Color(0xFFD6E2FF),
    secondary = Color(0xFFEAB308),
    onSecondary = Color(0xFF382A00),
    secondaryContainer = Color(0xFF4D3B00),
    onSecondaryContainer = Color(0xFFFFE599),
    tertiary = Color(0xFF5EEAD4),
    onTertiary = Color(0xFF003730),
    tertiaryContainer = Color(0xFF004D44),
    onTertiaryContainer = Color(0xFF99F6E4),
    background = UpaoBackgroundDark,
    onBackground = Color(0xFFE2E6EE),
    surface = UpaoSurfaceDark,
    onSurface = Color(0xFFE2E6EE),
    surfaceVariant = Color(0xFF232936),
    onSurfaceVariant = Color(0xFFC0C5D2),
    surfaceContainerLowest = Color(0xFF080B0F),
    surfaceContainerLow = UpaoSurfaceLowDark,
    surfaceContainer = UpaoSurfaceDark,
    surfaceContainerHigh = UpaoSurfaceHighDark,
    surfaceContainerHighest = Color(0xFF262D3D),
    outline = Color(0xFF868C9A),
    outlineVariant = Color(0xFF232936),
    error = Color(0xFFF87171),
    onError = Color(0xFF600004),
    errorContainer = Color(0xFF8C0009),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = UpaoBlue,
    onPrimary = Color.White,
    primaryContainer = UpaoBlueContainer,
    onPrimaryContainer = UpaoBlueDark,
    secondary = Color(0xFFCA8A04),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF08A),
    onSecondaryContainer = Color(0xFF422006),
    tertiary = Color(0xFF0D9488),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCCFBF1),
    onTertiaryContainer = Color(0xFF115E59),
    background = BackgroundLight,
    onBackground = Color(0xFF1A1C20),
    surface = SurfaceLight,
    onSurface = Color(0xFF1A1C20),
    surfaceVariant = Color(0xFFE2E6F0),
    onSurfaceVariant = Color(0xFF434651),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F3F9),
    surfaceContainer = Color(0xFFE7EAED),
    surfaceContainerHigh = SurfaceLightHigh,
    surfaceContainerHighest = Color(0xFFDCE0E9),
    outline = Color(0xFF717580),
    outlineVariant = Color(0xFFC2C5D0),
    error = UpaoRedDeep,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val UpaoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun UPAOSTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = UpaoShapes,
        content = content
    )
}

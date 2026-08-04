package com.example.upaos.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = UpaoBlueLight,
    onPrimary = UpaoBlueDark,
    primaryContainer = UpaoBlueDark,
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = UpaoOrangeBright,
    onSecondary = Color(0xFF3E2A00),
    secondaryContainer = Color(0xFF5C3A00),
    onSecondaryContainer = Color(0xFFFFDCAB),
    tertiary = UpaoOrange,
    background = UpaoBackgroundDark,
    onBackground = Color(0xFFE6EAF3),
    surface = UpaoSurfaceDark,
    onSurface = Color(0xFFE6EAF3),
    surfaceVariant = Color(0xFF2A2E37),
    onSurfaceVariant = Color(0xFFC3C9D6),
    surfaceContainerLowest = Color(0xFF0D0D11),
    surfaceContainerLow = UpaoSurfaceLowDark,
    surfaceContainer = UpaoSurfaceDark,
    surfaceContainerHigh = UpaoSurfaceHighDark,
    surfaceContainerHighest = Color(0xFF2A2E37),
    outline = Color(0xFF8A93A5),
    outlineVariant = Color(0xFF2A2E37),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = UpaoBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E4FF),
    onPrimaryContainer = Color(0xFF001B46),
    secondary = Color(0xFF8A5300),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCAB),
    onSecondaryContainer = Color(0xFF2B1600),
    tertiary = UpaoOrange,
    background = Color(0xFFF4F7FB),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFFBFCFE),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE1E3EC),
    onSurfaceVariant = Color(0xFF44474E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F7FB),
    surfaceContainer = Color(0xFFF0F2F8),
    surfaceContainerHigh = Color(0xFFEBEDF3),
    surfaceContainerHighest = Color(0xFFE1E4EB),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC2C7D0),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val UpaoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
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

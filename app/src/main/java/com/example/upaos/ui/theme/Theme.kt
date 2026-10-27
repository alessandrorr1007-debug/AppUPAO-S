package com.example.upaos.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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
    surfaceVariant = Color(0xFF24334D),
    onSurfaceVariant = Color(0xFFC0C5D2),
    surfaceContainerLowest = Color(0xFF070C17),
    surfaceContainerLow = UpaoSurfaceLowDark,
    surfaceContainer = UpaoSurfaceDark,
    surfaceContainerHigh = UpaoSurfaceHighDark,
    surfaceContainerHighest = Color(0xFF22334E),
    outline = Color(0xFF868C9A),
    outlineVariant = Color(0xFF2C3E5A),
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
    secondary = Color(0xFF925B00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF08A),
    onSecondaryContainer = Color(0xFF422006),
    tertiary = Color(0xFF08766D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCCFBF1),
    onTertiaryContainer = Color(0xFF115E59),
    background = BackgroundLight,
    onBackground = Color(0xFF18243B),
    surface = SurfaceLight,
    onSurface = Color(0xFF18243B),
    surfaceVariant = Color(0xFFE2E6F0),
    onSurfaceVariant = Color(0xFF526079),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F3F9),
    surfaceContainer = Color(0xFFEDF2FA),
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
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun UPAOSTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? android.app.Activity)?.window?.let { window ->
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = UpaoShapes,
        content = content
    )
}

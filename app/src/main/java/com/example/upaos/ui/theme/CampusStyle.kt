package com.example.upaos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Match the app's selected theme, independently of the device's appearance. */
val campusIsDark: Boolean
    @Composable get() = MaterialTheme.colorScheme.background.luminance() < .5f

/** Retain category hues while making small text readable against the app surface. */
@Composable
fun campusAccent(color: Color): Color = readableAccent(color, MaterialTheme.colorScheme.surface)

internal fun readableAccent(color: Color, background: Color): Color {
    fun contrast(foreground: Color): Float {
        val a = foreground.luminance()
        val b = background.luminance()
        return (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
    }
    if (contrast(color) >= 4.5f) return color
    val target = if (background.luminance() < .5f) Color.White else Color.Black
    for (step in 1..20) {
        val candidate = lerp(color, target, step / 20f)
        if (contrast(candidate) >= 4.5f) return candidate
    }
    return target
}

val campusSuccess: Color
    @Composable get() = if (campusIsDark) Color(0xFF77DEB0) else Color(0xFF14734D)
val campusWarning: Color
    @Composable get() = if (campusIsDark) Color(0xFFFFCC80) else Color(0xFF945500)
val campusPurple: Color
    @Composable get() = if (campusIsDark) Color(0xFFC9B6FF) else Color(0xFF6840B1)
val campusInfo: Color
    @Composable get() = MaterialTheme.colorScheme.primary
val campusError: Color
    @Composable get() = MaterialTheme.colorScheme.error

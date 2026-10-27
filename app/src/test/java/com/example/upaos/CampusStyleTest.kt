package com.example.upaos

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.upaos.ui.theme.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusStyleTest {
    private fun contrast(a: Color, b: Color): Float =
        (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)

    @Test fun categoryTextIsReadableInBothThemes() {
        val accents = listOf(UpaoBlue, UpaoGreen, UpaoRed, UpaoAmber, UpaoOrange, UpaoGray,
            Color(0xFF8B5CF6), Color(0xFF14B8A6), Color(0xFFEC4899))
        for (surface in listOf(SurfaceLight, UpaoSurfaceDark)) {
            for (accent in accents) {
                assertTrue("Accent $accent on $surface", contrast(readableAccent(accent, surface), surface) >= 4.5f)
            }
        }
    }

    @Test fun readableColorsKeepTheirOriginalHue() {
        assertEquals(Color.Black, readableAccent(Color.Black, Color.White))
        assertEquals(Color.White, readableAccent(Color.White, UpaoSurfaceDark))
    }
}

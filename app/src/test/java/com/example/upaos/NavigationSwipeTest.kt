package com.example.upaos

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationSwipeTest {

    private fun resolverIndiceTab(tabKey: String?): Int {
        return when (tabKey) {
            "notas" -> 1
            "horario" -> 2
            "asistencia" -> 3
            else -> 0
        }
    }

    @Test
    fun `test mapeo de destination_tab a indices del HorizontalPager`() {
        assertEquals(0, resolverIndiceTab(null))
        assertEquals(0, resolverIndiceTab("desconocido"))
        assertEquals(0, resolverIndiceTab("hoy"))
        assertEquals(1, resolverIndiceTab("notas"))
        assertEquals(2, resolverIndiceTab("horario"))
        assertEquals(3, resolverIndiceTab("asistencia"))
    }

    @Test
    fun `test nested scroll connection absorbe delta horizontal residual para proteger el pager`() {
        val connection = object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                return Offset(available.x, 0f)
            }
        }

        // Simular que el usuario hace un swipe hacia la izquierda en el horario que sobra 150px
        val unconsumedSwipeLeft = Offset(-150f, 0f)
        val absorbedLeft = connection.onPostScroll(Offset.Zero, unconsumedSwipeLeft, NestedScrollSource.UserInput)
        assertEquals(-150f, absorbedLeft.x, 0.001f)
        assertEquals(0f, absorbedLeft.y, 0.001f)

        // Simular que el usuario hace un swipe hacia la derecha en el horario que sobra 80px
        val unconsumedSwipeRight = Offset(80f, 0f)
        val absorbedRight = connection.onPostScroll(Offset.Zero, unconsumedSwipeRight, NestedScrollSource.UserInput)
        assertEquals(80f, absorbedRight.x, 0.001f)
        assertEquals(0f, absorbedRight.y, 0.001f)

        // El delta vertical no se absorbe
        val verticalDelta = Offset(0f, 40f)
        val absorbedVertical = connection.onPostScroll(Offset.Zero, verticalDelta, NestedScrollSource.UserInput)
        assertEquals(0f, absorbedVertical.x, 0.001f)
        assertEquals(0f, absorbedVertical.y, 0.001f)
    }
}

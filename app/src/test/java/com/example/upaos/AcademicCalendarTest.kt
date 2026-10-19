package com.example.upaos

import com.example.upaos.data.upao.AcademicCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class AcademicCalendarTest {

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    @Test
    fun `test antes de que inicie el ciclo`() {
        val fecha = sdf.parse("2026-03-10")!!
        val info = AcademicCalendar.calcularSemana("202610", fecha)

        assertTrue(info.configurada)
        assertTrue(info.fueraDeCiclo == true)
        assertNull(info.semana)
        assertEquals("El ciclo aún no inicia", info.etiqueta)
    }

    @Test
    fun `test primera semana de clases`() {
        val inicio = sdf.parse("2026-03-23")!!
        val infoInicio = AcademicCalendar.calcularSemana("202610", inicio)

        assertTrue(infoInicio.configurada)
        assertFalse(infoInicio.fueraDeCiclo == true)
        assertEquals(1, infoInicio.semana)
        assertEquals("Semana 1 de 17", infoInicio.etiqueta)

        // 4 días después sigue en semana 1
        val dia4 = sdf.parse("2026-03-27")!!
        val infoDia4 = AcademicCalendar.calcularSemana("202610", dia4)
        assertEquals(1, infoDia4.semana)
    }

    @Test
    fun `test semana 8 examenes parciales`() {
        // 7 semanas * 7 días = 49 días después de 2026-03-23 -> 2026-05-11
        val fecha = sdf.parse("2026-05-11")!!
        val info = AcademicCalendar.calcularSemana("202610", fecha)

        assertEquals(8, info.semana)
        assertEquals("Semana 8 de 17", info.etiqueta)
        assertFalse(info.fueraDeCiclo == true)
    }

    @Test
    fun `test semana 16 fin de ciclo regular`() {
        // 15 semanas * 7 días = 105 días después de 2026-03-23 -> 2026-07-06
        val fecha = sdf.parse("2026-07-06")!!
        val info = AcademicCalendar.calcularSemana("202610", fecha)

        assertEquals(16, info.semana)
        assertEquals("Semana 16 de 17", info.etiqueta)
        assertFalse(info.fueraDeCiclo == true)
    }

    @Test
    fun `test semana 17 sustitutorios`() {
        // 16 semanas * 7 días = 112 días después de 2026-03-23 -> 2026-07-13
        val fecha = sdf.parse("2026-07-13")!!
        val info = AcademicCalendar.calcularSemana("202610", fecha)

        assertEquals(17, info.semana)
        assertEquals("Semana 17 de 17", info.etiqueta)
        assertFalse(info.fueraDeCiclo == true)
    }

    @Test
    fun `test semana 18 fuera de ciclo`() {
        // 17 semanas * 7 días = 119 días después de 2026-03-23 -> 2026-07-20
        val fecha = sdf.parse("2026-07-20")!!
        val info = AcademicCalendar.calcularSemana("202610", fecha)

        assertNull(info.semana)
        assertEquals("Ciclo concluido", info.etiqueta)
        assertTrue(info.fueraDeCiclo == true)
    }

    @Test
    fun `test ciclo concluido`() {
        val fecha = sdf.parse("2026-08-01")!!
        val info = AcademicCalendar.calcularSemana("202610", fecha)

        assertNull(info.semana)
        assertEquals("Ciclo concluido", info.etiqueta)
        assertTrue(info.fueraDeCiclo == true)
    }

    @Test
    fun `test ciclo 202620 semana 7 domingo y semana 8 lunes`() {
        // Ciclo 202620 inició el 2026-08-31
        val domingoSemana7 = sdf.parse("2026-10-18")!!
        val infoDomingo = AcademicCalendar.calcularSemana("202620", domingoSemana7)
        assertEquals(7, infoDomingo.semana)
        assertEquals("Semana 7 de 17", infoDomingo.etiqueta)

        val lunesSemana8 = sdf.parse("2026-10-19")!!
        val infoLunes = AcademicCalendar.calcularSemana("202620", lunesSemana8)
        assertEquals(8, infoLunes.semana)
        assertEquals("Semana 8 de 17", infoLunes.etiqueta)
    }

    @Test
    fun `test estimarFechaInicio dinamico`() {
        assertEquals("2027-03-23", AcademicCalendar.estimarFechaInicio("202710"))
        assertEquals("2027-08-31", AcademicCalendar.estimarFechaInicio("202720"))
        assertEquals("2027-01-05", AcademicCalendar.estimarFechaInicio("202700"))
        // Si está en el mapa, devuelve el valor exacto
        assertEquals("2026-03-23", AcademicCalendar.estimarFechaInicio("202610"))
        assertEquals("2026-08-31", AcademicCalendar.estimarFechaInicio("202620"))
    }
}

package com.example.upaos

import com.example.upaos.data.model.HorarioBloque
import com.example.upaos.data.upao.ScheduleCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleCalculatorTest {

    @Test
    fun `test convertirHora 24h a 12h`() {
        val (h24_1, h12_1) = ScheduleCalculator.convertirHora("0700")
        assertEquals("07:00", h24_1)
        assertEquals("7:00 AM", h12_1)

        val (h24_2, h12_2) = ScheduleCalculator.convertirHora("1430")
        assertEquals("14:30", h24_2)
        assertEquals("2:30 PM", h12_2)

        val (h24_3, h12_3) = ScheduleCalculator.convertirHora("0000")
        assertEquals("00:00", h24_3)
        assertEquals("12:00 AM", h12_3)

        val (h24_4, h12_4) = ScheduleCalculator.convertirHora("1200")
        assertEquals("12:00", h24_4)
        assertEquals("12:00 PM", h12_4)

        val (h24_5, h12_5) = ScheduleCalculator.convertirHora("1250")
        assertEquals("12:50", h24_5)
        assertEquals("12:50 PM", h12_5)

        // Entrada inválida devuelve original
        val (inv1, inv2) = ScheduleCalculator.convertirHora("abc")
        assertEquals("abc", inv1)
        assertEquals("abc", inv2)
    }

    @Test
    fun `test normalizarEdificio`() {
        assertEquals("PABELLON G", ScheduleCalculator.normalizarEdificio("PAB-G"))
        assertEquals("PABELLON G", ScheduleCalculator.normalizarEdificio("PAB G"))
        assertEquals("PABELLON H", ScheduleCalculator.normalizarEdificio("PAB-H"))
        assertEquals("EDIFICIO CENTRAL", ScheduleCalculator.normalizarEdificio("EDIFICIO CENTRAL"))
        assertNull(ScheduleCalculator.normalizarEdificio(null))
        assertNull(ScheduleCalculator.normalizarEdificio("   "))
    }

    @Test
    fun `test formatoAula`() {
        assertEquals("PABELLON G G-402", ScheduleCalculator.formatoAula("PAB-G", "G-402"))
        assertEquals("G-402", ScheduleCalculator.formatoAula(null, "G-402"))
        assertEquals("PABELLON G", ScheduleCalculator.formatoAula("PAB-G", null))
        assertNull(ScheduleCalculator.formatoAula(null, null))
        assertNull(ScheduleCalculator.formatoAula("", "  "))
    }

    @Test
    fun `test fusionarBloques consecutivos del mismo dia`() {
        val b1 = HorarioBloque(
            dia = 0,
            diaNombre = "LUN",
            horaInicio = "07:00",
            horaFin = "08:40",
            horaInicio12h = "7:00 AM",
            horaFin12h = "8:40 AM",
            aula = "PABELLON G G-402",
            docente = "PEREZ JUAN",
            tipo = "TEORÍA"
        )
        val b2 = HorarioBloque(
            dia = 0,
            diaNombre = "LUN",
            horaInicio = "08:40",
            horaFin = "10:20",
            horaInicio12h = "8:40 AM",
            horaFin12h = "10:20 AM",
            aula = "PABELLON G G-402",
            docente = "PEREZ JUAN",
            tipo = "TEORÍA"
        )

        val merged = ScheduleCalculator.fusionarBloques(listOf(b1, b2))
        assertEquals(1, merged.size)
        assertEquals("07:00", merged[0].horaInicio)
        assertEquals("10:20", merged[0].horaFin)
        assertEquals("7:00 AM", merged[0].horaInicio12h)
        assertEquals("10:20 AM", merged[0].horaFin12h)
        assertEquals("PABELLON G G-402", merged[0].aula)
        assertEquals("PEREZ JUAN", merged[0].displayDocente)
    }

    @Test
    fun `test fusionarBloques no consecutivos permanecen separados`() {
        val b1 = HorarioBloque(
            dia = 0,
            horaInicio = "07:00",
            horaFin = "08:40",
            horaInicio12h = "7:00 AM",
            horaFin12h = "8:40 AM"
        )
        val b2 = HorarioBloque(
            dia = 0,
            horaInicio = "09:00",
            horaFin = "10:40",
            horaInicio12h = "9:00 AM",
            horaFin12h = "10:40 AM"
        )

        val merged = ScheduleCalculator.fusionarBloques(listOf(b1, b2))
        assertEquals(2, merged.size)
        assertEquals("07:00", merged[0].horaInicio)
        assertEquals("09:00", merged[1].horaInicio)
    }

    @Test
    fun `test fusionarBloques de dias diferentes permanecen en sus dias`() {
        val b1 = HorarioBloque(
            dia = 0,
            horaInicio = "07:00",
            horaFin = "08:40"
        )
        val b2 = HorarioBloque(
            dia = 1,
            horaInicio = "07:00",
            horaFin = "08:40"
        )

        val merged = ScheduleCalculator.fusionarBloques(listOf(b1, b2))
        assertEquals(2, merged.size)
        assertEquals(0, merged[0].dia)
        assertEquals(1, merged[1].dia)
    }
}

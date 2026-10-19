package com.example.upaos

import com.example.upaos.data.model.HorarioBloque
import com.example.upaos.data.upao.ScheduleCalculator
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `test esHoy para lunes, sabado y domingo`() {
        // Lunes: 19 de octubre de 2026
        val lunes = LocalDate.of(2026, 10, 19)
        assertEquals(0, ScheduleCalculator.diaSemanaLima(lunes))
        assertTrue(ScheduleCalculator.esHoy(0, lunes))
        assertFalse(ScheduleCalculator.esHoy(1, lunes))
        assertFalse(ScheduleCalculator.esHoy(5, lunes))

        // Sábado: 24 de octubre de 2026
        val sabado = LocalDate.of(2026, 10, 24)
        assertEquals(5, ScheduleCalculator.diaSemanaLima(sabado))
        assertTrue(ScheduleCalculator.esHoy(5, sabado))
        assertFalse(ScheduleCalculator.esHoy(0, sabado))
        assertFalse(ScheduleCalculator.esHoy(4, sabado))

        // Domingo: 25 de octubre de 2026 (los chips visibles son 0..5, ninguno debe tener esHoy = true)
        val domingo = LocalDate.of(2026, 10, 25)
        assertEquals(6, ScheduleCalculator.diaSemanaLima(domingo))
        for (dia in 0..5) {
            assertFalse("En domingo, el chip de dia $dia no debe tener esHoy = true", ScheduleCalculator.esHoy(dia, domingo))
        }
    }

    @Test
    fun `test diaParaAutoScroll selecciona dia correcto y domingo va a lunes`() {
        val lunes = LocalDate.of(2026, 10, 19)
        val miercoles = LocalDate.of(2026, 10, 21)
        val sabado = LocalDate.of(2026, 10, 24)
        val domingo = LocalDate.of(2026, 10, 25)

        assertEquals(0, ScheduleCalculator.diaParaAutoScroll(lunes))
        assertEquals(2, ScheduleCalculator.diaParaAutoScroll(miercoles))
        assertEquals(5, ScheduleCalculator.diaParaAutoScroll(sabado))
        assertEquals(0, ScheduleCalculator.diaParaAutoScroll(domingo)) // Domingo -> Lunes sin crashear
    }

    @Test
    fun `test calcularScrollOffsetDp centra el chip del dia`() {
        val columnaWidthDp = 140f
        val screenWidthDp = 360f

        // Lunes (0): centro en 70dp, menor a la mitad de pantalla (180dp), se acota en 0dp
        val offsetLunes = ScheduleCalculator.calcularScrollOffsetDp(0, columnaWidthDp, screenWidthDp)
        assertEquals(0f, offsetLunes, 0.001f)

        // Miércoles (2): centro en 2*140 + 70 = 350dp. Offset deseado: 350 - 180 = 170dp
        val offsetMiercoles = ScheduleCalculator.calcularScrollOffsetDp(2, columnaWidthDp, screenWidthDp)
        assertEquals(170f, offsetMiercoles, 0.001f)

        // Domingo (6): fuera de rango de 0..5 se acota a Lunes (0) -> 0dp
        val offsetDomingo = ScheduleCalculator.calcularScrollOffsetDp(6, columnaWidthDp, screenWidthDp)
        assertEquals(0f, offsetDomingo, 0.001f)
    }
}


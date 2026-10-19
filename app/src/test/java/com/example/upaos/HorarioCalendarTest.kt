package com.example.upaos

import com.example.upaos.data.model.HorarioBloque
import com.example.upaos.data.model.HorarioCurso
import com.example.upaos.ui.horario.*
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

class HorarioCalendarTest {
    private fun date(value: String): Long = SimpleDateFormat("yyyy-MM-dd").apply {
        timeZone = TimeZone.getTimeZone("America/Lima")
    }.parse(value)!!.time

    @Test fun sundayShowsOnlySundayClassesInChronologicalOrder() {
        val course = HorarioCurso(nombre = "Curso", bloques = listOf(
            HorarioBloque(dia = 6, horaInicio = "14:00", horaFin = "16:00"),
            HorarioBloque(dia = 0, horaInicio = "08:00", horaFin = "10:00"),
            HorarioBloque(dia = 6, horaInicio = "9:00", horaFin = "11:00"),
            HorarioBloque(dia = null, horaInicio = "07:00")
        ))
        assertEquals(listOf("9:00", "14:00"),
            classesForScheduleDate(listOf(course), date("2026-10-18")).map { it.bloque.horaInicio })
        assertEquals(listOf("08:00"),
            classesForScheduleDate(listOf(course), date("2026-10-19")).map { it.bloque.horaInicio })
        assertTrue(classesForScheduleDate(listOf(course), date("2026-10-20")).isEmpty())

        // En semana 18 (2026-12-28) ya está fuera del ciclo de 17 semanas y no debe retornar clases
        assertTrue(classesForScheduleDate(listOf(course), date("2026-12-28"), "202620").isEmpty())
    }

    @Test fun monthNavigationClampsDayAndCrossesYearBoundaries() {
        assertEquals(date("2026-02-28"), moveScheduleMonth(date("2026-01-31"), 1))
        assertEquals(date("2024-02-29"), moveScheduleMonth(date("2024-01-31"), 1))
        assertEquals(date("2027-01-18"), moveScheduleMonth(date("2026-12-18"), 1))
        assertEquals(date("2026-12-18"), moveScheduleMonth(date("2027-01-18"), -1))
    }

    @Test fun stripIncludesEveryDayOfSelectedMonth() {
        val days = scheduleMonthDays(date("2024-02-18"))
        assertEquals(29, days.size)
        assertEquals(date("2024-02-01"), days.first())
        assertEquals(date("2024-02-29"), days.last())
        assertEquals(31, scheduleMonthDays(date("2026-10-18")).size)
    }

    @Test fun phoneTimezoneCannotShiftSelectedCampusDate() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))
            val sunday = date("2026-10-18")
            assertEquals(sunday, scheduleDateStart(sunday + 23 * 60 * 60 * 1000))
            assertEquals(Calendar.SUNDAY, scheduleCalendar(sunday).get(Calendar.DAY_OF_WEEK))
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test fun resolverTipoClaseDistinguishesTheoryAndLabOnSameDay() {
        val bloqueTeoria = HorarioBloque(dia = 1, horaInicio = "07:15", horaFin = "08:50", aula = "G-301", tipo = "Clase")
        val bloqueLab = HorarioBloque(dia = 1, horaInicio = "08:50", horaFin = "10:30", aula = "Pab. H - LAB 202", tipo = "Clase")
        val curso = HorarioCurso(
            nombre = "MACHINE LEARNING",
            crn = "5045",
            bloques = listOf(bloqueTeoria, bloqueLab)
        )

        val infoTeoria = deducirInfoComponente(curso, bloqueTeoria, emptyList())
        val resTeoria = resolverTipoClase(curso, bloqueTeoria, infoTeoria)
        assertEquals("T", resTeoria.letra)
        assertEquals("Teoría", resTeoria.nombreCompleto)
        assertFalse(resTeoria.esLab)

        val infoLab = deducirInfoComponente(curso, bloqueLab, emptyList())
        val resLab = resolverTipoClase(curso, bloqueLab, infoLab)
        assertEquals("L", resLab.letra)
        assertEquals("Laboratorio", resLab.nombreCompleto)
        assertTrue(resLab.esLab)
    }

    @Test fun resolverTipoClaseDetectsDirectLetterTypes() {
        val curso = HorarioCurso(nombre = "CURSO EJEMPLO")
        val bloqueT = HorarioBloque(dia = 0, tipo = "T")
        val bloqueL = HorarioBloque(dia = 0, tipo = "L")
        val bloqueP = HorarioBloque(dia = 0, tipo = "P")

        val resT = resolverTipoClase(curso, bloqueT, InfoBloqueComponente("T", null, null, null))
        assertEquals("T", resT.letra)
        assertEquals("Teoría", resT.nombreCompleto)

        val resL = resolverTipoClase(curso, bloqueL, InfoBloqueComponente("L", null, null, null))
        assertEquals("L", resL.letra)
        assertEquals("Laboratorio", resL.nombreCompleto)
        assertTrue(resL.esLab)

        val resP = resolverTipoClase(curso, bloqueP, InfoBloqueComponente("P", null, null, null))
        assertEquals("P", resP.letra)
        assertEquals("Práctica", resP.nombreCompleto)
        assertTrue(resP.esPrac)
    }
}


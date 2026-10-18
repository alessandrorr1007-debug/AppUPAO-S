package com.example.upaos

import com.example.upaos.data.model.HorarioBloque
import com.example.upaos.data.model.HorarioCurso
import com.example.upaos.service.ClassReminderCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ClassReminderCalculatorTest {

    @Test
    fun clase0715_programaNotificacionA0700() {
        // Lunes 2026-04-13 (día index 0)
        val fechaLunes = LocalDate.of(2026, 4, 13)
        val curso = HorarioCurso(
            crn = "101",
            nombre = "Ingeniería de Software",
            bloques = listOf(
                HorarioBloque(
                    dia = 0,
                    horaInicio = "07:15",
                    horaFin = "08:50",
                    aula = "Pabellón H - Aula 302",
                    tipo = "Teoría"
                )
            )
        )

        val recordatorios = ClassReminderCalculator.calcularRecordatorios(
            cursos = listOf(curso),
            fecha = fechaLunes,
            esSemanaActivaOverride = true
        )

        assertEquals(1, recordatorios.size)
        val rec = recordatorios[0]
        assertEquals(LocalTime.of(7, 0), rec.triggerInstant.toLocalTime())
        assertEquals("Ingeniería de Software · 07:15 · Pabellón H - Aula 302 (Teoría)", rec.mensaje)
    }

    @Test
    fun dosBloquesSeguidos_seFusionanEnUnaSolaNotificacion() {
        // Martes 2026-04-14 (día index 1)
        val fechaMartes = LocalDate.of(2026, 4, 14)
        // Dos bloques continuos: 08:00 a 09:30 y 09:30 a 11:00
        val curso = HorarioCurso(
            crn = "202",
            nombre = "Redes y Comunicaciones",
            bloques = listOf(
                HorarioBloque(
                    dia = 1,
                    horaInicio = "08:00",
                    horaFin = "09:30",
                    aula = "Pabellón G - Aula 201",
                    tipo = "Práctica"
                ),
                HorarioBloque(
                    dia = 1,
                    horaInicio = "09:30",
                    horaFin = "11:00",
                    aula = "Pabellón G - Aula 201",
                    tipo = "Práctica"
                )
            )
        )

        val recordatorios = ClassReminderCalculator.calcularRecordatorios(
            cursos = listOf(curso),
            fecha = fechaMartes,
            esSemanaActivaOverride = true
        )

        // Debe generar exactamente 1 notificación a las 07:45 (15 min antes del inicio del bloque continuo)
        assertEquals(1, recordatorios.size)
        val rec = recordatorios[0]
        assertEquals(LocalTime.of(7, 45), rec.triggerInstant.toLocalTime())
        assertEquals("Redes y Comunicaciones · 08:00 · Pabellón G - Aula 201 (Práctica)", rec.mensaje)
    }

    @Test
    fun domingoSinClases_noGeneraRecordatorios() {
        // Domingo 2026-04-19
        val fechaDomingo = LocalDate.of(2026, 4, 19)
        val curso = HorarioCurso(
            crn = "101",
            nombre = "Ingeniería de Software",
            bloques = listOf(
                HorarioBloque(
                    dia = 0,
                    horaInicio = "07:15",
                    horaFin = "08:50",
                    aula = "Pabellón H - Aula 302"
                )
            )
        )

        val recordatorios = ClassReminderCalculator.calcularRecordatorios(
            cursos = listOf(curso),
            fecha = fechaDomingo,
            esSemanaActivaOverride = true
        )

        assertTrue(recordatorios.isEmpty())
    }

    @Test
    fun fueraDeSemestreAcademico_noGeneraRecordatorios() {
        // Lunes en periodo vacacional o fuera de ciclo
        val fechaLunes = LocalDate.of(2026, 1, 19)
        val curso = HorarioCurso(
            crn = "101",
            nombre = "Ingeniería de Software",
            bloques = listOf(
                HorarioBloque(
                    dia = 0,
                    horaInicio = "07:15",
                    horaFin = "08:50",
                    aula = "Pabellón H - Aula 302"
                )
            )
        )

        val recordatorios = ClassReminderCalculator.calcularRecordatorios(
            cursos = listOf(curso),
            fecha = fechaLunes,
            esSemanaActivaOverride = false
        )

        assertTrue(recordatorios.isEmpty())
    }
}

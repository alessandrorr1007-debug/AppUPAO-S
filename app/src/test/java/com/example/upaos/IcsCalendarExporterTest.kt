package com.example.upaos

import com.example.upaos.data.model.HorarioBloque
import com.example.upaos.data.model.HorarioCurso
import com.example.upaos.data.upao.IcsCalendarExporter
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

class IcsCalendarExporterTest {

    @Test
    fun generarIcs_horarioEjemplo_produceEstructuraCorrectaYValida() {
        // Inicio semestre: 31-ago-2026 (lunes)
        val fechaInicio = LocalDate.of(2026, 8, 31)
        val ahoraFijo = ZonedDateTime.of(2026, 8, 31, 12, 0, 0, 0, ZoneOffset.UTC)

        val curso = HorarioCurso(
            crn = "12345",
            nombre = "Ingeniería de Software; Pruebas, Calidad",
            bloques = listOf(
                HorarioBloque(
                    dia = 0, // Lunes
                    horaInicio = "07:15",
                    horaFin = "08:50",
                    aula = "Pabellón H - Aula 302",
                    tipo = "Teoría",
                    docente = "Perez Juan"
                )
            )
        )

        val ics = IcsCalendarExporter.generarIcs(
            cursos = listOf(curso),
            term = "202620",
            fechaInicioOverride = fechaInicio,
            ahoraUtc = ahoraFijo
        )

        // Verificaciones de estructura RFC 5545
        assertTrue(ics.contains("BEGIN:VCALENDAR"))
        assertTrue(ics.contains("END:VCALENDAR"))
        assertTrue(ics.contains("TZID:America/Lima"))
        assertTrue(ics.contains("BEGIN:VEVENT"))
        assertTrue(ics.contains("UID:upao-202620-12345-0-071500@upaos"))
        assertTrue(ics.contains("DTSTART;TZID=America/Lima:20260831T071500"))
        assertTrue(ics.contains("DTEND;TZID=America/Lima:20260831T085000"))
        // 16 semanas: fin de semana 16 es domingo 2026-12-20 a las 23:59:59 PET -> 2026-12-21 04:59:59 UTC
        assertTrue(ics.contains("RRULE:FREQ=WEEKLY;UNTIL=20261221T045959Z"))
        // Caracteres especiales escapados: ';' -> '\;' y ',' -> '\,'
        assertTrue(ics.contains("SUMMARY:Ingeniería de Software\\; Pruebas\\, Calidad"))
        assertTrue(ics.contains("LOCATION:Pabellón H - Aula 302"))
        assertTrue(ics.contains("DESCRIPTION:Docente: Perez Juan\\nTipo: Teoría"))
        assertTrue(ics.contains("STATUS:CONFIRMED"))
        assertTrue(ics.contains("END:VEVENT"))
    }

    @Test
    fun generarIcs_bloquesContinuos_seFusionanEnUnSoloEvento() {
        val fechaInicio = LocalDate.of(2026, 8, 31)

        val curso = HorarioCurso(
            crn = "55555",
            nombre = "Algoritmos y Estructura de Datos",
            bloques = listOf(
                HorarioBloque(
                    dia = 1, // Martes
                    horaInicio = "08:00",
                    horaFin = "09:35",
                    aula = "Pabellón G - Aula 201",
                    tipo = "Laboratorio"
                ),
                HorarioBloque(
                    dia = 1, // Martes contiguo
                    horaInicio = "09:35",
                    horaFin = "11:10",
                    aula = "Pabellón G - Aula 201",
                    tipo = "Laboratorio"
                )
            )
        )

        val ics = IcsCalendarExporter.generarIcs(
            cursos = listOf(curso),
            term = "202620",
            fechaInicioOverride = fechaInicio
        )

        // Debe contener exactamente 1 bloque VEVENT continuo de 08:00 a 11:10
        val veventCount = ics.split("BEGIN:VEVENT").size - 1
        assertEquals(1, veventCount)
        assertTrue(ics.contains("DTSTART;TZID=America/Lima:20260901T080000"))
        assertTrue(ics.contains("DTEND;TZID=America/Lima:20260901T111000"))
    }

    @Test
    fun foldLine_lineaMuyLarga_pliegaCorrectamenteA75Bytes() {
        val lineaLarga = "DESCRIPTION:" + "A".repeat(100)
        val plegada = IcsCalendarExporter.foldLine(lineaLarga, maxBytes = 75)

        val partes = plegada.split("\r\n ")
        assertEquals(2, partes.size)
        // La primera parte no excede 75 bytes
        assertTrue(partes[0].toByteArray(Charsets.UTF_8).size <= 75)
        // La segunda parte combinada con el espacio no excede 75 bytes
        assertTrue((" " + partes[1]).toByteArray(Charsets.UTF_8).size <= 75)
    }

    @Test
    fun escapeIcsText_caracteresEspeciales_seEscapanAdecuadamente() {
        val texto = "A\\B;C,D\nE\r\nF"
        val escapado = IcsCalendarExporter.escapeIcsText(texto)
        assertEquals("A\\\\B\\;C\\,D\\nE\\nF", escapado)
    }
}

package com.example.upaos

import com.example.upaos.data.agenda.*
import com.example.upaos.data.model.HorarioBloque
import com.example.upaos.data.model.HorarioCurso
import com.example.upaos.ui.home.nextClass
import org.junit.Assert.*
import org.junit.Test

class AgendaTest {
    private fun task(id: Long, date: String?, submitted: Boolean = false, course: Long = 1) = AgendaItem(id, course, "Curso", "Entrega $id", date, submitted, false, null)

    @Test fun datesRespectOffsetsAndRejectInvalidData() {
        assertEquals(parseDeadline("2026-10-10T15:00:00Z"), parseDeadline("2026-10-10T10:00:00-05:00"))
        assertEquals(parseDeadline("2026-10-10T15:00:00Z"), parseDeadline("2026-10-10T15:00:00.000Z"))
        assertNull(parseDeadline("2026-02-30T15:00:00Z"))
        assertNull(parseDeadline("2026-10-10T15:00:00Zjunk"))
        assertNull(parseDeadline(null))
    }
    @Test fun pendingTasksComeFirstAndKeepUndatedLast() {
        val list = prioritizeAgenda(listOf(task(1, null), task(2, "2026-10-10T15:00:00Z", true), task(3, "2026-10-11T15:00:00Z"), task(4, "2026-10-09T15:00:00Z")), parseDeadline("2026-10-10T15:00:00Z")!!)
        assertEquals(listOf(3L, 4L, 1L, 2L), list.map { it.id })
    }
    @Test fun duplicateIdsFromDifferentCoursesAreKept() {
        assertEquals(2, prioritizeAgenda(listOf(task(1, null), task(1, null), task(1, null, course = 2))).size)
    }
    @Test fun submissionStateTakesPriorityOverOverdueDate() {
        val due = "2026-10-01T15:00:00Z"
        val now = parseDeadline("2026-10-10T15:00:00Z")!!
        assertEquals("Vencida · sin entregar", task(1, due).status(now))
        assertEquals("Entregada", task(1, due, true).status(now))
        assertEquals("Pendiente", task(1, null).status(now))
    }
    @Test fun nextClassKeepsCurrentClassAndRollsPastClassesToNextWeek() {
        // Monday 10:30 Lima.
        val now = parseDeadline("2026-10-12T10:30:00-05:00")!!
        val course = HorarioCurso(nombre = "Matemática", bloques = listOf(HorarioBloque(dia = 0, horaInicio = "10:00", horaFin = "11:00", aula = "A101")))
        val current = nextClass(listOf(course), now)!!
        assertEquals("A101", current.room)
        assertTrue(current.start < now && current.end > now)
        val next = nextClass(listOf(course), now + 60 * 60_000)!!
        assertEquals(current.start + 7 * 86_400_000L, next.start)
    }
}

package com.example.upaos

import com.example.upaos.data.agenda.AgendaItem
import com.example.upaos.data.agenda.parseDeadline
import com.example.upaos.data.model.*
import com.example.upaos.ui.home.*
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class TodayRulesTest {
    private val now = parseDeadline("2026-10-12T12:00:00-05:00")!!
    private fun assignment(id: Long, date: String?, submitted: Boolean = false) = AgendaItem(id, 1, "Curso", "Tarea", date, submitted, false, null)

    @Test fun todayExcludesYesterdayTomorrowUndatedAndSubmitted() {
        val items = listOf(
            assignment(1, "2026-10-12T00:00:00-05:00"),
            assignment(2, "2026-10-12T23:59:59-05:00"),
            assignment(3, "2026-10-13T00:00:00-05:00"),
            assignment(4, "2026-10-11T23:59:59-05:00"),
            assignment(5, null), assignment(6, "2026-10-12T18:00:00-05:00", true)
        )
        assertEquals(listOf(1L, 2L), assignmentsToday(items, now).map { it.id })
    }
    @Test fun utcDeadlineIsFilteredByPeruvianCalendarDate() {
        assertEquals(listOf(1L), assignmentsToday(listOf(assignment(1, "2026-10-13T04:59:59Z"), assignment(2, "2026-10-13T05:00:00Z")), now).map { it.id })
    }
    @Test fun newsOnlyContainsExistingEventsFromToday() {
        val items = listOf(
            NotificacionItem(id = 1, fechaCreacion = "2026-10-12T06:00:00Z"),
            NotificacionItem(id = 2, fechaCreacion = "2026-10-11T23:30:00-05:00"),
            NotificacionItem(id = 3, fechaCreacion = "2026-10-13T00:00:00-05:00"),
            NotificacionItem(id = 4, fechaCreacion = "2026-10-12T18:00:00-05:00"),
            NotificacionItem(id = 5, fechaCreacion = null),
            NotificacionItem(id = 6, fechaCreacion = "2026-10-12T11:00:00-05:00")
        )
        assertEquals(listOf(6L, 1L), notificationsToday(items, now).map { it.id })
    }
    @Test fun serverMicrosecondsDoNotShiftTheTimestamp() {
        assertEquals(parseDeadline("2026-10-12T17:00:00.123Z"), notificationMillis("2026-10-12T17:00:00.123456+00:00"))
        assertEquals(parseDeadline("2026-10-12T17:00:00Z"), notificationMillis("2026-10-12T17:00:00"))
        assertNull(notificationMillis("invalid"))
    }
    @Test fun classesIncludeFinishedTodayButNeverRollIntoAnotherDay() {
        val course = HorarioCurso(nombre = "Curso", bloques = listOf(
            HorarioBloque(dia = 0, horaInicio = "08:00", horaFin = "10:00", aula = " A101 "),
            HorarioBloque(dia = 0, horaInicio = "18:00", horaFin = "20:00"),
            HorarioBloque(dia = 1, horaInicio = "08:00", horaFin = "10:00")
        ))
        val today = classesToday(listOf(course), now)
        assertEquals(2, today.size)
        assertEquals(listOf("A101", "Virtual"), today.map { it.room })
        assertTrue(today.first().end < now)
        assertTrue(today.last().start > now)
        assertTrue(today.all { it.start in campusDay(now) })
    }
    @Test fun noClassesTodayDoesNotShowTheNextWeek() {
        val course = HorarioCurso(nombre = "Mañana", bloques = listOf(HorarioBloque(dia = 1, horaInicio = "08:00", horaFin = "10:00")))
        assertTrue(classesToday(listOf(course), now).isEmpty())
    }
    @Test fun phoneTimezoneDoesNotChangeTheCampusDay() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))
            assertEquals(parseDeadline("2026-10-12T05:00:00Z"), campusDay(now).first)
            assertEquals(parseDeadline("2026-10-13T05:00:00Z")!! - 1, campusDay(now).last)
        } finally { TimeZone.setDefault(original) }
    }
}

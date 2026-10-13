package com.example.upaos.ui.home

import com.example.upaos.data.agenda.AgendaItem
import com.example.upaos.data.agenda.parseDeadline
import com.example.upaos.data.agenda.parseServerDate
import com.example.upaos.data.model.HorarioCurso
import com.example.upaos.data.model.NotificacionItem
import java.util.Calendar
import java.util.TimeZone

val campusTimeZone: TimeZone get() = TimeZone.getTimeZone("America/Lima")

fun campusDay(now: Long): LongRange {
    val start = Calendar.getInstance(campusTimeZone).apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val end = (start.clone() as Calendar).apply { add(Calendar.DATE, 1) }
    return start.timeInMillis until end.timeInMillis
}

fun assignmentsToday(items: List<AgendaItem>, now: Long): List<AgendaItem> {
    val day = campusDay(now)
    return items.distinctBy { it.key }.filter {
        !it.submitted && !it.graded && it.dueMillis?.let(day::contains) == true
    }.sortedBy { it.dueMillis }
}

fun notificationMillis(value: String?): Long? {
    // Old Render responses were naive server UTC; new responses include their offset.
    return parseServerDate(value)
}

fun notificationsToday(items: List<NotificacionItem>, now: Long): List<NotificacionItem> {
    val day = campusDay(now)
    return items.filter { notificationMillis(it.fechaCreacion)?.let { date -> date in day && date <= now } == true }
        .sortedWith(compareByDescending<NotificacionItem> { notificationMillis(it.fechaCreacion) }.thenByDescending { it.id })
}

fun classesToday(courses: List<HorarioCurso>, now: Long): List<NextClass> {
    val calendar = Calendar.getInstance(campusTimeZone).apply { timeInMillis = now }
    val weekday = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
    fun minutes(value: String?): Int? {
        val parts = value?.split(':') ?: return null
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
    }
    val midnight = campusDay(now).first
    return courses.flatMap { course -> course.bloques.mapNotNull { block ->
        if (block.dia != weekday) return@mapNotNull null
        val start = minutes(block.horaInicio) ?: return@mapNotNull null
        val end = minutes(block.horaFin) ?: return@mapNotNull null
        if (end <= start) return@mapNotNull null
        NextClass(course.displayNombre, block.aula?.trim()?.takeIf { it.isNotEmpty() } ?: "Virtual", midnight + start * 60_000L, midnight + end * 60_000L)
    } }.distinct().sortedBy { it.start }
}

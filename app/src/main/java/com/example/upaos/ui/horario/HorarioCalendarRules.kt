package com.example.upaos.ui.horario

import com.example.upaos.data.model.HorarioBloque
import com.example.upaos.data.model.HorarioCurso
import com.example.upaos.data.upao.AcademicCalendar
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

internal fun scheduleCalendar(date: Long): Calendar =
    Calendar.getInstance(TimeZone.getTimeZone("America/Lima")).apply { timeInMillis = date }

internal fun scheduleDateStart(date: Long): Long = scheduleCalendar(date).apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

internal fun scheduleMonthDays(date: Long): List<Long> {
    val calendar = scheduleCalendar(scheduleDateStart(date)).apply { set(Calendar.DAY_OF_MONTH, 1) }
    return List(calendar.getActualMaximum(Calendar.DAY_OF_MONTH)) {
        calendar.timeInMillis.also { calendar.add(Calendar.DAY_OF_MONTH, 1) }
    }
}

internal fun moveScheduleMonth(date: Long, months: Int): Long =
    scheduleCalendar(date).apply { add(Calendar.MONTH, months) }.timeInMillis

internal data class ScheduleDayClass(val curso: HorarioCurso, val bloque: HorarioBloque)

internal fun classesForScheduleDate(
    courses: List<HorarioCurso>,
    date: Long,
    term: String? = null
): List<ScheduleDayClass> {
    if (term != null) {
        val semanaInfo = AcademicCalendar.calcularSemana(term, Date(date))
        if (semanaInfo.fueraDeCiclo == true) {
            return emptyList()
        }
    }
    val weekday = (scheduleCalendar(date).get(Calendar.DAY_OF_WEEK) + 5) % 7
    fun minutes(value: String?): Int {
        val parts = value?.split(':') ?: return Int.MAX_VALUE
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return Int.MAX_VALUE
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return Int.MAX_VALUE
        return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else Int.MAX_VALUE
    }
    return courses.flatMap { course ->
        course.bloques.filter { it.dia == weekday }.map { ScheduleDayClass(course, it) }
    }.sortedBy { minutes(it.bloque.horaInicio) }
}

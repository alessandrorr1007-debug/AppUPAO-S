package com.example.upaos.data.upao

import com.example.upaos.data.model.SemanaInfo
import com.example.upaos.domain.academic.AcademicRules
import java.time.temporal.ChronoUnit
import java.util.Date

/** Compatibility facade; rules and date provenance live in the domain layer. */
object AcademicCalendar {
    const val TOTAL_SEMANAS_REGULARES = AcademicRules.TOTAL_WEEKS

    fun estimarFechaInicio(term: String): String? = AcademicRules.startDate(term)?.date?.toString()

    fun calcularSemana(term: String, fechaReferencia: Date = Date()): SemanaInfo {
        val start = AcademicRules.startDate(term)
            ?: return SemanaInfo(configurada = false, totalSemanas = TOTAL_SEMANAS_REGULARES)
        val today = fechaReferencia.toInstant().atZone(AcademicRules.LIMA).toLocalDate()
        val days = ChronoUnit.DAYS.between(start.date, today).toInt()
        val week = if (days >= 0) days / 7 + 1 else 0
        val active = week in 1..TOTAL_SEMANAS_REGULARES
        return SemanaInfo(
            configurada = true,
            semana = week.takeIf { active },
            totalSemanas = TOTAL_SEMANAS_REGULARES,
            etiqueta = when {
                week == 0 -> "El ciclo aún no inicia"
                active -> "Semana $week de $TOTAL_SEMANAS_REGULARES"
                else -> "Ciclo concluido"
            },
            fueraDeCiclo = !active,
            fechaInicio = start.date.toString(),
            diasTranscurridos = days.takeIf { it >= 0 },
            fechaEstimada = !start.verified
        )
    }
}

package com.example.upaos.service

import com.example.upaos.data.model.HorarioCurso
import com.example.upaos.data.upao.AcademicCalendar
import com.example.upaos.data.upao.ScheduleCalculator
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.Date
import kotlin.math.abs

data class RecordatorioClase(
    val triggerInstant: ZonedDateTime,
    val horaInicioClase: String,
    val titulo: String,
    val mensaje: String,
    val idAlarma: Int
)

object ClassReminderCalculator {

    /**
     * Calcula los recordatorios de clases (15 minutos antes del inicio de cada bloque fusionado)
     * para la fecha dada.
     *
     * @param cursos lista de cursos con bloques (los bloques deben estar fusionados o se fusionan aquí).
     * @param fecha fecha a consultar (en zona horaria de Lima).
     * @param term periodo académico activo para verificar semanas válidas en AcademicCalendar.
     * @param esSemanaActivaOverride opcional para tests unitarios.
     * @return lista de recordatorios ordenados cronológicamente.
     */
    fun calcularRecordatorios(
        cursos: List<HorarioCurso>,
        fecha: LocalDate,
        term: String = "",
        esSemanaActivaOverride: Boolean? = null
    ): List<RecordatorioClase> {
        // 1. En domingo no hay clases ni recordatorios
        if (fecha.dayOfWeek == DayOfWeek.SUNDAY) {
            return emptyList()
        }

        // 2. Solo semanas académicas regulares activas (1 a 16)
        val esSemanaActiva = esSemanaActivaOverride ?: run {
            val fechaRef = Date.from(fecha.atStartOfDay(ScheduleCalculator.LIMA_ZONE).toInstant())
            val infoSemana = AcademicCalendar.calcularSemana(term, fechaRef)
            infoSemana.configurada && infoSemana.fueraDeCiclo == false &&
                    infoSemana.semana != null && infoSemana.semana in 1..AcademicCalendar.TOTAL_SEMANAS_REGULARES
        }

        if (!esSemanaActiva) {
            return emptyList()
        }

        // 3. Índice del día de la semana (0 = Lunes, ..., 5 = Sábado)
        val diaIndex = fecha.dayOfWeek.value - 1
        if (diaIndex !in 0..5) return emptyList()

        val recordatorios = mutableListOf<RecordatorioClase>()

        for (curso in cursos) {
            // Asegurar que los bloques estén fusionados (un solo bloque continuo por clase)
            val bloquesFusionados = ScheduleCalculator.fusionarBloques(curso.bloques)

            for (bloque in bloquesFusionados) {
                if (bloque.dia != diaIndex) continue

                val horaInicioStr = bloque.horaInicio?.trim().orEmpty()
                if (horaInicioStr.isBlank() || !horaInicioStr.contains(":")) continue

                val partes = horaInicioStr.split(":")
                val hh = partes.getOrNull(0)?.toIntOrNull() ?: continue
                val mm = partes.getOrNull(1)?.toIntOrNull() ?: continue

                val horaInicio = LocalTime.of(hh, mm)
                val inicioClase = fecha.atTime(horaInicio).atZone(ScheduleCalculator.LIMA_ZONE)
                val triggerInstant = inicioClase.minusMinutes(15)

                val nombreCurso = curso.displayNombre
                val aulaStr = bloque.aula?.trim().takeIf { !it.isNullOrBlank() } ?: "Sin aula asignada"
                val tipoStr = bloque.displayTipo?.trim().takeIf { !it.isNullOrBlank() }

                val mensaje = if (tipoStr != null) {
                    "$nombreCurso · $horaInicioStr · $aulaStr ($tipoStr)"
                } else {
                    "$nombreCurso · $horaInicioStr · $aulaStr"
                }

                // ID determinista para evitar duplicados al reprogramar
                val claveUnica = "${fecha}_${curso.crn ?: nombreCurso}_$horaInicioStr"
                val idAlarma = abs(claveUnica.hashCode()) % 1_000_000

                recordatorios.add(
                    RecordatorioClase(
                        triggerInstant = triggerInstant,
                        horaInicioClase = horaInicioStr,
                        titulo = "Recordatorio de clase",
                        mensaje = mensaje,
                        idAlarma = idAlarma
                    )
                )
            }
        }

        return recordatorios.sortedBy { it.triggerInstant }
    }
}

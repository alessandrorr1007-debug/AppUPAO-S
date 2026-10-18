package com.example.upaos.data.upao

import com.example.upaos.data.model.SemanaInfo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Configuración centralizada del calendario académico de la UPAO.
 * Permite calcular la semana actual del ciclo sin depender de endpoints remotos de Render.
 */
object AcademicCalendar {

    const val TOTAL_SEMANAS_REGULARES = 16

    /**
     * Fechas oficiales o estimadas de inicio de clases por periodo académico (formato YYYY-MM-DD).
     */
    val FECHAS_INICIO_CICLO: Map<String, String> = mapOf(
        "202610" to "2026-03-23",
        "202620" to "2026-08-31",
        "202510" to "2025-03-24",
        "202520" to "2025-09-01"
    )

    fun estimarFechaInicio(term: String): String {
        FECHAS_INICIO_CICLO[term]?.let { return it }
        if (term.length == 6) {
            val year = term.substring(0, 4)
            val tipo = term.substring(4, 6)
            return when (tipo) {
                "00" -> "$year-01-05"
                "10" -> "$year-03-23"
                "20" -> "$year-08-31"
                else -> "$year-03-23"
            }
        }
        return "2026-03-23"
    }

    /**
     * Calcula la semana académica para un periodo dado respecto a la fecha actual (o una fecha de referencia).
     */
    fun calcularSemana(term: String, fechaReferencia: Date = Date()): SemanaInfo {
        val fechaInicioStr = FECHAS_INICIO_CICLO[term] ?: estimarFechaInicio(term)

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val fechaInicio = try {
            sdf.parse(fechaInicioStr) ?: return SemanaInfo(configurada = false, totalSemanas = TOTAL_SEMANAS_REGULARES)
        } catch (_: Exception) {
            return SemanaInfo(configurada = false, totalSemanas = TOTAL_SEMANAS_REGULARES)
        }

        val calRef = Calendar.getInstance().apply { time = fechaReferencia }
        val calInicio = Calendar.getInstance().apply { time = fechaInicio }

        // Normalizar a medianoche para cálculo exacto de días
        calRef.set(Calendar.HOUR_OF_DAY, 0)
        calRef.set(Calendar.MINUTE, 0)
        calRef.set(Calendar.SECOND, 0)
        calRef.set(Calendar.MILLISECOND, 0)

        calInicio.set(Calendar.HOUR_OF_DAY, 0)
        calInicio.set(Calendar.MINUTE, 0)
        calInicio.set(Calendar.SECOND, 0)
        calInicio.set(Calendar.MILLISECOND, 0)

        val diffMillis = calRef.timeInMillis - calInicio.timeInMillis
        val diasTranscurridos = (diffMillis / (1000 * 60 * 60 * 24)).toInt()

        val semana = if (diasTranscurridos >= 0) {
            (diasTranscurridos / 7) + 1
        } else {
            0
        }

        val (etiqueta, fuera) = when {
            semana == 0 -> "El ciclo aún no inicia" to true
            semana in 1..TOTAL_SEMANAS_REGULARES -> "Semana $semana de $TOTAL_SEMANAS_REGULARES" to false
            semana == TOTAL_SEMANAS_REGULARES + 1 -> "Semana 17 · Sustitutorios" to false
            semana == TOTAL_SEMANAS_REGULARES + 2 -> "Semana 18 · Exámenes Finales" to false
            else -> "Ciclo concluido" to true
        }

        return SemanaInfo(
            configurada = true,
            semana = if (semana in 1..TOTAL_SEMANAS_REGULARES) semana else null,
            totalSemanas = TOTAL_SEMANAS_REGULARES,
            etiqueta = etiqueta,
            fueraDeCiclo = fuera,
            fechaInicio = fechaInicioStr,
            diasTranscurridos = if (diasTranscurridos >= 0) diasTranscurridos else null
        )
    }
}

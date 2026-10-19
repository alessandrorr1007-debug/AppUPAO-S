package com.example.upaos.data.upao

import com.example.upaos.data.model.HorarioBloque
import com.example.upaos.data.model.HorarioCurso
import java.time.LocalDate
import java.time.ZoneId

object ScheduleCalculator {

    val LIMA_ZONE: ZoneId = ZoneId.of("America/Lima")
    val DIAS_NOMBRES = listOf("LUN", "MAR", "MIE", "JUE", "VIE", "SAB", "DOM")
    val FLAG_DIAS = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")

    /**
     * Calcula el índice del día de la semana (0 = Lunes, 1 = Martes, ..., 5 = Sábado, 6 = Domingo)
     * en la zona horaria de Lima (America/Lima).
     */
    fun diaSemanaLima(fecha: LocalDate = LocalDate.now(LIMA_ZONE)): Int {
        return fecha.dayOfWeek.value - 1
    }

    /**
     * Determina si el índice del día (0 = Lunes, ..., 5 = Sábado) corresponde al día real actual.
     */
    fun esHoy(dia: Int, fecha: LocalDate = LocalDate.now(LIMA_ZONE)): Boolean {
        return dia == diaSemanaLima(fecha)
    }

    /**
     * Retorna el índice del día que debe quedar visible al abrir el horario (0 = Lunes, ..., 5 = Sábado).
     * Si hoy es domingo (índice 6) o está fuera de rango, regresa 0 (Lunes).
     */
    fun diaParaAutoScroll(fecha: LocalDate = LocalDate.now(LIMA_ZONE)): Int {
        val dia = diaSemanaLima(fecha)
        return if (dia in 0..5) dia else 0
    }

    /**
     * Calcula el desplazamiento horizontal para centrar el chip/columna del día especificado en pantalla.
     */
    fun calcularScrollOffsetDp(diaIndex: Int, columnaWidthDp: Float = 140f, screenWidthDp: Float): Float {
        val diaClamped = if (diaIndex in 0..5) diaIndex else 0
        val targetCenter = diaClamped * columnaWidthDp + (columnaWidthDp / 2f)
        return (targetCenter - (screenWidthDp / 2f)).coerceAtLeast(0f)
    }


    /**
     * Convierte "0700" a ("07:00", "7:00 AM") y "1430" a ("14:30", "2:30 PM").
     */
    fun convertirHora(raw: Any?): Pair<String, String> {
        val s = raw?.toString()?.trim().orEmpty()
        if (s.length == 4 && s.all { it.isDigit() }) {
            val hh = s.substring(0, 2).toIntOrNull() ?: return s to s
            val mm = s.substring(2, 4)
            val suffix = if (hh < 12) "AM" else "PM"
            val h12 = when {
                hh == 0 -> 12
                hh > 12 -> hh - 12
                else -> hh
            }
            val hora24 = "${s.substring(0, 2)}:$mm"
            val hora12 = "$h12:$mm $suffix"
            return hora24 to hora12
        }
        return s to s
    }

    /**
     * Normaliza el código de edificio para que sea legible (ej. PAB-G -> PABELLON G).
     */
    fun normalizarEdificio(edificio: String?): String? {
        val clean = edificio?.trim()
        if (clean.isNullOrBlank()) return null
        return when {
            clean.startsWith("PAB-", ignoreCase = true) -> {
                val letra = clean.substring(4).trim()
                "PABELLON $letra"
            }
            clean.startsWith("PAB ", ignoreCase = true) -> {
                val letra = clean.substring(4).trim()
                "PABELLON $letra"
            }
            else -> clean
        }
    }

    /**
     * Genera la etiqueta completa de aula ("PABELLON G G-402").
     */
    fun formatoAula(edificio: String?, salon: String?): String? {
        val ed = normalizarEdificio(edificio)
        val sal = salon?.trim()
        return listOfNotNull(ed, sal).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
    }

    /**
     * Fusiona bloques consecutivos del mismo día (horaFin == horaInicio siguiente).
     */
    fun fusionarBloques(bloques: List<HorarioBloque>): List<HorarioBloque> {
        if (bloques.isEmpty()) return emptyList()

        val porDia = bloques.groupBy { it.dia ?: 0 }
        val resultado = mutableListOf<HorarioBloque>()

        for ((_, listaDia) in porDia) {
            val ordenada = listaDia.sortedBy { it.horaInicio ?: "" }
            if (ordenada.isEmpty()) continue

            val merged = mutableListOf(ordenada[0])
            for (i in 1 until ordenada.size) {
                val actual = ordenada[i]
                val ultimo = merged.last()

                if (!ultimo.horaFin.isNullOrBlank() && ultimo.horaFin == actual.horaInicio) {
                    val aulaFinal = ultimo.aula ?: actual.aula
                    val docenteFinal = ultimo.displayDocente ?: actual.displayDocente
                    val tipoFinal = ultimo.tipo ?: actual.tipo

                    val fusionado = ultimo.copy(
                        horaFin = actual.horaFin,
                        horaFin12h = actual.horaFin12h,
                        aula = aulaFinal,
                        docente = docenteFinal,
                        profesor = docenteFinal,
                        nombreDocente = docenteFinal,
                        tipo = tipoFinal
                    )
                    merged[merged.size - 1] = fusionado
                } else {
                    merged.add(actual)
                }
            }
            resultado.addAll(merged)
        }

        return resultado.sortedWith(compareBy({ it.dia ?: 0 }, { it.horaInicio ?: "" }))
    }
}

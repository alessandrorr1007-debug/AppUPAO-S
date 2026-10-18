package com.example.upaos.data.upao

import com.example.upaos.data.model.HorarioBloque
import com.example.upaos.data.model.HorarioCurso

object ScheduleCalculator {

    val DIAS_NOMBRES = listOf("LUN", "MAR", "MIE", "JUE", "VIE", "SAB", "DOM")
    val FLAG_DIAS = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")

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

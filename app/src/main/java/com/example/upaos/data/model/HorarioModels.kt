package com.example.upaos.data.model

import com.google.gson.annotations.SerializedName

/**
 * Extrae y formatea el nombre del docente/profesor sin importar si viene como:
 * - String: "PEREZ JUAN"
 * - List: ["PEREZ JUAN", "LOPEZ CARLOS"]
 * - Map: {"nombre": "PEREZ JUAN"}
 * - Objeto con campo docente/profesor/name/instructor
 */
fun extraerNombreDocente(value: Any?): String? {
    if (value == null) return null
    return when (value) {
        is String -> {
            val s = value.trim()
            if (s.isEmpty() || s.equals("null", ignoreCase = true)) null else s
        }
        is List<*> -> {
            val nombres = value.mapNotNull { elem ->
                when (elem) {
                    is String -> elem.trim().takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                    is Map<*, *> -> {
                        (elem["nombre"] ?: elem["name"] ?: elem["docente"] ?: elem["profesor"] ?: elem["instructor"])
                            ?.toString()?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                    }
                    else -> elem?.toString()?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                }
            }
            if (nombres.isNotEmpty()) nombres.distinct().joinToString(" / ") else null
        }
        is Map<*, *> -> {
            (value["nombre"] ?: value["name"] ?: value["docente"] ?: value["profesor"] ?: value["instructor"])
                ?.toString()?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        }
        else -> {
            val s = value.toString().trim()
            if (s.isEmpty() || s.equals("null", ignoreCase = true)) null else s
        }
    }
}

data class HorarioBloque(
    @SerializedName("dia") val dia: Int? = null,
    @SerializedName("dia_nombre") val diaNombre: String? = null,
    @SerializedName("hora_inicio") val horaInicio: String? = null,
    @SerializedName("hora_fin") val horaFin: String? = null,
    @SerializedName("hora_inicio_12h") val horaInicio12h: String? = null,
    @SerializedName("hora_fin_12h") val horaFin12h: String? = null,
    @SerializedName("aula") val aula: String? = null,
    @SerializedName("tipo") val tipo: String? = null,
    @SerializedName("tipo_componente") val tipoComponente: String? = null,
    @SerializedName("tipo_horario") val tipoHorario: String? = null,
    @SerializedName("seccion") val seccion: String? = null,
    @SerializedName("nrc") val nrc: String? = null,
    @SerializedName("crn") val crn: String? = null,
    @SerializedName("docente") val docente: Any? = null,
    @SerializedName("profesor") val profesor: Any? = null,
    @SerializedName("instructor") val instructor: Any? = null,
    @SerializedName("docentes") val docentes: Any? = null,
    @SerializedName("profesores") val profesores: Any? = null,
    @SerializedName("instructores") val instructores: Any? = null,
    @SerializedName("docente_nombre") val docenteNombre: Any? = null,
    @SerializedName("profesor_nombre") val profesorNombre: Any? = null,
    @SerializedName("nombre_docente") val nombreDocente: Any? = null,
    @SerializedName("nombre_profesor") val nombreProfesor: Any? = null,
    @SerializedName("teacher") val teacher: Any? = null,
    @SerializedName("teachers") val teachers: Any? = null,
    @SerializedName("instructors") val instructors: Any? = null
) {
    val displayDocente: String?
        get() = extraerNombreDocente(docente)
            ?: extraerNombreDocente(profesor)
            ?: extraerNombreDocente(instructor)
            ?: extraerNombreDocente(docentes)
            ?: extraerNombreDocente(profesores)
            ?: extraerNombreDocente(instructores)
            ?: extraerNombreDocente(docenteNombre)
            ?: extraerNombreDocente(profesorNombre)
            ?: extraerNombreDocente(nombreDocente)
            ?: extraerNombreDocente(nombreProfesor)
            ?: extraerNombreDocente(teacher)
            ?: extraerNombreDocente(teachers)
            ?: extraerNombreDocente(instructors)

    val displayTipo: String?
        get() = tipo?.takeIf { it.isNotBlank() }
            ?: tipoComponente?.takeIf { it.isNotBlank() }
            ?: tipoHorario?.takeIf { it.isNotBlank() }
}

data class HorarioCurso(
    @SerializedName("crn") val crn: String? = null,
    @SerializedName("codigo_materia") val codigoMateria: String? = null,
    @SerializedName("numero_curso") val numeroCurso: String? = null,
    @SerializedName("nombre") val nombre: String? = null,
    @SerializedName("seccion") val seccion: String? = null,
    @SerializedName("docente") val docente: Any? = null,
    @SerializedName("profesor") val profesor: Any? = null,
    @SerializedName("instructor") val instructor: Any? = null,
    @SerializedName("docentes") val docentes: Any? = null,
    @SerializedName("profesores") val profesores: Any? = null,
    @SerializedName("instructores") val instructores: Any? = null,
    @SerializedName("docente_nombre") val docenteNombre: Any? = null,
    @SerializedName("profesor_nombre") val profesorNombre: Any? = null,
    @SerializedName("nombre_docente") val nombreDocente: Any? = null,
    @SerializedName("nombre_profesor") val nombreProfesor: Any? = null,
    @SerializedName("teacher") val teacher: Any? = null,
    @SerializedName("teachers") val teachers: Any? = null,
    @SerializedName("instructors") val instructors: Any? = null,
    @SerializedName("bloques") val bloques: List<HorarioBloque> = emptyList()
) {
    val displayNombre: String
        get() = nombre?.takeIf { it.isNotBlank() } ?: "Curso $crn"

    val displayCodigo: String
        get() = listOf(codigoMateria, numeroCurso)
            .filterNotNull()
            .filter { it.isNotBlank() }
            .joinToString(" ")

    val displayDocente: String?
        get() = extraerNombreDocente(docente)
            ?: extraerNombreDocente(profesor)
            ?: extraerNombreDocente(instructor)
            ?: extraerNombreDocente(docentes)
            ?: extraerNombreDocente(profesores)
            ?: extraerNombreDocente(instructores)
            ?: extraerNombreDocente(docenteNombre)
            ?: extraerNombreDocente(profesorNombre)
            ?: extraerNombreDocente(nombreDocente)
            ?: extraerNombreDocente(nombreProfesor)
            ?: extraerNombreDocente(teacher)
            ?: extraerNombreDocente(teachers)
            ?: extraerNombreDocente(instructors)
}

data class HorarioResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("periodo") val periodo: String? = null,
    @SerializedName("total_cursos") val totalCursos: Int = 0,
    @SerializedName("total_bloques") val totalBloques: Int = 0,
    @SerializedName("cursos") val cursos: List<HorarioCurso> = emptyList(),
    @SerializedName("horario") val horario: List<HorarioCurso>? = null
) {
    val listaCursos: List<HorarioCurso>
        get() = if (cursos.isNotEmpty()) cursos else (horario ?: emptyList())
}

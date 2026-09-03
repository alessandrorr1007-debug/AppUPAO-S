package com.example.upaos.data.model

import com.google.gson.annotations.SerializedName

data class HorarioBloque(
    @SerializedName("dia") val dia: Int? = null,
    @SerializedName("dia_nombre") val diaNombre: String? = null,
    @SerializedName("hora_inicio") val horaInicio: String? = null,
    @SerializedName("hora_fin") val horaFin: String? = null,
    @SerializedName("hora_inicio_12h") val horaInicio12h: String? = null,
    @SerializedName("hora_fin_12h") val horaFin12h: String? = null,
    @SerializedName("aula") val aula: String? = null
)

data class HorarioCurso(
    @SerializedName("crn") val crn: String? = null,
    @SerializedName("codigo_materia") val codigoMateria: String? = null,
    @SerializedName("numero_curso") val numeroCurso: String? = null,
    @SerializedName("nombre") val nombre: String? = null,
    @SerializedName("bloques") val bloques: List<HorarioBloque> = emptyList()
) {
    val displayNombre: String
        get() = nombre?.takeIf { it.isNotBlank() } ?: "Curso $crn"

    val displayCodigo: String
        get() = listOf(codigoMateria, numeroCurso)
            .filterNotNull()
            .filter { it.isNotBlank() }
            .joinToString(" ")
}

data class HorarioResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("periodo") val periodo: String? = null,
    @SerializedName("total_cursos") val totalCursos: Int = 0,
    @SerializedName("total_bloques") val totalBloques: Int = 0,
    @SerializedName("cursos") val cursos: List<HorarioCurso> = emptyList()
)

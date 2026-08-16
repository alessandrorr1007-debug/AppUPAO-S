package com.example.upaos.data.model

import com.google.gson.annotations.SerializedName

data class ComponenteAsistencia(
    @SerializedName("crn") val crn: String? = null,
    @SerializedName("seccion") val seccion: String? = null,
    @SerializedName("porcentaje") val porcentaje: Double? = null,
    @SerializedName("faltas") val faltas: Int? = null,
    @SerializedName("horario_dias") val horarioDias: String? = null,
    @SerializedName("hora_12h") val hora12h: String? = null
)

data class AsistenciaCurso(
    @SerializedName("crn") val crn: String? = null,
    @SerializedName("materia") val materia: String? = null,
    @SerializedName("codigo_materia") val codigoMateria: String? = null,
    @SerializedName("nombre_curso") val nombreCurso: String? = null,
    @SerializedName("seccion") val seccion: String? = null,
    @SerializedName("periodo") val periodo: String? = null,
    @SerializedName("faltas") val faltas: Int? = null,
    @SerializedName("porcentaje") val porcentaje: Double? = null,
    @SerializedName("horario_dias") val horarioDias: String? = null,
    @SerializedName("hora") val hora: String? = null,
    @SerializedName("hora_12h") val hora12h: String? = null,
    @SerializedName("total_secciones") val totalSecciones: Int? = null,
    @SerializedName("componentes") val componentes: List<ComponenteAsistencia>? = null
) {
    val displayNombre: String
        get() = nombreCurso ?: materia ?: "Curso"
}

data class AsistenciaResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("totalCount") val totalCount: Int = 0,
    @SerializedName("total_secciones") val totalSecciones: Int = 0,
    @SerializedName("asistencia") val asistencia: List<AsistenciaCurso> = emptyList()
)

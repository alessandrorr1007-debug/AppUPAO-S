package com.example.upaos.data.model

import com.google.gson.annotations.SerializedName

data class GradeDetail(
    @SerializedName("componente") val componente: String? = "Evaluación",
    @SerializedName("nota") val nota: Any? = null
)

data class SubComponente(
    @SerializedName("nombre") val nombre: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("componente") val componente: String? = null,
    @SerializedName("codigo") val codigo: String? = null,
    @SerializedName("peso") val peso: Any? = null,
    @SerializedName("weight") val weight: Any? = null,
    @SerializedName("percentage") val percentage: Any? = null,
    @SerializedName("porcentaje_logrado") val porcentajeLogrado: Any? = null,
    @SerializedName("puntaje_obtenido") val puntajeObtenido: Any? = null,
    @SerializedName("puntaje_sobre") val puntajeSobre: Any? = null,
    @SerializedName("grade") val grade: Any? = null,
    @SerializedName("nota") val nota: Any? = null,
    @SerializedName("score") val score: Any? = null
) {
    val displayNombre: String
        get() = nombre ?: description ?: componente ?: "Sub-componente"

    val displayNota: Any?
        get() = puntajeObtenido ?: grade ?: nota

    val displayPeso: Any?
        get() = peso ?: weight
}

data class ComponenteDetalle(
    @SerializedName("nombre") val nombre: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("componente") val componente: String? = null,
    @SerializedName("codigo") val codigo: String? = null,
    @SerializedName("peso") val peso: Any? = null,
    @SerializedName("weight") val weight: Any? = null,
    @SerializedName("porcentaje_logrado") val porcentajeLogrado: Any? = null,
    @SerializedName("puntaje_obtenido") val puntajeObtenido: Any? = null,
    @SerializedName("puntaje_sobre") val puntajeSobre: Any? = null,
    @SerializedName("grade") val grade: Any? = null,
    @SerializedName("nota") val nota: Any? = null,
    @SerializedName("score") val score: Any? = null,
    @SerializedName("grade_oficial") val gradeOficial: Any? = null,
    @SerializedName("componentId") val componentId: Long? = null,
    @SerializedName("hasSubComponents") val hasSubComponents: Boolean = false,
    @SerializedName("subcomponentes") val subcomponentes: List<SubComponente> = emptyList()
) {
    val displayNombre: String
        get() = nombre ?: description ?: componente ?: "Componente"

    val displayNota: Any?
        get() = puntajeObtenido ?: grade ?: nota

    val displayPeso: Any?
        get() = peso ?: weight

    val displayPuntaje: String?
        get() {
            val obtenido = puntajeObtenido?.toString()
            val sobre = puntajeSobre?.toString()
            return if (obtenido != null && sobre != null &&
                obtenido.toDoubleOrNull() != null && sobre.toDoubleOrNull() != null
            ) "$obtenido/$sobre" else null
        }
}

data class DetalleCursoResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("totalCount") val totalCount: Int = 0,
    @SerializedName("detalles") val detalles: List<ComponenteDetalle> = emptyList(),
    @SerializedName("nota_proyectada") val notaProyectada: Any? = null,
    @SerializedName("pesos_pendientes") val pesosPendientes: List<String> = emptyList()
)

data class GradeSection(
    @SerializedName("nota") val nota: Any? = null,
    @SerializedName("detalles") val detalles: List<GradeDetail> = emptyList()
)

data class CourseGrade(
    @SerializedName("nombre") val nombre: String? = null,
    @SerializedName("courseTitle") val courseTitle: String? = null,
    @SerializedName("subjectDescription") val subjectDescription: String? = null,
    @SerializedName("ep1") val ep1: GradeSection? = GradeSection(),
    @SerializedName("ep2") val ep2: GradeSection? = GradeSection(),
    @SerializedName("nota_actual") val notaActual: Any? = null,
    @SerializedName("crn") val crn: String? = null,
    @SerializedName("courseReferenceNumber") val courseReferenceNumber: String? = null,
    @SerializedName("raw_banner") val rawBanner: Map<String, Any?>? = null
) {
    val displayNombre: String
        get() = nombre ?: courseTitle ?: subjectDescription ?: "Curso Matriculado"

    val displayNotaActual: Any?
        get() = notaActual

    val displayEp1: GradeSection
        get() = ep1 ?: GradeSection()

    val displayEp2: GradeSection
        get() = ep2 ?: GradeSection()
}

data class GradesResponse(
    @SerializedName("periodo") val periodo: String? = "202610",
    @SerializedName("carrera") val carrera: String? = "UG",
    @SerializedName("ultima_actualizacion") val ultimaActualizacion: String? = null,
    @SerializedName("cursos") val cursos: List<CourseGrade> = emptyList(),
    @SerializedName("promedio_general") val promedioGeneral: Any? = null,
    @SerializedName("promedio_basado_en") val promedioBasadoEn: String? = null
)

data class PeriodosResponse(
    @SerializedName("periodo_actual") val periodoActual: String? = "202610",
    @SerializedName("periodos") val periodos: List<String> = emptyList()
)

data class CarrerasResponse(
    @SerializedName("carreras") val carreras: List<String> = emptyList()
)

data class AutoCheckRequest(
    @SerializedName("enabled") val enabled: Boolean
)

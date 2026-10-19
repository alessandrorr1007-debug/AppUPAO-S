package com.example.upaos.data.model

import com.google.gson.annotations.SerializedName
import java.text.Normalizer

data class SemanaInfo(
    @SerializedName("configurada") val configurada: Boolean = false,
    @SerializedName("semana") val semana: Int? = null,
    @SerializedName("total_semanas") val totalSemanas: Int = 16,
    @SerializedName("etiqueta") val etiqueta: String? = null,
    @SerializedName("fuera_de_ciclo") val fueraDeCiclo: Boolean? = null,
    @SerializedName("fecha_inicio") val fechaInicio: String? = null,
    @SerializedName("dias_transcurridos") val diasTranscurridos: Int? = null
)

// ---------- Modelos para Promedio Ponderado Semestral (PPS) ----------

data class PromedioCursoItem(
    @SerializedName("crn") val crn: String = "",
    @SerializedName("nombre") val nombre: String = "",
    @SerializedName("nota") val nota: Float? = null,
    @SerializedName("creditos") val creditos: Int? = null
)

data class PromedioPeriodoResponse(
    @SerializedName("periodo") val periodo: String,
    @SerializedName("pps_oficial") val ppsOficial: Float? = null,
    @SerializedName("pps_calculado") val ppsCalculado: Float? = null,
    @SerializedName("fuente") val fuente: String = "calculado",
    @SerializedName("total_creditos") val totalCreditos: Int? = null,
    @SerializedName("cursos") val cursos: List<PromedioCursoItem> = emptyList()
)

/** course_id único = subjectCode + courseNumber (ej. 'HUMA-1185'), normalizado sin tildes. */
fun normalizarCourseId(codigoMateria: Any?, numeroCurso: Any?): String {
    val codigo = codigoMateria?.toString().orEmpty()
    val numero = numeroCurso?.toString().orEmpty()
    val raw = if (numero.isNotBlank()) "$codigo-$numero" else codigo
    val sinTildes = Normalizer.normalize(raw, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .uppercase()
    return sinTildes.replace(Regex("[^A-Z0-9]"), "-")
        .replace(Regex("-+"), "-")
        .trim('-')
}

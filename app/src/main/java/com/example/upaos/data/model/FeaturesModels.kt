package com.example.upaos.data.model

import com.google.gson.annotations.SerializedName
import java.text.Normalizer

data class CuentaInfo(
    @SerializedName("usuario") val usuario: String? = null,
    @SerializedName("nombre") val nombre: String? = null,
    @SerializedName("is_admin") val isAdmin: Boolean = false,
    @SerializedName("ranking_optin") val rankingOptin: Boolean = false,
    @SerializedName("auto_check_enabled") val autoCheckEnabled: Boolean = false,
    @SerializedName("tiene_password_guardada") val tienePasswordGuardada: Boolean = false,
    @SerializedName("fecha_primer_login") val fechaPrimerLogin: String? = null
)

data class SemanaInfo(
    @SerializedName("configurada") val configurada: Boolean = false,
    @SerializedName("semana") val semana: Int? = null,
    @SerializedName("total_semanas") val totalSemanas: Int = 16,
    @SerializedName("etiqueta") val etiqueta: String? = null,
    @SerializedName("fuera_de_ciclo") val fueraDeCiclo: Boolean? = null,
    @SerializedName("fecha_inicio") val fechaInicio: String? = null,
    @SerializedName("dias_transcurridos") val diasTranscurridos: Int? = null
)

data class SugerenciaRequest(
    @SerializedName("usuario") val usuario: String,
    @SerializedName("texto") val texto: String
)

data class SugerenciaResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("sugerencia_id") val sugerenciaId: Long? = null,
    @SerializedName("estado") val estado: String? = null
)

data class SugerenciaItem(
    @SerializedName("id") val id: Long,
    @SerializedName("texto") val texto: String,
    @SerializedName("estado") val estado: String = "pendiente",
    @SerializedName("fecha_creacion") val fechaCreacion: String? = null
)

data class MisSugerenciasResponse(
    @SerializedName("sugerencias") val sugerencias: List<SugerenciaItem> = emptyList()
)

data class RankingOptinRequest(
    @SerializedName("usuario") val usuario: String,
    @SerializedName("enabled") val enabled: Boolean
)

data class RankingResponse(
    @SerializedName("disponible") val disponible: Boolean = false,
    @SerializedName("motivo") val motivo: String? = null,
    @SerializedName("course_id") val courseId: String? = null,
    @SerializedName("ciclo") val ciclo: String? = null,
    @SerializedName("min_usuarios") val minUsuarios: Int = 5,
    @SerializedName("position") val position: Int? = null,
    @SerializedName("total") val total: Int = 0,
    @SerializedName("percentil") val percentil: Int? = null
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

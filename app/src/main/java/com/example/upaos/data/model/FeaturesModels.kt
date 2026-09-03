package com.example.upaos.data.model

import com.google.gson.annotations.SerializedName
import java.text.Normalizer

data class CuentaInfo(
    @SerializedName("usuario") val usuario: String? = null,
    @SerializedName("nombre") val nombre: String? = null,
    @SerializedName("is_admin") val isAdmin: Boolean = false,
    @SerializedName("rol") val rol: String? = null,
    @SerializedName("ranking_optin") val rankingOptin: Boolean = false,
    @SerializedName("auto_check_enabled") val autoCheckEnabled: Boolean = false,
    @SerializedName("tiene_password_guardada") val tienePasswordGuardada: Boolean = false,
    @SerializedName("fecha_primer_login") val fechaPrimerLogin: String? = null
) {
    val esAdmin: Boolean get() = isAdmin || rol == "admin"
}

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

// ---------- Modelos para el Panel Administrador ----------

data class AdminCuentaItem(
    @SerializedName("usuario") val usuario: String,
    @SerializedName("nombre") val nombre: String? = null,
    @SerializedName("is_admin") val isAdmin: Boolean = false,
    @SerializedName("ranking_optin") val rankingOptin: Boolean = false,
    @SerializedName("auto_check_enabled") val autoCheckEnabled: Boolean = false,
    @SerializedName("tiene_password_guardada") val tienePasswordGuardada: Boolean = false,
    @SerializedName("fecha_primer_login") val fechaPrimerLogin: String? = null,
    @SerializedName("ultima_revision") val ultimaRevision: String? = null
)

data class AdminCuentasResponse(
    @SerializedName("cuentas") val cuentas: List<AdminCuentaItem> = emptyList()
)

data class AdminSugerenciaItem(
    @SerializedName("id") val id: Long,
    @SerializedName("usuario") val usuario: String,
    @SerializedName("texto") val texto: String,
    @SerializedName("estado") val estado: String = "pendiente",
    @SerializedName("fecha_creacion") val fechaCreacion: String? = null
)

data class AdminSugerenciasResponse(
    @SerializedName("sugerencias") val sugerencias: List<AdminSugerenciaItem> = emptyList()
)

data class AdminEstadoSugerenciaRequest(
    @SerializedName("estado") val estado: String
)

data class AdminSemanaRequest(
    @SerializedName("fecha_inicio") val fechaInicio: String
)

data class DauPunto(
    @SerializedName("fecha") val fecha: String,
    @SerializedName("usuarios") val usuarios: Int
)

data class PicoTrafico(
    @SerializedName("fecha_hora") val fechaHora: String? = null,
    @SerializedName("usuarios_simultaneos") val usuariosSimultaneos: Int = 0
)

data class AdminMetricasResponse(
    @SerializedName("dau_30_dias") val dau30Dias: List<DauPunto> = emptyList(),
    @SerializedName("cuentas_activas_hoy") val cuentasActivasHoy: Int = 0,
    @SerializedName("pico_hoy") val picoHoy: PicoTrafico? = null,
    @SerializedName("pico_historico") val picoHistorico: PicoTrafico? = null
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

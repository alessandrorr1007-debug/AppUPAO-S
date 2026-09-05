package com.example.upaos.data.model

import com.google.gson.annotations.SerializedName
import kotlin.math.roundToInt

data class AsistenciaComponente(
    @SerializedName("crn") val crn: String? = null,
    @SerializedName("seccion") val seccion: String? = null,
    @SerializedName("tipo") val tipo: String? = null,
    @SerializedName("tipo_componente") val tipoComponente: String? = null,
    @SerializedName("porcentaje") val porcentaje: Double? = null,
    @SerializedName("faltas") val faltas: Int? = null,
    @SerializedName("asistencias") val asistencias: Int? = null,
    @SerializedName("veces_asistio") val vecesAsistio: Int? = null,
    @SerializedName("total_clases") val totalClases: Int? = null,
    @SerializedName("horario_dias") val horarioDias: String? = null,
    @SerializedName("hora") val hora: String? = null,
    @SerializedName("hora_12h") val hora12h: String? = null,
    @SerializedName("aula") val aula: String? = null,
    @SerializedName("sectionMeetingId") val sectionMeetingId: Long? = null
) {
    /**
     * Calcula o deduce la cantidad de asistencias de este componente.
     */
    fun calcularVecesAsistidas(clasesEstimadas: Int? = null): Int? {
        if (asistencias != null) return asistencias
        if (vecesAsistio != null) return vecesAsistio
        val p = porcentaje ?: return null
        val f = faltas ?: 0

        if (p <= 0.0) return 0

        // Si hay faltas y el porcentaje es menor al 100%, la fórmula matemática es exacta:
        // p = (A / (A + F)) * 100  =>  A = round((p * F) / (100 - p))
        if (f > 0 && p < 100.0) {
            val calculadas = kotlin.math.round((p * f) / (100.0 - p)).toInt()
            return calculadas.coerceAtLeast(0)
        }

        // Si la asistencia es 100% (o faltas == 0)
        if (p >= 100.0 || f == 0) {
            if (totalClases != null && totalClases > 0) return (totalClases - f).coerceAtLeast(0)
            if (clasesEstimadas != null && clasesEstimadas > 0) return (clasesEstimadas - f).coerceAtLeast(1)
            // Estimación por frecuencia semanal si no hay otro dato
            val dias = contarDiasHorario(horarioDias)
            return (dias * 4).coerceAtLeast(1)
        }

        return clasesEstimadas ?: totalClases ?: 0
    }

    val vecesAsistidas: Int?
        get() = calcularVecesAsistidas(null)

    val totalClasesCalculadas: Int?
        get() {
            if (totalClases != null && totalClases > 0) return totalClases
            val a = vecesAsistidas
            val f = faltas ?: 0
            return if (a != null) a + f else null
        }
}

data class AsistenciaCurso(
    @SerializedName("crn") val crn: String? = null,
    @SerializedName("materia") val materia: String? = null,
    @SerializedName("codigo_materia") val codigoMateria: String? = null,
    @SerializedName("nombre_curso") val nombreCurso: String? = null,
    @SerializedName("seccion") val seccion: String? = null,
    @SerializedName("periodo") val periodo: String? = null,
    @SerializedName("faltas") val faltas: Int? = null,
    @SerializedName("asistencias") val asistencias: Int? = null,
    @SerializedName("veces_asistio") val vecesAsistio: Int? = null,
    @SerializedName("total_clases") val totalClases: Int? = null,
    @SerializedName("porcentaje") val porcentaje: Double? = null,
    @SerializedName("horario_dias") val horarioDias: String? = null,
    @SerializedName("hora") val hora: String? = null,
    @SerializedName("hora_12h") val hora12h: String? = null,
    @SerializedName("aula") val aula: String? = null,
    @SerializedName("tipo") val tipo: String? = null,
    @SerializedName("tipo_componente") val tipoComponente: String? = null,
    @SerializedName("componentes") val componentes: List<AsistenciaComponente> = emptyList(),
    @SerializedName("total_secciones") val totalSecciones: Int? = null
) {
    val displayNombre: String
        get() = nombreCurso ?: materia ?: "Curso"

    fun calcularVecesAsistidas(clasesEstimadas: Int? = null): Int? {
        if (componentes.isNotEmpty()) {
            val suma = componentes.sumOf { it.calcularVecesAsistidas(clasesEstimadas) ?: 0 }
            return if (suma > 0 || componentes.any { it.vecesAsistidas != null }) suma else null
        }
        if (asistencias != null) return asistencias
        if (vecesAsistio != null) return vecesAsistio
        val p = porcentaje ?: return null
        val f = faltas ?: 0
        if (p <= 0.0) return 0
        if (f > 0 && p < 100.0) {
            val calculadas = kotlin.math.round((p * f) / (100.0 - p)).toInt()
            return calculadas.coerceAtLeast(0)
        }
        if (p >= 100.0 || f == 0) {
            if (totalClases != null && totalClases > 0) return (totalClases - f).coerceAtLeast(0)
            if (clasesEstimadas != null && clasesEstimadas > 0) return (clasesEstimadas - f).coerceAtLeast(1)
            val dias = contarDiasHorario(horarioDias)
            return (dias * 4).coerceAtLeast(1)
        }
        return clasesEstimadas ?: totalClases ?: 0
    }

    /**
     * Veces que el alumno asistió a clases en todo el curso (sumando componentes si existen).
     */
    val vecesAsistidas: Int?
        get() = calcularVecesAsistidas(null)

    /**
     * Total de faltas acumuladas en el curso (sumando componentes si existen).
     */
    val totalFaltasCalculadas: Int
        get() = if (componentes.isNotEmpty()) {
            componentes.sumOf { it.faltas ?: 0 }
        } else {
            faltas ?: 0
        }

    /**
     * Total de clases registradas (asistencias + faltas).
     */
    val totalClasesCalculadas: Int?
        get() {
            if (componentes.isNotEmpty()) {
                val suma = componentes.sumOf { it.totalClasesCalculadas ?: 0 }
                return if (suma > 0) suma else null
            }
            if (totalClases != null && totalClases > 0) return totalClases
            val a = vecesAsistidas
            val f = faltas ?: 0
            return if (a != null) a + f else null
        }

    /**
     * Devuelve los componentes clasificados asignando inteligentemente
     * Teoría, Práctica o Laboratorio cuando la API no lo trae etiquetado.
     */
    fun componentesTipificados(): List<Pair<String, AsistenciaComponente>> {
        if (componentes.isEmpty()) {
            val tipoDetectado = clasificarTipo(tipo ?: tipoComponente, seccion, displayNombre, 0, 1)
            val unico = AsistenciaComponente(
                crn = crn,
                seccion = seccion,
                tipo = tipoDetectado,
                porcentaje = porcentaje,
                faltas = faltas,
                asistencias = asistencias,
                vecesAsistio = vecesAsistio,
                totalClases = totalClases,
                horarioDias = horarioDias,
                hora = hora,
                hora12h = hora12h,
                aula = aula
            )
            return listOf(tipoDetectado to unico)
        }

        return componentes.mapIndexed { index, comp ->
            val tipoFinal = comp.tipo?.takeIf { it.isNotBlank() } ?: clasificarTipo(
                tipoApi = comp.tipo ?: comp.tipoComponente,
                seccion = comp.seccion,
                nombreCurso = displayNombre,
                index = index,
                totalComponentes = componentes.size
            )
            tipoFinal to comp.copy(tipo = tipoFinal)
        }
    }
}

data class AsistenciaResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("totalCount") val totalCount: Int = 0,
    @SerializedName("total_secciones") val totalSecciones: Int? = null,
    @SerializedName("asistencia") val asistencia: List<AsistenciaCurso> = emptyList()
)

fun clasificarTipo(
    tipoApi: String?,
    seccion: String?,
    nombreCurso: String,
    index: Int,
    totalComponentes: Int
): String {
    if (!tipoApi.isNullOrBlank()) {
        val t = tipoApi.trim().lowercase()
        when {
            t.contains("teor") -> return "Teoría"
            t.contains("lab") -> return "Laboratorio"
            t.contains("prac") -> return "Práctica"
            else -> return tipoApi.trim().replaceFirstChar { it.uppercase() }
        }
    }

    if (!seccion.isNullOrBlank()) {
        val sec = seccion.trim().uppercase()
        when {
            sec.contains("LAB") || sec.endsWith("L") -> return "Laboratorio"
            sec.contains("PRAC") || sec.endsWith("P") -> return "Práctica"
            sec.contains("TEOR") || sec.endsWith("T") -> return "Teoría"
        }
    }

    if (totalComponentes <= 1) return "Teoría"

    // Si hay 2 componentes: el 1ro es Teoría y el 2do es Laboratorio (o Práctica si el nombre no tiene lab)
    if (totalComponentes == 2) {
        val nombreMayus = nombreCurso.uppercase()
        val tieneLab = nombreMayus.contains("LAB") ||
                nombreMayus.contains("COMPUT") ||
                nombreMayus.contains("SISTEM") ||
                nombreMayus.contains("REDES") ||
                nombreMayus.contains("PROGRAM") ||
                nombreMayus.contains("DATOS") ||
                nombreMayus.contains("FISICA") ||
                nombreMayus.contains("QUIMICA") ||
                nombreMayus.contains("BIOLOG") ||
                nombreMayus.contains("ELECTR") ||
                nombreMayus.contains("DESARROLLO") ||
                nombreMayus.contains("INTELIG")
        return if (index == 0) "Teoría" else if (tieneLab) "Laboratorio" else "Práctica"
    }

    return when (index) {
        0 -> "Teoría"
        1 -> "Práctica"
        else -> "Laboratorio"
    }
}

fun contarDiasHorario(horario: String?): Int {
    if (horario.isNullOrBlank()) return 1
    val sinAcentos = horario.uppercase()
        .replace("Á", "A").replace("É", "E").replace("Í", "I")
        .replace("Ó", "O").replace("Ú", "U")
    val dias = listOf("LUN", "MAR", "MIE", "JUE", "VIE", "SAB", "DOM")
    val encontrados = dias.count { sinAcentos.contains(it) }
    return if (encontrados > 0) encontrados else 1
}

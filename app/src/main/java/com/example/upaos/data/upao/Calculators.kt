package com.example.upaos.data.upao

import com.example.upaos.data.model.ComponenteDetalle
import com.example.upaos.data.model.PromedioCursoItem
import java.util.Locale
import kotlin.math.roundToInt

object Calculators {

    /**
     * Pesos por periodo: 202520 usa 25/20/30/25; los siguientes, 20/30/20/30.
     * Si el nombre no coincide con las siglas estándar, intenta usar el peso reportado por Banner.
     */
    fun pesoComponente(componente: ComponenteDetalle, periodo: String? = null): Double? {
        val texto = listOfNotNull(
            componente.nombre,
            componente.description,
            componente.codigo,
            componente.componente
        ).joinToString(" ").uppercase(Locale.ROOT)

        val historico = periodo?.trim() == "202520"
        val reconocido = when {
            texto.contains("FINAL") || texto.contains("EVF") -> if (historico) 25.0 else 30.0
            texto.contains("PARCIAL") || texto.contains("EVP") -> if (historico) 20.0 else 30.0
            texto.contains("EP2") || texto.contains("PROCESO 2") || texto.contains("PERMANENTE 2") || texto.contains("EP 2") -> if (historico) 30.0 else 20.0
            texto.contains("EP1") || texto.contains("PROCESO 1") || texto.contains("PERMANENTE 1") || texto.contains("EP 1") -> if (historico) 25.0 else 20.0
            else -> null
        }
        return reconocido ?: toDouble(componente.displayPeso?.toString()?.replace("%", ""))
            ?.takeIf { it.isFinite() && it > 0 && it <= 100 }
    }

    data class Progreso(val acumulado: Double, val pesoEvaluado: Double) {
        val promedio: Double get() = acumulado * 100.0 / pesoEvaluado
        val minimoAcumulado: Double get() = com.example.upaos.domain.academic.AcademicRules.PASS_GRADE * pesoEvaluado / 100.0
        val enRitmoAprobatorio: Boolean get() = acumulado + 1e-9 >= minimoAcumulado
    }

    fun progreso(componentes: List<ComponenteDetalle>, periodo: String? = null): Progreso? {
        var puntos = 0.0
        var evaluado = 0.0
        for (componente in componentes) {
            val nota = toDouble(componente.displayNota)?.takeIf { it.isFinite() && it in 0.0..20.0 } ?: continue
            val peso = pesoComponente(componente, periodo)?.takeIf { it.isFinite() && it > 0 && it <= 100 } ?: continue
            puntos += nota * peso / 100.0
            evaluado += peso
        }
        return if (evaluado > 0 && evaluado <= 100.0 + 1e-9) Progreso(puntos, evaluado) else null
    }

    fun progresoCurso(curso: com.example.upaos.data.model.CourseGrade, periodo: String? = null): Progreso? =
        progreso(curso.componentes.ifEmpty {
            listOf(ComponenteDetalle(nombre = "EP1", nota = curso.displayEp1.nota),
                ComponenteDetalle(nombre = "EP2", nota = curso.displayEp2.nota))
        }, periodo)

    fun toDouble(v: Any?): Double? {
        if (v == null) return null
        return when (v) {
            is Number -> v.toDouble()
            is String -> {
                val s = v.trim()
                if (s.isEmpty() || s == "null" || s == "None" || s == "-") null
                else s.toDoubleOrNull()
            }
            else -> null
        }
    }

    /**
     * Calcula la nota actual progresiva de un curso:
     * Promedio ponderado de los componentes que YA tienen nota, normalizado por la suma de sus pesos.
     * Ejemplo: EP1=15 (20%) y Parcial=14 (30%) -> (15*20 + 14*30) / (20+30) = 14.4
     */
    fun calcularNotaActual(componentes: List<ComponenteDetalle>, periodo: String? = null): Double? {
        var sumaPonderada = 0.0
        var sumaPesos = 0.0

        for (c in componentes) {
            val peso = pesoComponente(c, periodo) ?: continue
            if (peso <= 0.0) continue
            val nota = toDouble(c.displayNota) ?: continue

            sumaPonderada += nota * peso
            sumaPesos += peso
        }

        if (sumaPesos <= 0.0) return null
        return (sumaPonderada / sumaPesos * 100.0).roundToInt() / 100.0
    }

    /**
     * Calcula la nota proyectada (acumulada sobre el total del curso) y lista los componentes pendientes.
     * Si el examen final ya tiene nota, la proyección no aplica.
     */
    fun calcularNotaProyectada(componentes: List<ComponenteDetalle>, periodo: String? = null): Pair<Double?, List<String>> {
        val compFinal = componentes.firstOrNull { c ->
            val txt = listOfNotNull(c.nombre, c.description, c.codigo).joinToString(" ").uppercase(Locale.ROOT)
            txt.contains("FINAL") || txt.contains("EVF")
        }

        if (compFinal != null && toDouble(compFinal.displayNota) != null) {
            return Pair(null, emptyList())
        }

        var sumaPonderada = 0.0
        val pendientes = mutableListOf<String>()
        var componentesConNota = 0

        for (c in componentes) {
            val peso = pesoComponente(c, periodo) ?: continue
            if (peso <= 0.0) continue
            val nota = toDouble(c.displayNota)

            if (nota == null) {
                pendientes.add(c.displayNombre)
            } else {
                sumaPonderada += nota * (peso / 100.0)
                componentesConNota++
            }
        }

        if (componentesConNota == 0) {
            return Pair(null, emptyList())
        }

        val proyectada = (sumaPonderada * 100.0).roundToInt() / 100.0
        return Pair(proyectada, pendientes)
    }

    /**
     * Cálculo oficial del Promedio Ponderado Semestral (PPS) de la UPAO:
     * PPS = Σ(redondear(nota) * creditos) / Σ(creditos)
     * Cada nota se redondea a entero (>= .5 sube) antes de multiplicar por los créditos.
     */
    fun calcularPps(cursos: List<PromedioCursoItem>): Double? {
        val validos = mutableListOf<Pair<Double, Int>>()

        for (c in cursos) {
            val nota = c.nota?.toDouble()
            val cred = c.creditos
            if (nota != null && cred != null && cred > 0) {
                validos.add(nota to cred)
            }
        }

        if (validos.isEmpty()) return null

        var sumaPonderada = 0
        var totalCreditos = 0

        for ((nota, cred) in validos) {
            val notaEntera = (nota + 0.5).toInt()
            sumaPonderada += notaEntera * cred
            totalCreditos += cred
        }

        if (totalCreditos <= 0) return null

        val pps = sumaPonderada.toDouble() / totalCreditos
        return (pps * 10000.0).roundToInt() / 10000.0
    }

    /**
     * Valida si un código de periodo corresponde a un ciclo regular (termina en 10 o 20).
     * Los códigos terminados en 90 son idiomas / extraordinarios.
     */
    fun esPeriodoRegular(code: String?): Boolean =
        com.example.upaos.domain.academic.AcademicRules.isRegular(code)

    fun detectarPeriodoActual(periodos: List<String>, fallback: String? = null): String =
        com.example.upaos.domain.academic.AcademicRules.latestAvailable(periodos, fallback)
}

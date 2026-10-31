package com.example.upaos.ui.calculadora

import com.example.upaos.data.model.CourseGrade
import com.example.upaos.data.upao.Calculators
import java.util.Locale
import kotlin.math.ceil

/** Fill only blank fields, using an equal required grade across the remaining weights. */
internal fun completePassingGrades(values: List<String>, weights: List<Int>, target: Double): List<String>? {
    require(values.size == weights.size && weights.all { it > 0 })
    val missing = values.indices.filter { values[it].isBlank() }
    if (missing.isEmpty()) return null
    val grades = values.map { value ->
        if (value.isBlank()) null else value.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it in 0.0..20.0 } ?: return null
    }
    val accumulated = grades.indices.sumOf { (grades[it] ?: 0.0) * weights[it] / 100.0 }
    val remainingWeight = missing.sumOf { weights[it] } / 100.0
    val required = ceil(((target - accumulated) / remainingWeight).coerceAtLeast(0.0) * 100.0 - 1e-9) / 100.0
    if (required > 20.0) return null
    val text = if (required % 1.0 == 0.0) required.toInt().toString() else String.format(Locale.US, "%.2f", required)
    return values.mapIndexed { index, value -> if (index in missing) text else value }
}

/** Only recorded component grades are imported; averages never stand in for an exam. */
internal fun simulationGrades(course: CourseGrade): List<String> {
    fun grade(value: Any?): String? = Calculators.toDouble(value)
        ?.takeIf { it.isFinite() && it in 0.0..20.0 }
        ?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }
    val values = MutableList(4) { "" }
    course.componentes.forEach { component ->
        val name = listOfNotNull(component.nombre, component.description, component.codigo, component.componente)
            .joinToString(" ").uppercase(Locale.ROOT)
        val index = when {
            name.contains("FINAL") || name.contains("EVF") -> 3
            name.contains("PARCIAL") || name.contains("EVP") -> 1
            listOf("EP2", "EP 2", "PROCESO 2", "PERMANENTE 2").any(name::contains) -> 2
            listOf("EP1", "EP 1", "PROCESO 1", "PERMANENTE 1").any(name::contains) -> 0
            else -> -1
        }
        if (index >= 0) grade(component.displayNota)?.let { values[index] = it }
    }
    if (values[0].isBlank()) values[0] = grade(course.displayEp1.nota).orEmpty()
    if (values[2].isBlank()) values[2] = grade(course.displayEp2.nota).orEmpty()
    return values
}

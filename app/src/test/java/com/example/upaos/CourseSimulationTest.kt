package com.example.upaos

import com.example.upaos.data.model.ComponenteDetalle
import com.example.upaos.data.model.CourseGrade
import com.example.upaos.data.model.GradeSection
import com.example.upaos.ui.calculadora.simulationGrades
import com.example.upaos.ui.calculadora.completePassingGrades
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseSimulationTest {
    private val weights = listOf(20, 30, 20, 30)

    @Test fun fillsOnlyTheMissingExamAndRoundsUpToReachTheTarget() {
        val result = completePassingGrades(listOf("14", "12", "10", ""), weights, 10.5)!!
        assertEquals(listOf("14", "12", "10", "7"), result)
        val fractional = completePassingGrades(listOf("13", "12", "10", ""), weights, 10.5)!!
        assertEquals("7.67", fractional[3])
        assertTrue(fractional.indices.sumOf { fractional[it].toDouble() * weights[it] / 100.0 } >= 10.5)
    }

    @Test fun distributesAcrossMissingComponentsWithoutChangingEnteredGrades() {
        assertEquals(listOf("15", "9.38", "9.38", "9.38"),
            completePassingGrades(listOf("15", "", "", ""), weights, 10.5))
        assertEquals(List(4) { "10.50" }, completePassingGrades(List(4) { "" }, weights, 10.5))
    }

    @Test fun handlesImpossibleInvalidCompleteAndAlreadyPassingInputs() {
        assertNull(completePassingGrades(listOf("0", "0", "0", ""), weights, 10.5))
        assertNull(completePassingGrades(listOf("21", "", "", ""), weights, 10.5))
        assertNull(completePassingGrades(listOf("NaN", "", "", ""), weights, 10.5))
        assertNull(completePassingGrades(listOf("12", "12", "12", "12"), weights, 10.5))
        assertEquals(listOf("20", "20", "20", "0"),
            completePassingGrades(listOf("20", "20", "20", ""), weights, 10.5))
    }

    @Test fun importsRecordedComponentsAndLeavesMissingExamBlank() {
        val course = CourseGrade(componentes = listOf(
            ComponenteDetalle(nombre = "Evaluación permanente 1", nota = "14.5"),
            ComponenteDetalle(codigo = "EVP", nota = 0),
            ComponenteDetalle(nombre = "EP 2", nota = 16),
            ComponenteDetalle(codigo = "EVF", nota = "SN")
        ))
        assertEquals(listOf("14.5", "0", "16", ""), simulationGrades(course))
    }

    @Test fun legacySectionsAreSupportedWithoutImportingTheCourseAverage() {
        val course = CourseGrade(notaActual = 18, ep1 = GradeSection(nota = 12), ep2 = GradeSection(nota = 15))
        assertEquals(listOf("12", "", "15", ""), simulationGrades(course))
    }

    @Test fun rejectsInvalidGradesAndPreservesRecordedPrecision() {
        val course = CourseGrade(componentes = listOf(
            ComponenteDetalle(nombre = "EP1", nota = Double.NaN),
            ComponenteDetalle(nombre = "Parcial", nota = 21),
            ComponenteDetalle(nombre = "EP2", nota = -1),
            ComponenteDetalle(nombre = "Final", nota = 13.375)
        ))
        assertEquals(listOf("", "", "", "13.375"), simulationGrades(course))
    }

    @Test fun switchingCoursesProducesANewIndependentSetOfValues() {
        val first = CourseGrade(ep1 = GradeSection(nota = 15))
        val second = CourseGrade(componentes = listOf(ComponenteDetalle(nombre = "Parcial", nota = 11)))
        val edited = simulationGrades(first).toMutableList().also { it[3] = "20" }
        assertEquals(listOf("", "11", "", ""), simulationGrades(second))
        assertEquals(listOf("15", "", "", ""), simulationGrades(first))
        assertEquals("20", edited[3])
    }
}

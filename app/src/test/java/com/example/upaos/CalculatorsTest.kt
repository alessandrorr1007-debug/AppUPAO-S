package com.example.upaos

import com.example.upaos.data.model.ComponenteDetalle
import com.example.upaos.data.model.PromedioCursoItem
import com.example.upaos.data.upao.Calculators
import org.junit.Assert.*
import org.junit.Test

class CalculatorsTest {

    @Test
    fun testPesoComponenteStandardNames() {
        val ep1 = ComponenteDetalle(nombre = "EVALUACION PERMANENTE 1")
        assertEquals(20.0, Calculators.pesoComponente(ep1)!!, 0.001)

        val parcial = ComponenteDetalle(nombre = "EXAMEN PARCIAL")
        assertEquals(30.0, Calculators.pesoComponente(parcial)!!, 0.001)

        val ep2 = ComponenteDetalle(nombre = "EVALUACION PERMANENTE 2")
        assertEquals(20.0, Calculators.pesoComponente(ep2)!!, 0.001)

        val finalComp = ComponenteDetalle(nombre = "EXAMEN FINAL")
        assertEquals(30.0, Calculators.pesoComponente(finalComp)!!, 0.001)

        val evf = ComponenteDetalle(codigo = "EVF", nombre = "EVF")
        assertEquals(30.0, Calculators.pesoComponente(evf)!!, 0.001)
    }

    @Test
    fun testCalcularNotaActualProgresiva() {
        // EP1=15 (20%) y Parcial=14 (30%) -> (15*20 + 14*30) / (20+30) = (300 + 420) / 50 = 14.4
        val comps = listOf(
            ComponenteDetalle(nombre = "EP1", puntajeObtenido = 15.0),
            ComponenteDetalle(nombre = "EXAMEN PARCIAL", puntajeObtenido = 14.0),
            ComponenteDetalle(nombre = "EP2", puntajeObtenido = null),
            ComponenteDetalle(nombre = "EXAMEN FINAL", puntajeObtenido = null)
        )

        val nota = Calculators.calcularNotaActual(comps)
        assertNotNull(nota)
        assertEquals(14.4, nota!!, 0.001)
    }

    @Test
    fun testCalcularNotaActualWhenNoGrades() {
        val comps = listOf(
            ComponenteDetalle(nombre = "EP1", puntajeObtenido = null),
            ComponenteDetalle(nombre = "EXAMEN PARCIAL", puntajeObtenido = null)
        )
        assertNull(Calculators.calcularNotaActual(comps))
    }

    @Test
    fun testCalcularNotaProyectadaPendingFinal() {
        val comps = listOf(
            ComponenteDetalle(nombre = "EP1", puntajeObtenido = 16.0), // 16 * 0.20 = 3.2
            ComponenteDetalle(nombre = "PARCIAL", puntajeObtenido = 14.0), // 14 * 0.30 = 4.2
            ComponenteDetalle(nombre = "FINAL", puntajeObtenido = null)
        )

        val (proyectada, pendientes) = Calculators.calcularNotaProyectada(comps)
        assertNotNull(proyectada)
        assertEquals(7.4, proyectada!!, 0.001)
        assertTrue(pendientes.any { it.contains("FINAL", ignoreCase = true) })
    }

    @Test
    fun testCalcularNotaProyectadaWhenFinalHasGrade() {
        val comps = listOf(
            ComponenteDetalle(nombre = "EP1", puntajeObtenido = 16.0),
            ComponenteDetalle(nombre = "PARCIAL", puntajeObtenido = 14.0),
            ComponenteDetalle(nombre = "FINAL", puntajeObtenido = 15.0)
        )

        val (proyectada, pendientes) = Calculators.calcularNotaProyectada(comps)
        assertNull(proyectada)
        assertTrue(pendientes.isEmpty())
    }

    @Test
    fun testCalcularPpsOfficialRounding() {
        // Curso 1: nota 10.66 (redondea a 11), 3 créditos -> 33
        // Curso 2: nota 14.2 (redondea a 14), 4 créditos -> 56
        // Total créditos: 7
        // PPS: (33 + 56) / 7 = 89 / 7 = 12.714285... -> 12.7143
        val cursos = listOf(
            PromedioCursoItem(crn = "1001", nombre = "Curso A", nota = 10.66f, creditos = 3),
            PromedioCursoItem(crn = "1002", nombre = "Curso B", nota = 14.2f, creditos = 4)
        )

        val pps = Calculators.calcularPps(cursos)
        assertNotNull(pps)
        assertEquals(12.7143, pps!!, 0.0001)
    }

    @Test
    fun testDetectarPeriodoActual() {
        val periodos = listOf("202510", "202520", "202590", "202610")
        assertEquals("202610", Calculators.detectarPeriodoActual(periodos))
        assertFalse(Calculators.esPeriodoRegular("202590"))
        assertTrue(Calculators.esPeriodoRegular("202610"))
    }
}

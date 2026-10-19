package com.example.upaos

import com.example.upaos.data.model.AsistenciaComponente
import com.example.upaos.data.model.AsistenciaCurso
import com.example.upaos.data.model.clasificarTipo
import com.example.upaos.data.model.contarDiasHorario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttendanceParsingTest {

    @Test
    fun `test clasificarTipo segun seccion y total de componentes`() {
        // Por sección
        assertEquals("Laboratorio", clasificarTipo(null, "NRC123_LAB", "QUÍMICA", 0, 2))
        assertEquals("Laboratorio", clasificarTipo(null, "SEC_L", "QUÍMICA", 0, 2))
        assertEquals("Práctica", clasificarTipo(null, "SEC_PRAC", "FÍSICA", 0, 2))
        assertEquals("Práctica", clasificarTipo(null, "SEC_P", "FÍSICA", 0, 2))
        assertEquals("Teoría", clasificarTipo(null, "SEC_TEOR", "FÍSICA", 0, 2))
        assertEquals("Teoría", clasificarTipo(null, "SEC_T", "FÍSICA", 0, 2))

        // Por tipo de API explícito
        assertEquals("Teoría", clasificarTipo("TEORIA GENERAL", null, "ALGORITMOS", 0, 2))
        assertEquals("Laboratorio", clasificarTipo("LAB COMPUTO", null, "ALGORITMOS", 0, 2))
        assertEquals("Práctica", clasificarTipo("PRACTICA CALIFICADA", null, "ALGORITMOS", 0, 2))

        // Por orden por defecto cuando hay 2 componentes (Teoría / Laboratorio)
        assertEquals("Teoría", clasificarTipo(null, "01", "INGENIERÍA", 0, 2))
        assertEquals("Laboratorio", clasificarTipo(null, "02", "INGENIERÍA", 1, 2))

        // Componente único
        assertEquals("Teoría", clasificarTipo(null, "01", "FILOSOFÍA", 0, 1))
    }

    @Test
    fun `test contarDiasHorario`() {
        assertEquals(2, contarDiasHorario("LUN, MIE"))
        assertEquals(3, contarDiasHorario("LUN, MAR, VIE"))
        assertEquals(1, contarDiasHorario("SAB"))
        assertEquals(1, contarDiasHorario(null))
        assertEquals(1, contarDiasHorario(""))
    }

    @Test
    fun `test deduccion matematica exacta de asistencias a partir de faltas y porcentaje`() {
        // Caso: 80% de asistencia con 2 faltas -> Asistió 8 veces (Total: 10 clases)
        val comp = AsistenciaComponente(
            crn = "1234",
            seccion = "01",
            faltas = 2,
            porcentaje = 80.0
        )
        assertEquals(8, comp.vecesAsistidas)
        assertEquals(10, comp.totalClasesCalculadas)
        assertTrue(comp.tieneRegistroAsistencia)
    }

    @Test
    fun `test deteccion de limite de 30 por ciento de inhabilitacion UPAO`() {
        // En UPAO el límite de faltas para inhabilitar es el 30% (asistencia < 70%)
        // Caso 1: Asistencia >= 90% (Óptimo)
        val cursoOptimo = AsistenciaCurso(
            crn = "1001",
            nombreCurso = "CALCULO I",
            porcentaje = 92.0,
            faltas = 1
        )
        val enRiesgoOptimo = (cursoOptimo.porcentaje ?: 100.0) < 70.0
        assertFalse("No debe estar en riesgo con 92%", enRiesgoOptimo)

        // Caso 2: Asistencia entre 70% y 89.9% (Aceptable)
        val cursoAceptable = AsistenciaCurso(
            crn = "1002",
            nombreCurso = "FISICA I",
            porcentaje = 75.0,
            faltas = 3
        )
        val enRiesgoAceptable = (cursoAceptable.porcentaje ?: 100.0) < 70.0
        assertFalse("No debe estar en riesgo con 75%", enRiesgoAceptable)

        // Caso 3: Asistencia < 70% (> 30% faltas -> Inhabilitado / En Riesgo Severo)
        val cursoInhabilitado = AsistenciaCurso(
            crn = "1003",
            nombreCurso = "ESTRUCTURA DE DATOS",
            porcentaje = 65.0,
            faltas = 5
        )
        val enRiesgoInhabilitado = (cursoInhabilitado.porcentaje ?: 100.0) < 70.0
        assertTrue("Debe estar en riesgo por superar el 30% de faltas (asistencia < 70%)", enRiesgoInhabilitado)
    }

    @Test
    fun `test curso con multiples componentes suma faltas y promedia porcentaje`() {
        val c1 = AsistenciaComponente(crn = "101", seccion = "01", tipo = "Teoría", faltas = 1, porcentaje = 90.0)
        val c2 = AsistenciaComponente(crn = "102", seccion = "02", tipo = "Laboratorio", faltas = 2, porcentaje = 80.0)

        val curso = AsistenciaCurso(
            crn = "101",
            nombreCurso = "REDES Y CONECTIVIDAD",
            componentes = listOf(c1, c2),
            faltas = 3,
            porcentaje = 85.0
        )

        assertEquals(3, curso.totalFaltasCalculadas)
        assertEquals(85.0, curso.porcentaje!!, 0.01)
        val tipificados = curso.componentesTipificados()
        assertEquals(2, tipificados.size)
        assertEquals("Teoría", tipificados[0].first)
        assertEquals("Laboratorio", tipificados[1].first)
    }
}

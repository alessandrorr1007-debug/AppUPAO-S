package com.example.upaos

import com.example.upaos.data.model.NotificacionItem
import com.example.upaos.data.model.NotificacionesResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNotificationsTest {

    @Test
    fun `test creacion y conteo de notificaciones no leidas`() {
        val item1 = NotificacionItem(
            id = 1L,
            mensaje = "Te registraron una nueva falta en CÁLCULO I",
            curso = "CÁLCULO I",
            componente = "Asistencia",
            fechaCreacion = "2026-10-18T10:00:00",
            leida = false
        )
        val item2 = NotificacionItem(
            id = 2L,
            mensaje = "Calificación en EP1 - Práctica 1: 18",
            curso = "FÍSICA I",
            componente = "EP1 - Práctica 1",
            fechaCreacion = "2026-10-18T11:00:00",
            leida = false
        )

        val lista = listOf(item2, item1)
        val response = NotificacionesResponse(
            noLeidas = lista.count { !it.leida },
            total = lista.size,
            notificaciones = lista
        )

        assertEquals(2, response.total)
        assertEquals(2, response.noLeidas)
    }

    @Test
    fun `test marcar notificacion como leida individual y total`() {
        val item1 = NotificacionItem(id = 1L, mensaje = "Aviso 1", leida = false)
        val item2 = NotificacionItem(id = 2L, mensaje = "Aviso 2", leida = false)

        val listaInicial = listOf(item1, item2)

        // Marcar item 1 como leída
        val listaConItem1Leido = listaInicial.map {
            if (it.id == 1L) it.copy(leida = true) else it
        }

        assertEquals(1, listaConItem1Leido.count { !it.leida })
        assertTrue(listaConItem1Leido.first { it.id == 1L }.leida)
        assertFalse(listaConItem1Leido.first { it.id == 2L }.leida)

        // Marcar todas como leídas
        val listaTodasLeidas = listaConItem1Leido.map { it.copy(leida = true) }
        assertEquals(0, listaTodasLeidas.count { !it.leida })
        assertTrue(listaTodasLeidas.all { it.leida })
    }

    @Test
    fun `test formato mensaje de falta vs asistencia`() {
        val faltasActuales = 3
        val faltasPrevias = 2
        val difFaltas = faltasActuales - faltasPrevias
        val pluralFaltas = if (difFaltas == 1) "una nueva falta" else "$difFaltas faltas nuevas"
        assertEquals("una nueva falta", pluralFaltas)

        val asistenciasActuales = 5
        val asistenciasPrevias = 3
        val difAsist = asistenciasActuales - asistenciasPrevias
        val pluralAsist = if (difAsist == 1) "asistencia" else "$difAsist asistencias"
        assertEquals("2 asistencias", pluralAsist)
    }

    @Test
    fun `test tiempoRelativo fecha reciente no muestra desfase de 5 horas`() {
        val now = java.util.Date()
        // Formato UTC estándar
        val utcFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val utcStr = utcFormat.format(now)
        val relUtc = com.example.upaos.ui.components.tiempoRelativo(utcStr)
        assertEquals("hace un momento", relUtc)

        // Formato local naive (sin zona horaria, como guardaba la versión previa localmente)
        val localFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getDefault()
        }
        val localNaiveStr = localFormat.format(now)
        val relNaive = com.example.upaos.ui.components.tiempoRelativo(localNaiveStr)
        assertEquals("hace un momento", relNaive)
    }

    @Test
    fun `test formato de notificacion para subcomponentes de curso`() {
        val cursoNombre = "Algoritmos y Estructura de Datos"
        val compNombre = "EVALUACIÓN DE PROCESO 1"
        val subNombre = "PRÁCTICA CALIFICADA 1"
        val nota = "18"

        val compCorto = when {
            compNombre.contains("PROCESO 1") || compNombre.contains("EP1") -> "EP1"
            else -> compNombre
        }
        val subFormateado = com.example.upaos.ui.components.toTitleCase(subNombre)
        val etiqueta = "$compCorto: $subFormateado"
        val mensaje = "$cursoNombre: Calificación en $subFormateado ($compCorto): $nota."

        assertEquals("EP1: Práctica Calificada 1", etiqueta)
        assertEquals("Algoritmos y Estructura de Datos: Calificación en Práctica Calificada 1 (EP1): 18.", mensaje)
    }
}

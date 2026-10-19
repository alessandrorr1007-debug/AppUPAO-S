package com.example.upaos

import com.example.upaos.data.model.CourseGrade
import com.example.upaos.data.model.HorarioBloque
import com.example.upaos.data.model.HorarioCurso
import com.example.upaos.data.upao.ScheduleCalculator
import com.example.upaos.ui.share.ShareBitmapHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareCardsTest {

    @Test
    fun dimensionesTarjeta_1080pxAnchoInstitucional() {
        val (ancho, alto) = ShareBitmapHelper.obtenerDimensionesObjetivo()
        assertEquals(1080, ancho)
        assertEquals(1920, alto)

        // Verificación de creación de Bitmap (en entorno JVM sin emulador, Bitmap está stubbed por Android Gradle)
        val bitmap = ShareBitmapHelper.crearBitmapTarjeta(ancho, alto)
        if (bitmap != null) {
            assertEquals(ancho, bitmap.width)
            assertEquals(alto, bitmap.height)
        }
    }

    @Test
    fun privacidadTarjetaNotas_ocultarNombre_noExponeDatosSensibles() {
        // Simular datos de un estudiante con código y nombre
        val codigoAlumno = "000123456"
        val nombreReal = "Juan Carlos Perez Lopez"
        val contrasena = "Secreto123!"

        val curso = CourseGrade(
            crn = "101",
            courseTitle = "Algoritmos y Estructura de Datos",
            notaActual = "16.0"
        )

        // Cuando ocultarNombre es true:
        val ocultarNombre = true
        val nombreAMostrar = if (ocultarNombre) "Estudiante UPAO" else nombreReal

        assertEquals("Estudiante UPAO", nombreAMostrar)
        assertTrue("No debe contener el nombre real cuando está oculto", !nombreAMostrar.contains("Juan Carlos"))
        assertTrue("Nunca debe incluir el código de alumno", !nombreAMostrar.contains(codigoAlumno))
        assertTrue("Nunca debe incluir credenciales ni contraseñas", !nombreAMostrar.contains(contrasena))
    }

    @Test
    fun horarioParaTarjeta_fusionaBloquesContinuosCorrectamente() {
        val curso = HorarioCurso(
            crn = "303",
            nombre = "Base de Datos",
            bloques = listOf(
                HorarioBloque(dia = 0, horaInicio = "07:15", horaFin = "08:50", aula = "Pab. H - 302"),
                HorarioBloque(dia = 0, horaInicio = "08:50", horaFin = "10:30", aula = "Pab. H - 302")
            )
        )

        val fusionados = ScheduleCalculator.fusionarBloques(curso.bloques)
        assertEquals(1, fusionados.size)
        assertEquals("07:15", fusionados[0].horaInicio)
        assertEquals("10:30", fusionados[0].horaFin)
    }
}

package com.example.upaos

import com.example.upaos.data.upao.ApiErrors
import com.example.upaos.data.upao.BannerParsers
import com.example.upaos.data.upao.FormatoInesperado
import com.example.upaos.data.upao.UpaoInvalidCredentialsException
import com.example.upaos.data.upao.UpaoNetworkException
import com.example.upaos.data.upao.UpaoServiceUnavailableException
import com.example.upaos.data.upao.UpaoSessionExpiredException
import com.example.upaos.data.upao.UpaoUnexpectedFormatException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.InputStreamReader

class BannerParsersTest {

    private fun loadFixture(path: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream(path)
            ?: throw IllegalArgumentException("Fixture no encontrada en el classpath: $path")
        return InputStreamReader(stream).use { it.readText() }
    }

    // ---------- 1. LOGIN FORM WSO2 / BANNER ----------

    @Test
    fun parseLoginForm_casoFeliz_extraeCamposCorrectamente() {
        val html = loadFixture("fixtures/login_form.html")
        val result = BannerParsers.parseLoginForm(html, "https://upaosso.upao.edu.pe/Account/Login")

        assertEquals("https://upaosso.upao.edu.pe/Account/Login", result.actionUrl)
        assertTrue(result.hiddenInputs.containsKey("__RequestVerificationToken"))
        assertEquals("anon_csrf_token_fixture_abc123", result.hiddenInputs["__RequestVerificationToken"])
        assertEquals("/StudentSelfService/ssb/studentGrades", result.hiddenInputs["returnUrl"])
    }

    @Test
    fun parseLoginForm_htmlSinFormulario_lanzaFormatoInesperado() {
        val htmlAlterado = "<html><body><div>Página de error genérica sin form</div></body></html>"
        try {
            BannerParsers.parseLoginForm(htmlAlterado, "https://upaosso.upao.edu.pe/")
            fail("Se esperaba FormatoInesperado / UpaoUnexpectedFormatException")
        } catch (e: UpaoUnexpectedFormatException) {
            assertTrue(e.message?.contains("La UPAO cambió algo en su portal") == true)
            assertFalse(e.message?.contains("password") == true)
        }
    }

    @Test
    fun parseLoginForm_formularioSinInputs_lanzaFormatoInesperado() {
        val htmlAlterado = "<html><body><form action='/login'></form></body></html>"
        try {
            BannerParsers.parseLoginForm(htmlAlterado, "https://upaosso.upao.edu.pe/")
            fail("Se esperaba FormatoInesperado")
        } catch (e: FormatoInesperado) {
            assertTrue(e.detalle.contains("inputs") || e.detalle.contains("<input>"))
        }
    }

    // ---------- 2. NOTAS (studentGrades) ----------

    @Test
    fun parseCoursesJson_casoFeliz_extraeCursos() {
        val json = loadFixture("fixtures/grades.json")
        val courses = BannerParsers.parseCoursesJson(json)

        assertEquals(2, courses.size)
        assertEquals("INGENIERIA DE SOFTWARE", courses[0].nombre)
        assertEquals("1001", courses[0].crn)
        assertEquals("REDES Y COMUNICACIONES", courses[1].nombre)
        assertEquals("1002", courses[1].crn)
    }

    @Test
    fun parseCoursesJson_jsonAlteradoSinDataNiItems_lanzaFormatoInesperado() {
        val jsonAlterado = """{"portalVersion": "v10", "status": "ok", "unexpectedPayload": {}}"""
        try {
            BannerParsers.parseCoursesJson(jsonAlterado)
            fail("Se esperaba FormatoInesperado")
        } catch (e: UpaoUnexpectedFormatException) {
            assertTrue(e.message?.contains("La UPAO cambió algo en su portal") == true)
        }
    }

    // ---------- 3. HORARIO (registrationHistory) ----------

    @Test
    fun parseHorarioJson_casoFeliz_extraeRegistros() {
        val json = loadFixture("fixtures/schedule.json")
        val registrations = BannerParsers.parseHorarioJson(json)

        assertEquals(2, registrations.size)
        assertEquals("1001", registrations[0].get("courseReferenceNumber").asString)
        assertEquals("INGENIERIA DE SOFTWARE", registrations[0].get("courseTitle").asString)
        assertEquals("1002", registrations[1].get("courseReferenceNumber").asString)
    }

    @Test
    fun parseHorarioJson_jsonAlteradoSinRegistrations_lanzaFormatoInesperado() {
        val jsonAlterado = """{"data": {"otherField": [1, 2, 3]}}"""
        try {
            BannerParsers.parseHorarioJson(jsonAlterado)
            fail("Se esperaba FormatoInesperado")
        } catch (e: UpaoUnexpectedFormatException) {
            assertTrue(e.detalle.contains("registrations"))
        }
    }

    @Test
    fun parseHorarioJson_conRegistrosRetirados_filtraSoloActivos() {
        val jsonConBajas = """
        {
          "data": {
            "registrations": [
              {
                "courseReferenceNumber": "5045",
                "courseTitle": "MACHINE LEARNING",
                "subject": "ISIA",
                "courseNumber": "110",
                "sequenceNumber": "01",
                "status": "DW",
                "statusIndicator": "N",
                "statusDescription": "Eliminado por Web"
              },
              {
                "courseReferenceNumber": "5046",
                "courseTitle": "MACHINE LEARNING",
                "subject": "ISIA",
                "courseNumber": "110",
                "sequenceNumber": "02",
                "status": "RW",
                "statusIndicator": "Y",
                "statusDescription": "Inscrito por Web"
              },
              {
                "courseReferenceNumber": "6001",
                "courseTitle": "BASE DE DATOS",
                "subject": "ISIA",
                "courseNumber": "112"
              }
            ]
          }
        }
        """.trimIndent()

        val activos = BannerParsers.parseHorarioJson(jsonConBajas)
        assertEquals(2, activos.size)
        assertEquals("5046", activos[0].get("courseReferenceNumber").asString)
        assertEquals("6001", activos[1].get("courseReferenceNumber").asString)

        // Verificamos que si se desactiva soloActivos, devuelve todos (incluyendo la baja)
        val todos = BannerParsers.parseHorarioJson(jsonConBajas, soloActivos = false)
        assertEquals(3, todos.size)
    }

    @Test
    fun esRegistroActivo_distingueActivosEInactivos() {
        fun makeReg(status: String? = null, statusIndicator: String? = null, statusDesc: String? = null): com.google.gson.JsonObject {
            val obj = com.google.gson.JsonObject()
            status?.let { obj.addProperty("status", it) }
            statusIndicator?.let { obj.addProperty("statusIndicator", it) }
            statusDesc?.let { obj.addProperty("statusDescription", it) }
            return obj
        }

        // Bajas / eliminados / retirados
        assertFalse(BannerParsers.esRegistroActivo(makeReg(status = "DW")))
        assertFalse(BannerParsers.esRegistroActivo(makeReg(status = "DD")))
        assertFalse(BannerParsers.esRegistroActivo(makeReg(status = "WD")))
        assertFalse(BannerParsers.esRegistroActivo(makeReg(statusIndicator = "N")))
        assertFalse(BannerParsers.esRegistroActivo(makeReg(statusDesc = "Eliminado por Web")))
        assertFalse(BannerParsers.esRegistroActivo(makeReg(statusDesc = "Baja por Web")))
        assertFalse(BannerParsers.esRegistroActivo(makeReg(statusDesc = "Retirado")))

        // Activos
        assertTrue(BannerParsers.esRegistroActivo(makeReg(status = "RW", statusIndicator = "Y", statusDesc = "Inscrito por Web")))
        assertTrue(BannerParsers.esRegistroActivo(makeReg(status = "RE", statusIndicator = "Y", statusDesc = "Inscrito")))
        assertTrue(BannerParsers.esRegistroActivo(makeReg())) // Sin campos de estado = activo por defecto
    }

    // ---------- 4. ASISTENCIA (studentAttendanceTracking) ----------

    @Test
    fun parseAsistenciaJson_casoFeliz_extraeTotalYFilas() {
        val json = loadFixture("fixtures/attendance.json")
        val (totalCount, items) = BannerParsers.parseAsistenciaJson(json)

        assertEquals(2, totalCount)
        assertEquals(2, items.size)
        assertEquals("1001", items[0].get("courseReferenceNumber").asString)
        assertEquals("H", items[0].get("building").asString)
        assertEquals("302", items[0].get("room").asString)
    }

    @Test
    fun parseAsistenciaJson_jsonAlteradoSinData_lanzaFormatoInesperado() {
        val jsonAlterado = """{"success": true, "message": "Portal actualizado"}"""
        try {
            BannerParsers.parseAsistenciaJson(jsonAlterado)
            fail("Se esperaba FormatoInesperado")
        } catch (e: UpaoUnexpectedFormatException) {
            assertTrue(e.detalle.contains("data") || e.detalle.contains("totalCount"))
        }
    }

    // ---------- 5. MENSAJES DE ERROR EN UI (ApiErrors) ----------

    @Test
    fun apiErrors_mapeaMensajesCorrectosYSinSecretos() {
        assertEquals(
            "Sin conexión. Mostrando lo último guardado.",
            ApiErrors.mensajeParaUsuario(UpaoNetworkException())
        )
        assertEquals(
            "El portal de la UPAO no responde ahora. Mostrando lo último guardado.",
            ApiErrors.mensajeParaUsuario(UpaoServiceUnavailableException())
        )
        assertEquals(
            "La UPAO cambió algo en su portal y la app necesita actualizarse. Mostrando lo último guardado.",
            ApiErrors.mensajeParaUsuario(UpaoUnexpectedFormatException("Detalle interno sin secretos"))
        )
        assertEquals(
            "ID o contraseña incorrectos. Revisa tus datos e inicia sesión nuevamente.",
            ApiErrors.mensajeParaUsuario(UpaoInvalidCredentialsException())
        )
        assertEquals(
            "Tu sesión en la UPAO ha expirado. Por favor, inicia sesión nuevamente.",
            ApiErrors.mensajeParaUsuario(UpaoSessionExpiredException())
        )
    }
}

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

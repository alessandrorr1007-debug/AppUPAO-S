package com.example.upaos

import com.example.upaos.data.upao.*
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class UpaoAuthTest {

    @Test
    fun extractStudentNameFromUserNameClass() {
        val html = """
            <html>
                <body>
                    <div class="user-name">ALESSANDRO RODRIGUEZ</div>
                </body>
            </html>
        """.trimIndent()
        assertEquals("ALESSANDRO RODRIGUEZ", UpaoAuth.extractStudentName(html))
    }

    @Test
    fun extractStudentNameFromUserNameId() {
        val html = """
            <html>
                <body>
                    <span id="user-name">ALESSANDRO RODRIGUEZ</span>
                </body>
            </html>
        """.trimIndent()
        assertEquals("ALESSANDRO RODRIGUEZ", UpaoAuth.extractStudentName(html))
    }

    @Test
    fun extractStudentNameFromProfileName() {
        val html = """
            <html>
                <body>
                    <div class="profile-name">RODRIGUEZ RUIZ, ALESSANDRO</div>
                </body>
            </html>
        """.trimIndent()
        assertEquals("RODRIGUEZ RUIZ, ALESSANDRO", UpaoAuth.extractStudentName(html))
    }

    @Test
    fun extractStudentNameFromFullNameJsonPattern() {
        val html = """
            <script>
                var userData = {
                    "userId": "000123456",
                    "fullName": "ALESSANDRO RODRIGUEZ RUIZ",
                    "term": "202610"
                };
            </script>
        """.trimIndent()
        assertEquals("ALESSANDRO RODRIGUEZ RUIZ", UpaoAuth.extractStudentName(html))
    }

    @Test
    fun extractStudentNameFromDataAttribute() {
        val html = """
            <div class="user-menu" data-user-name="RODRIGUEZ RUIZ/ALESSANDRO"></div>
        """.trimIndent()
        assertEquals("RODRIGUEZ RUIZ/ALESSANDRO", UpaoAuth.extractStudentName(html))
    }

    @Test
    fun extractStudentNameFromDisplayNameJson() {
        val html = """
            <script>
                window.initialState = { "displayName": "ALESSANDRO DANIEL" };
            </script>
        """.trimIndent()
        assertEquals("ALESSANDRO DANIEL", UpaoAuth.extractStudentName(html))
    }

    @Test
    fun parseLoginFormInputsCorrectly() {
        val html = """
            <html>
                <body>
                    <form action="/Account/Login?ReturnUrl=%2fStudentSelfService" method="post">
                        <input type="hidden" name="__RequestVerificationToken" value="TOKEN_XYZ_123" />
                        <input type="hidden" name="ReturnUrl" value="/StudentSelfService" />
                        <input type="text" name="id_usuario" value="" />
                        <input type="password" name="nip" value="" />
                    </form>
                </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://upaosso.upao.edu.pe:410/Account/Login")
        val form = doc.selectFirst("form")
        assertNotNull(form)

        val tokenInput = form!!.selectFirst("input[name=__RequestVerificationToken]")
        assertNotNull(tokenInput)
        assertEquals("TOKEN_XYZ_123", tokenInput?.attr("value"))

        val absAction = form.absUrl("action")
        assertEquals("https://upaosso.upao.edu.pe:410/Account/Login?ReturnUrl=%2fStudentSelfService", absAction)
    }

    @Test
    fun cookieJarAccountIsolation() {
        val jarA = UpaoCookieJar("000111111")
        val jarB = UpaoCookieJar("000222222")
        val url = "https://ssb.upao.edu.pe/StudentSelfService".toHttpUrl()

        // Cuenta A

        val cookieA = Cookie.Builder()
            .domain("ssb.upao.edu.pe")
            .path("/")
            .name("JSESSIONID")
            .value("SESSION_A")
            .build()
        jarA.saveFromResponse(url, listOf(cookieA))

        // Verificamos en Cuenta A
        val loadedA = jarA.loadForRequest(url)
        assertEquals(1, loadedA.size)
        assertEquals("SESSION_A", loadedA[0].value)

        // Cuenta B

        val loadedB = jarB.loadForRequest(url)
        assertTrue(loadedB.isEmpty())

        val cookieB = Cookie.Builder()
            .domain("ssb.upao.edu.pe")
            .path("/")
            .name("JSESSIONID")
            .value("SESSION_B")
            .build()
        jarB.saveFromResponse(url, listOf(cookieB))

        // Verificamos en Cuenta B
        assertEquals("SESSION_B", jarB.loadForRequest(url)[0].value)

        // Volvemos a Cuenta A

        assertEquals("SESSION_A", jarA.loadForRequest(url)[0].value)

        // Limpiar cuenta A
        jarA.clear()
        assertTrue(jarA.loadForRequest(url).isEmpty())

        // Cuenta B sigue intacta

        assertEquals("SESSION_B", jarB.loadForRequest(url)[0].value)
    }

    @Test
    fun testUpaoExceptionsHierarchy() {
        val invalid: Exception = UpaoInvalidCredentialsException()
        assertTrue(invalid is UpaoException)
        assertEquals("ID o contraseña incorrectos. Revisa tus datos.", invalid.message)

        val outage: Exception = UpaoServiceUnavailableException()
        assertTrue(outage is UpaoException)
        assertTrue(outage.message!!.contains("no está disponible"))

        val expired: Exception = UpaoSessionExpiredException()
        assertTrue(expired is UpaoException)
        assertTrue(expired.message!!.contains("ha expirado"))
    }
}

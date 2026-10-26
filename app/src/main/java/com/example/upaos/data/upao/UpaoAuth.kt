package com.example.upaos.data.upao

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URLDecoder
import java.util.regex.Pattern

data class UpaoLoginResult(
    val success: Boolean,
    val studentName: String? = null,
    val errorMessage: String? = null
)

object UpaoAuth {

    private const val SSB_BASE_URL = "https://ssb.upao.edu.pe/StudentSelfService/ssb/studentGrades"
    private const val SESSION_PING_URL = "https://ssb.upao.edu.pe/StudentSelfService/ssb/studentGrades/term?filter=&page=1&max=5"
    private const val INSCRIPCION_LOGIN_URL = "https://inscripcion.upao.edu.pe/StudentRegistrationSsb/login"


    internal fun buildLoginForm(user: String, password: String, hiddenInputs: Map<String, String>): FormBody {
        val builder = FormBody.Builder()
        hiddenInputs.forEach { (name, value) ->
            if (name.lowercase() !in setOf("username", "password", "id_usuario", "nip", "btn_valida")) builder.add(name, value)
        }
        return builder.add("id_usuario", user).add("nip", password).add("btn_valida", "Iniciar sesión").build()
    }

    /**
     * Extrae el nombre del estudiante a partir del HTML de studentGrades.
     */
    fun extractStudentName(html: String): String? {
        if (html.isBlank()) return null
        try {
            val doc = Jsoup.parse(html)
            val selectors = listOf(
                ".user-name", "#user-name", ".profile-name", ".student-name", "span.name",
                "#userName", ".userName", ".username", ".user-display-name", "#user-display-name",
                ".user-info", "#user-info", ".user-welcome", ".identity"
            )
            for (sel in selectors) {
                val element = doc.selectFirst(sel)
                val text = element?.text()?.trim()
                if (!text.isNullOrBlank() && !text.equals("null", ignoreCase = true)) {
                    return text
                }
            }
            val attrElement = doc.selectFirst("[data-user-name], [data-fullname], [data-name]")
            val attrText = attrElement?.let {
                it.attr("data-user-name").ifBlank { it.attr("data-fullname") }.ifBlank { it.attr("data-name") }
            }?.trim()
            if (!attrText.isNullOrBlank()) return attrText

            val patterns = listOf(
                Pattern.compile("[\"'](?:fullName|displayName|studentName|nombreCompleto|userName)[\"']\\s*:\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE),
                Pattern.compile("[\"']name[\"']\\s*:\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
            )
            for (pattern in patterns) {
                val matcher = pattern.matcher(html)
                if (matcher.find()) {
                    val name = matcher.group(1)?.trim()
                    if (!name.isNullOrBlank() && !name.equals("null", ignoreCase = true)) {
                        return name
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Inicia sesión SSO en UPAO (WSO2 Banner).
     * 1. GET a studentGrades (redirige a upaosso.upao.edu.pe:410).
     * 2. Extrae inputs ocultos (__RequestVerificationToken).
     * 3. POST formulario con id_usuario, nip, btn_valida.
     * 4. Valida redirección final a ssb.upao.edu.pe.
     */
    suspend fun login(usuario: String, pass: String): UpaoLoginResult {
        val session = UpaoHttpClient.selectAccount(usuario)
        return session.loginMutex.withLock { loginInSession(usuario, pass, session) }
    }

    private suspend fun loginInSession(usuario: String, pass: String, session: UpaoHttpClient.AccountSession): UpaoLoginResult = withContext(Dispatchers.IO) {
        val user = usuario.trim()
        val password = pass

        if (user.length != 9 || !user.all { it.isDigit() } || password.isEmpty()) {
            throw UpaoInvalidCredentialsException("El ID debe tener 9 dígitos y la contraseña no puede estar vacía.")
        }

        session.requireValid()
        session.authenticatedUser = null
        session.cookieJar.clear()
        val client = session.client

        try {
            // Paso 1: GET inicial a studentGrades
            val initRequest = Request.Builder()
                .url(SSB_BASE_URL)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .get()
                .build()

            val (initResponse, initHtml) = consumeHttpResponse(client, initRequest) { it to it.body?.string().orEmpty() }
            val initFinalUrl = initResponse.request.url.toString()

            if (!initResponse.isSuccessful && initResponse.code != 302) {
                throw UpaoServiceUnavailableException("El portal de la UPAO no está disponible (HTTP ${initResponse.code}).")
            }

            // Paso 2: Parsear formulario de login en la página obtenida
            val loginFormInfo = BannerParsers.parseLoginForm(initHtml, initFinalUrl)
            val resolvedActionUrl = loginFormInfo.actionUrl

            val formHttpUrl = resolvedActionUrl.toHttpUrlOrNull()
            val host = formHttpUrl?.host.orEmpty()
            if (formHttpUrl == null || !com.example.upaos.data.api.TrustedServers.isLogin(formHttpUrl)) {
                throw UpaoServiceUnavailableException("Redirección no autorizada a servidor desconocido ($host).")
            }

            // Capturar todos los inputs presentes (incluye __RequestVerificationToken)
            val formBody = buildLoginForm(user, password, loginFormInfo.hiddenInputs)

            // Paso 3: POST al endpoint de autenticación
            val postRequest = Request.Builder()
                .url(resolvedActionUrl)
                .header("Referer", initFinalUrl)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .post(formBody)
                .build()

            val (postResponse, postHtml) = consumeHttpResponse(client, postRequest) { it to it.body?.string().orEmpty() }
            if (postResponse.code == 401) {
                throw UpaoInvalidCredentialsException()
            }

            val postFinalHttpUrl = postResponse.request.url
            val postFinalUrl = postFinalHttpUrl.toString()

            // Paso 4: Evaluar respuesta y URL final
            val isErrorParam = postFinalHttpUrl.queryParameter("is_error")
                ?: postFinalHttpUrl.queryParameter("isError")
            val mensajeErrorParam = postFinalHttpUrl.queryParameter("mensaje_error")
                ?: postFinalHttpUrl.queryParameter("mensajeError")
                ?: postFinalHttpUrl.queryParameter("error")

            val isError = isErrorParam?.equals("true", ignoreCase = true) == true
            if (isError || !mensajeErrorParam.isNullOrBlank()) {
                val decoded = try {
                    URLDecoder.decode(mensajeErrorParam ?: "", "UTF-8")
                } catch (_: Exception) {
                    mensajeErrorParam ?: ""
                }.lowercase()

                val isMaintenance = listOf("mantenimiento", "no disponible", "servidor", "conex", "temporal")
                    .any { decoded.contains(it) }

                if (isMaintenance) {
                    throw UpaoServiceUnavailableException()
                } else {
                    throw UpaoInvalidCredentialsException()
                }
            }

            // Verificación de éxito: aterrizó en Banner SSB
            if (postFinalHttpUrl.host == "ssb.upao.edu.pe" && postFinalHttpUrl.encodedPath.startsWith("/StudentSelfService/ssb/")) {
                session.requireValid()
                session.authenticatedUser = user
                val studentName = extractStudentName(postHtml)
                return@withContext UpaoLoginResult(
                    success = true,
                    studentName = studentName
                )
            }

            // Si permanece en upaosso o login, credenciales incorrectas
            if (postFinalHttpUrl.host.contains("upaosso") || postFinalHttpUrl.encodedPath.contains("login", ignoreCase = true)) {
                throw UpaoInvalidCredentialsException()
            }

            throw UpaoServiceUnavailableException()

        } catch (e: UpaoException) {
            throw e
        } catch (e: IOException) {
            throw UpaoNetworkException("Error de red al conectar con la UPAO: ${e.localizedMessage ?: "timeout"}", e)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw UpaoServiceUnavailableException("Error inesperado durante la autenticación: ${e.localizedMessage}")
        }
    }

    /**
     * Verifica si la sesión actual en Banner SSB está viva con una petición ligera.
     */
    suspend fun checkSessionAlive(usuario: String): Boolean = withContext(Dispatchers.IO) {
        val session = UpaoHttpClient.forAccount(usuario)
        if (session.authenticatedUser != usuario.trim()) return@withContext false
        val client = session.client
        try {
            val req = Request.Builder()
                .url(SESSION_PING_URL)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", SSB_BASE_URL)
                .get()
                .build()

            consumeHttpResponse(client, req) { res ->
                val contentType = res.header("Content-Type").orEmpty().lowercase()
                val alive = res.isSuccessful && contentType.contains("json") && res.request.url.host == "ssb.upao.edu.pe"
                session.requireValid()
                alive
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: IOException) {
            android.util.Log.w("UpaoAuth", "No se pudo comprobar la sesión")
            false
        }
    }

    /**
     * Garantiza que haya una sesión activa. Si la sesión expiró, realiza re-login protegido con Mutex.
     */
    suspend fun ensureSession(usuario: String, pass: String): UpaoLoginResult {
        val session = UpaoHttpClient.forAccount(usuario)
        if (checkSessionAlive(usuario)) {
            return UpaoLoginResult(success = true)
        }

        return session.loginMutex.withLock {
            if (checkSessionAlive(usuario)) {
                return@withLock UpaoLoginResult(success = true)
            }
            loginInSession(usuario, pass, session)
        }
    }

    /**
     * Prepara la sesión en inscripcion.upao.edu.pe para consultas de horario y aulas.
     * Si no tiene cookie de inscripción, realiza 1 GET previo al endpoint de login.
     */
    suspend fun ensureInscriptionSession(usuario: String) = withContext(Dispatchers.IO) {
        val session = UpaoHttpClient.forAccount(usuario)
        val client = session.client
        if (!session.cookieJar.hasCookieForDomain("inscripcion.upao.edu.pe")) {
            try {
                val req = Request.Builder()
                    .url(INSCRIPCION_LOGIN_URL)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .get()
                    .build()
                consumeHttpResponse(client, req) { response ->
                    if (!response.isSuccessful) throw UpaoServiceUnavailableException("No se pudo preparar la sesión de inscripción")
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: IOException) { throw UpaoNetworkException(cause = e) }
        }
    }
}

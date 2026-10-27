package com.example.upaos.data.profile

import com.example.upaos.data.upao.BannerParsers
import com.example.upaos.data.upao.UpaoAuth
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import java.io.IOException

/** Follow the published Experience -> UPAO SSO -> SAML callback, without clearing Banner cookies. */
internal object ExperienceAuth {
    private const val EXPERIENCE = "experience.elluciancloud.com"
    private const val SAML = "eee-api.10005.elluciancloud.com"
    private const val SSO = "upaosso.upao.edu.pe"
    private val samlFields = setOf("SAMLResponse", "SAMLRequest", "RelayState", "SigAlg", "Signature")

    internal fun allowed(url: HttpUrl): Boolean {
        if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty()) return false
        return when (url.host) {
            EXPERIENCE -> url.port == 443 && (url.encodedPath == "/upao" || url.encodedPath.startsWith("/upao/"))
            SAML -> url.port == 443 && url.encodedPath.startsWith("/saml-prod/")
            "login.upao.edu.pe" -> url.port == 443
            SSO -> url.port in setOf(443, 410)
            else -> false
        }
    }

    suspend fun authenticate(
        client: OkHttpClient,
        user: String,
        password: () -> String?,
        isCurrent: () -> Boolean
    ): Boolean {
        var request = Request.Builder().url("https://$EXPERIENCE/upao/auth")
            .header("Accept", "text/html,application/xhtml+xml").get().build()
        var credentialsSubmitted = false
        repeat(16) {
            currentCoroutineContext().ensureActive()
            if (!isCurrent() || !allowed(request.url)) return false
            val credentialBody = request.body as? FormBody
            if (credentialBody != null && (0 until credentialBody.size).any { credentialBody.name(it) == "nip" } && request.url.host != SSO) return false
            val next = client.newCall(request).execute().use { response ->
                if (response.code == 429 || response.code >= 500) throw IOException("Experience no está disponible temporalmente")
                if (response.code in 300..399) {
                    val target = response.header("Location")?.let(request.url::resolve) ?: return false
                    if (!allowed(target)) return false
                    if (response.code == 307 || response.code == 308) {
                        if (request.method == "POST" && (target.host != request.url.host || target.port != request.url.port)) return false
                        return@use request.newBuilder().url(target).build()
                    }
                    return@use Request.Builder().url(target).header("Accept", "text/html,application/xhtml+xml").get().build()
                }
                if (!response.isSuccessful) return false
                if (request.url.host == EXPERIENCE && request.method == "GET" &&
                    request.url.encodedPath in setOf("/upao", "/upao/")) return true
                val html = response.body?.string() ?: return false
                val doc = Jsoup.parse(html, request.url.toString())
                val login = doc.select("form").firstOrNull { it.selectFirst("input[name=id_usuario]") != null && it.selectFirst("input[name=nip]") != null }
                if (login != null) {
                    if (request.url.host != SSO || credentialsSubmitted) return false
                    val form = BannerParsers.parseLoginForm(login.outerHtml(), request.url.toString())
                    val target = request.url.resolve(form.actionUrl) ?: return false
                    if (!allowed(target) || target.host != SSO) return false
                    val pass = password()?.takeIf { it.isNotBlank() } ?: return false
                    if (!isCurrent()) return false
                    credentialsSubmitted = true
                    return@use Request.Builder().url(target)
                        .header("Referer", request.url.toString())
                        .header("Accept", "text/html,application/xhtml+xml")
                        .post(UpaoAuth.buildLoginForm(user, pass, form.hiddenInputs)).build()
                }
                // Ellucian's SAML token endpoint returns an auto-submitted JWT form, not a redirect.
                val jwtForm = doc.select("form").firstOrNull { it.selectFirst("input[name=jwt]") != null }
                if (jwtForm != null) {
                    if (request.url.host != SAML || request.url.encodedPath != "/saml-prod/token" ||
                        !jwtForm.attr("method").equals("post", ignoreCase = true)) return false
                    val target = request.url.resolve(jwtForm.attr("action")) ?: return false
                    if (!allowed(target) || target.host != EXPERIENCE || target.encodedPath != "/upao/auth/callback") return false
                    val jwt = jwtForm.selectFirst("input[name=jwt]")?.attr("value")?.takeIf { it.isNotBlank() } ?: return false
                    val origin = request.url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString().removeSuffix("/")
                    return@use Request.Builder().url(target).header("Referer", "$origin/").header("Origin", origin)
                        .header("Accept", "text/html,application/xhtml+xml")
                        .post(FormBody.Builder().add("jwt", jwt).build()).build()
                }
                val form = doc.select("form").firstOrNull {
                    it.selectFirst("input[name=SAMLResponse], input[name=SAMLRequest]") != null
                } ?: return false
                if (!form.attr("method").equals("post", ignoreCase = true)) return false
                val target = request.url.resolve(form.attr("action")) ?: return false
                if (!allowed(target)) return false
                val body = FormBody.Builder()
                form.select("input[name]").forEach { input ->
                    val name = input.attr("name")
                    if (name in samlFields) body.add(name, input.attr("value"))
                }
                val origin = request.url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString().removeSuffix("/")
                Request.Builder().url(target).header("Referer", "$origin/").header("Origin", origin)
                    .header("Accept", "text/html,application/xhtml+xml").post(body.build()).build()
            }
            request = next
        }
        return false
    }
}

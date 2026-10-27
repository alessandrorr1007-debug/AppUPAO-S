package com.example.upaos

import com.example.upaos.data.profile.ExperienceAuth
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ExperienceAuthTest {
    private val loginHtml = """<form method="post" action="/">
        <input name="id_usuario"><input type="password" name="nip">
        <input type="hidden" name="state" value="synthetic-state">
        <input type="hidden" name="__RequestVerificationToken" value="synthetic-csrf"></form>"""
    private fun response(request: Request, code: Int = 200, location: String? = null, html: String = ""): Response =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
            .apply { if (location != null) header("Location", location) }
            .body(html.toResponseBody("text/html".toMediaType())).build()

    @Test fun completesSsoAndSamlWithExistingCredentialsOnlyAtUpaoSso() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().followRedirects(false).addInterceptor { chain ->
            val r = chain.request(); requests += r
            when {
                r.url.host == "experience.elluciancloud.com" && r.url.encodedPath == "/upao/auth" -> response(r, 302, "https://upaosso.upao.edu.pe:410/Account/Login")
                r.url.host == "upaosso.upao.edu.pe" && r.method == "GET" -> response(r, html = loginHtml)
                r.url.host == "upaosso.upao.edu.pe" && r.method == "POST" -> response(r, 302, "https://login.upao.edu.pe/commonauth?code=synthetic")
                r.url.host == "login.upao.edu.pe" -> response(r, html = """<form method="post" action="https://eee-api.10005.elluciancloud.com/saml-prod/token">
                    <input type="hidden" name="SAMLResponse" value="synthetic-assertion">
                    <input type="hidden" name="RelayState" value="synthetic-state">
                    <input type="hidden" name="nip" value="must-not-forward"></form>""")
                r.url.host == "eee-api.10005.elluciancloud.com" -> response(r, html = """<form method="post" action="https://experience.elluciancloud.com/upao/auth/callback">
                    <input type="hidden" name="jwt" value="synthetic-jwt">
                    <input type="hidden" name="nip" value="must-not-forward"></form>""")
                r.url.encodedPath == "/upao/auth/callback" -> response(r, 302, "/upao/")
                else -> response(r, html = "<html>fixture landing</html>")
            }
        }.build()
        assertTrue(ExperienceAuth.authenticate(client, "000000001", { "synthetic-password" }, { true }))
        val login = requests.single { it.method == "POST" && it.url.host == "upaosso.upao.edu.pe" }.body as FormBody
        assertEquals("000000001", login.value((0 until login.size).first { login.name(it) == "id_usuario" }))
        assertEquals("synthetic-password", login.value((0 until login.size).first { login.name(it) == "nip" }))
        assertTrue((0 until login.size).any { login.name(it) == "__RequestVerificationToken" })
        val assertion = requests.single { it.method == "POST" && it.url.host == "eee-api.10005.elluciancloud.com" }.body as FormBody
        assertTrue((0 until assertion.size).any { assertion.name(it) == "SAMLResponse" })
        assertFalse((0 until assertion.size).any { assertion.name(it) == "nip" || assertion.name(it) == "id_usuario" })
        val callback = requests.single { it.method == "POST" && it.url.host == "experience.elluciancloud.com" }.body as FormBody
        assertEquals(1, callback.size)
        assertEquals("jwt", callback.name(0))
        assertEquals("synthetic-jwt", callback.value(0))
    }
    @Test fun jwtFormCannotForwardTokenToAnotherOrigin() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().followRedirects(false).addInterceptor { chain ->
            val r = chain.request(); requests += r
            if (r.url.host == "experience.elluciancloud.com") response(r, 302, "https://eee-api.10005.elluciancloud.com/saml-prod/token")
            else response(r, html = """<form method="post" action="https://untrusted.invalid/callback"><input name="jwt" value="synthetic"></form>""")
        }.build()
        assertFalse(ExperienceAuth.authenticate(client, "000000001", { "synthetic" }, { true }))
        assertTrue(requests.all { it.method == "GET" })
    }
    @Test fun reusesSsoSessionWithoutRequestingPassword() = runBlocking {
        var passwordReads = 0
        val client = OkHttpClient.Builder().followRedirects(false).addInterceptor { chain ->
            val r = chain.request()
            if (r.url.encodedPath == "/upao/auth") response(r, 302, "/upao/") else response(r)
        }.build()
        assertTrue(ExperienceAuth.authenticate(client, "000000001", { passwordReads++; null }, { true }))
        assertEquals(0, passwordReads)
    }
    @Test fun doesNotSendCredentialsToUntrustedFormDestination() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().followRedirects(false).addInterceptor { chain ->
            val r = chain.request(); requests += r
            if (r.url.host == "experience.elluciancloud.com") response(r, 302, "https://upaosso.upao.edu.pe:410/Account/Login")
            else response(r, html = loginHtml.replace("action=\"/\"", "action=\"https://untrusted.invalid/\""))
        }.build()
        assertFalse(ExperienceAuth.authenticate(client, "000000001", { "synthetic" }, { true }))
        assertTrue(requests.all { it.method == "GET" })
    }
    @Test fun stopsAfterOneRejectedCredentialSubmission() = runBlocking {
        var posts = 0
        val client = OkHttpClient.Builder().followRedirects(false).addInterceptor { chain ->
            val r = chain.request()
            if (r.url.host == "experience.elluciancloud.com") response(r, 302, "https://upaosso.upao.edu.pe:410/Account/Login")
            else { if (r.method == "POST") posts++; response(r, html = loginHtml) }
        }.build()
        assertFalse(ExperienceAuth.authenticate(client, "000000001", { "synthetic" }, { true }))
        assertEquals(1, posts)
    }
    @Test fun rejectsOtherOriginsAndPorts() {
        assertTrue(ExperienceAuth.allowed("https://upaosso.upao.edu.pe:410/Account/Login".toHttpUrl()))
        assertTrue(ExperienceAuth.allowed("https://login.upao.edu.pe/commonauth".toHttpUrl()))
        assertFalse(ExperienceAuth.allowed("http://upaosso.upao.edu.pe/".toHttpUrl()))
        assertFalse(ExperienceAuth.allowed("https://upaosso.upao.edu.pe.untrusted.invalid/".toHttpUrl()))
        assertFalse(ExperienceAuth.allowed("https://upaosso.upao.edu.pe:8443/".toHttpUrl()))
        assertFalse(ExperienceAuth.allowed("https://user:password@upaosso.upao.edu.pe/".toHttpUrl()))
        assertFalse(ExperienceAuth.allowed("https://experience.elluciancloud.com/other-tenant/auth".toHttpUrl()))
    }
    @Test fun rejectsStaleSessionBeforeNetworkAccess() = runBlocking {
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain -> requests++; response(chain.request()) }.build()
        assertFalse(ExperienceAuth.authenticate(client, "000000001", { "synthetic" }, { false }))
        assertEquals(0, requests)
    }
    @Test(expected = IOException::class) fun serviceFailureDoesNotTriggerCredentialRetries() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain -> response(chain.request(), 503) }.build()
        ExperienceAuth.authenticate(client, "000000001", { "synthetic" }, { true })
        Unit
    }
}

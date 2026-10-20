package com.example.upaos

import com.example.upaos.data.upao.UpaoAuth
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

class LoginPasswordTest {
    @Test fun passwordIncludingLeadingTrailingSpacesAndSymbolsIsSentUnchanged() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("fixture"))
            val password = "  synthetic + & ñ password  "
            val form = UpaoAuth.buildLoginForm("000111111", password, mapOf("nip" to "wrong", "__RequestVerificationToken" to "fixture"))
            OkHttpClient().newCall(Request.Builder().url(server.url("/login")).post(form).build()).execute().use {
                assertTrue(it.isSuccessful)
            }
            val body = server.takeRequest(5, TimeUnit.SECONDS)!!.body.readUtf8().split("&")
                .map { it.split("=", limit = 2) }.associate { URLDecoder.decode(it[0], "UTF-8") to URLDecoder.decode(it[1], "UTF-8") }
            assertEquals(password, body["nip"])
            assertEquals("fixture", body["__RequestVerificationToken"])
            assertEquals(1, (0 until form.size).count { form.name(it) == "nip" })
        }
    }
}

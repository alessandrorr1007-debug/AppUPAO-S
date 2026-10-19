package com.example.upaos

import com.example.upaos.data.api.TrustedServers
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class TrustedServersTest {
    @Test fun canvasClientBlocksTokenBeforeConnectingToForeignServer() {
        val request = okhttp3.Request.Builder().url("https://example.invalid/api/v1/files/1")
            .header("Authorization", "Bearer test-token").build()
        try {
            com.example.upaos.data.api.CanvasApiClient.okHttpClient.newCall(request).execute().close()
            fail("La petición debe bloquearse")
        } catch (e: java.io.IOException) {
            assertEquals("Destino de Canvas no autorizado", e.message)
        }
    }
    @Test fun loginAllowsOfficialSsoPort() {
        assertTrue(TrustedServers.isLogin("https://upaosso.upao.edu.pe:410/login".toHttpUrl()))
        assertTrue(TrustedServers.isLogin("https://ssb.upao.edu.pe/login".toHttpUrl()))
    }
    @Test fun loginRejectsLookalikesAndPlaintext() {
        listOf("https://upaosso.upao.edu.pe.evil.test/", "https://evilssb.upao.edu.pe/", "http://ssb.upao.edu.pe/", "https://user:pass@ssb.upao.edu.pe/").forEach {
            assertFalse(it, TrustedServers.isLogin(it.toHttpUrl()))
        }
    }
    @Test fun canvasTokenOnlyGoesToOfficialOrigin() {
        assertTrue(TrustedServers.isCanvas("https://upao.instructure.com/api/v1/files/1".toHttpUrl()))
        listOf("https://upao.instructure.com.evil.test/", "http://upao.instructure.com/", "https://upao.instructure.com:8443/", "https://other.instructure.com/").forEach {
            assertFalse(it, TrustedServers.isCanvas(it.toHttpUrl()))
        }
    }
}

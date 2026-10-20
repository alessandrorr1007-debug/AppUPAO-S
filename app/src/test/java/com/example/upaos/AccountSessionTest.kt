package com.example.upaos

import com.example.upaos.data.upao.UpaoHttpClient
import kotlinx.coroutines.CancellationException
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AccountSessionTest {
    @Test fun switchDuringInflightResponseCannotPublishOrCopyCookies() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().addHeader("Set-Cookie", "sid=account-a; Path=/").setBody("old data")
                .setHeadersDelay(1, TimeUnit.SECONDS))
            val a = UpaoHttpClient.selectAccount("000111111")
            val executor = Executors.newSingleThreadExecutor()
            try {
                val result = executor.submit<Boolean> {
                    try { a.client.newCall(Request.Builder().url(server.url("/")).build()).execute().use { it.body!!.string() }; true }
                    catch (_: java.io.IOException) { false }
                    catch (_: CancellationException) { false }
                }
                assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
                val b = UpaoHttpClient.selectAccount("000222222")
                assertFalse(result.get(5, TimeUnit.SECONDS))
                assertTrue(a.cookieJar.loadForRequest(server.url("/")).isEmpty())
                assertTrue(b.cookieJar.loadForRequest(server.url("/")).isEmpty())
                try { a.requireValid(); fail("Old session must be invalid") } catch (_: CancellationException) { }
            } finally {
                executor.shutdownNow()
                UpaoHttpClient.clearAccount("000222222")
            }
        }
    }

    @Test fun oneAccountsLiveIdentityCannotAuthenticateAnotherAccount() = kotlinx.coroutines.test.runTest {
        val a = UpaoHttpClient.selectAccount("000111111")
        assertEquals("000111111", a.user)
        try { com.example.upaos.data.upao.UpaoAuth.ensureSession("000222222", "synthetic"); fail("Identity mismatch") }
        catch (_: CancellationException) { }
        UpaoHttpClient.clearAccount(a.user)
    }
    @Test fun switchAfterHeadersRejectsTheOldBodyAndClearsItsCookies() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().addHeader("Set-Cookie", "sid=account-a; Path=/")
                .setBody("old body").setBodyDelay(1, TimeUnit.SECONDS))
            val a = UpaoHttpClient.selectAccount("000111111")
            val headers = CountDownLatch(1)
            val executor = Executors.newSingleThreadExecutor()
            try {
                val result = executor.submit<Boolean> {
                    try {
                        a.client.newCall(Request.Builder().url(server.url("/")).build()).execute().use {
                            headers.countDown()
                            it.body!!.string()
                        }
                        true
                    } catch (_: java.io.IOException) { false }
                    catch (_: CancellationException) { false }
                }
                assertTrue(headers.await(5, TimeUnit.SECONDS))
                val b = UpaoHttpClient.selectAccount("000222222")
                assertFalse(result.get(5, TimeUnit.SECONDS))
                assertTrue(a.cookieJar.loadForRequest(server.url("/")).isEmpty())
                assertTrue(b.cookieJar.loadForRequest(server.url("/")).isEmpty())
            } finally {
                executor.shutdownNow()
                UpaoHttpClient.clearAccount("000222222")
            }
        }
    }


}

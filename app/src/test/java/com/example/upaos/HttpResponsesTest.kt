package com.example.upaos

import com.example.upaos.data.upao.consumeHttpResponse
import okhttp3.*
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.CancellationException

class HttpResponsesTest {
    private fun checkClosed(code: Int, action: (Response) -> Unit) {
        var closed = false
        val source = object : ForwardingSource(Buffer().writeUtf8("fixture")) {
            override fun close() { closed = true; super.close() }
        }.buffer()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
                .body(object : ResponseBody() {
                    override fun contentType(): MediaType? = null
                    override fun contentLength() = 7L
                    override fun source() = source
                }).build()
        }.build()
        try { consumeHttpResponse(client, Request.Builder().url("https://fixture.invalid/").build(), action) }
        finally { assertTrue("Response must be closed", closed) }
    }
    @Test fun closesWithoutReadingBodyOnSuccessAndUnauthorized() {
        checkClosed(200) { assertTrue(it.isSuccessful) }
        checkClosed(401) { assertFalse(it.isSuccessful) }
    }
    @Test fun closesWhenParsingFails() {
        try { checkClosed(200) { throw IllegalArgumentException("fixture") }; fail("Expected failure") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun closesAndPropagatesCancellation() {
        try { checkClosed(200) { throw CancellationException("fixture") }; fail("Expected cancellation") }
        catch (_: CancellationException) { }
    }
}

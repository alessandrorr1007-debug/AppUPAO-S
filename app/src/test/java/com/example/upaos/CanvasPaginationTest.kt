package com.example.upaos

import com.example.upaos.data.api.*
import okhttp3.mockwebserver.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrl

class CanvasPaginationTest {
    private fun api(server: MockWebServer) = CanvasPagedApi(Retrofit.Builder().baseUrl(server.url("/"))
        .addConverterFactory(GsonConverterFactory.create()).build().create(CanvasApiService::class.java), server.url("/"))
    private fun page(body: String, next: String? = null) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
        .also { if (next != null) it.addHeader("Link", "<$next>; rel=\"next\"") }

    @Test fun followsOpaqueLinkIncludingAllQueryParametersAndReturnsCompleteCourses() = runTest {
        MockWebServer().use { server ->
            server.enqueue(page("[{\"id\":1,\"name\":\"A\"}]", server.url("/api/v1/courses.json?cursor=a%2Bb&per_page=1").toString()))
            server.enqueue(page("[{\"id\":2,\"name\":\"B\"}]"))
            val result = api(server).getActiveCourses("Bearer synthetic")
            assertEquals(listOf(1L,2L), result.body()!!.map { it.id })
            assertNull(result.headers()["Link"])
            server.takeRequest(5, TimeUnit.SECONDS)!!
            val next = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("/api/v1/courses.json", next.requestUrl!!.encodedPath)
            assertEquals("a+b", next.requestUrl!!.queryParameter("cursor"))
            assertEquals("1", next.requestUrl!!.queryParameter("per_page"))
            assertEquals("Bearer synthetic", next.getHeader("Authorization"))
        }
    }
    @Test fun conversationsRequestLargerFirstPageAndFollowOpaqueContinuation() = runTest {
        MockWebServer().use { server ->
            server.enqueue(page("[{\"id\":1}]", server.url("/api/v1/conversations?cursor=opaque&per_page=7").toString()))
            server.enqueue(page("[{\"id\":2}]"))
            assertEquals(listOf(1L,2L),api(server).getConversations("Bearer synthetic").body()!!.map { it.id })
            assertEquals("100",server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!.queryParameter("per_page"))
            val continuation = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
            assertEquals("opaque",continuation.queryParameter("cursor"))
            assertEquals("7",continuation.queryParameter("per_page"))
        }
    }
    @Test fun secondPageErrorCannotPublishFirstPageAsSuccess() = runTest {
        MockWebServer().use { server ->
            server.enqueue(page("[{\"id\":1,\"name\":\"A\"}]", server.url("/api/v1/courses?page=2").toString()))
            server.enqueue(MockResponse().setResponseCode(503))
            var published = false
            try { api(server).getActiveCourses("Bearer synthetic"); published = true; fail("Expected HTTP failure") }
            catch (e: retrofit2.HttpException) { assertEquals(503,e.code()) }
            assertFalse(published)
        }
    }
    @Test fun foreignOriginAndNonApiPathsAreRejectedBeforeSendingAnotherRequest() = runTest {
        MockWebServer().use { server ->
            for (url in listOf("https://example.invalid/api/v1/courses?page=2", server.url("/accounts/other?page=2").toString())) {
                server.enqueue(page("[]", url))
                try { api(server).getActiveCourses("Bearer synthetic"); fail("Foreign destination") } catch (_: IOException) { }
                server.takeRequest(5, TimeUnit.SECONDS)!!
            }
            assertEquals(2,server.requestCount)
        }
    }
    @Test fun cyclesAreRejectedAndLastAllowedPageIsAccepted() = runTest {
        MockWebServer().use { server ->
            val first = server.url("/api/v1/courses?include[]=term&include[]=teachers&enrollment_state=active&per_page=100&page=1").toString()
            server.enqueue(page("[]", first))
            try { api(server).getActiveCourses("Bearer synthetic"); fail("Cycle") } catch (_: IOException) { }
            assertEquals(1,server.requestCount)
        }
        assertEquals("/next",canvasNextLink(listOf("</previous>; rel=\"prev\", </next>; rel=\"next last\"")))
        assertNull(canvasNextLink(listOf("</previous>; rel=\"prev\"")))
    }
    @Test fun assignmentsPeopleFilesAndDiscussionsAllUseTheSameCollector() = runTest {
        MockWebServer().use { server ->
            val paged = api(server)
            val endpoints = listOf("assignments","users","files","discussion_topics")
            for (endpoint in endpoints) {
                val body = when(endpoint) {
                    "assignments" -> "[{\"id\":1,\"name\":\"A\"}]"
                    "users" -> "[{\"id\":1,\"name\":\"A\"}]"
                    "files" -> "[{\"id\":1,\"display_name\":\"A\"}]"
                    else -> "[{\"id\":1,\"title\":\"A\"}]"
                }
                server.enqueue(page(body, server.url("/api/v1/courses/1/$endpoint?page=2").toString()))
                server.enqueue(page(body))
                val count = when(endpoint) {
                    "assignments" -> paged.getCourseAssignments("Bearer synthetic",1).body()!!.size
                    "users" -> paged.getCourseUsers("Bearer synthetic",1).body()!!.size
                    "files" -> paged.getCourseFiles("Bearer synthetic",1).body()!!.size
                    else -> paged.getCourseDiscussions("Bearer synthetic",1).body()!!.size
                }
                assertEquals(2,count)
            }
        }
    }
    @Test fun paginatesModulesAndTheirNestedItems() = runTest {
        MockWebServer().use { server ->
            server.enqueue(page("[{\"id\":1,\"name\":\"M1\",\"items_count\":3,\"items\":[]}]",server.url("/api/v1/courses/1/modules?page=2").toString()))
            server.enqueue(page("[{\"id\":2,\"name\":\"M2\",\"items_count\":0,\"items\":[]}]"))
            server.enqueue(page("[{\"id\":10,\"title\":\"A\"},{\"id\":11,\"title\":\"B\"}]",server.url("/api/v1/courses/1/modules/1/items?page=2").toString()))
            server.enqueue(page("[{\"id\":12,\"title\":\"C\"}]"))
            val modules = api(server).getCourseModules("Bearer synthetic",1).body()!!
            assertEquals(2,modules.size)
            assertEquals(listOf(10L,11L,12L),modules.first().items!!.map { it.id })
        }
    }
    @Test fun pageLimitAcceptsItsFinalPageAndRefusesAnExtraOne() = runTest {
        val origin = "http://fixture.invalid/".toHttpUrl()
        fun response(page: Int, next: Int?): retrofit2.Response<List<Int>> {
            val request = okhttp3.Request.Builder().url(origin.resolve("/api/v1/courses?page=$page")!!).build()
            val raw = okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("fixture")
            if (next != null) raw.addHeader("Link", "</api/v1/courses?page=$next>; rel=\"next\"")
            return retrofit2.Response.success(listOf(page), raw.build())
        }
        val complete = collectCanvasPages(origin, { response(1, 2) }, { response(2, null) }, maxPages=2)
        assertEquals(listOf(1,2),complete.body())
        try { collectCanvasPages(origin, { response(1,2) }, { response(2,3) }, maxPages=2); fail("Page limit") }
        catch (_: IOException) { }
    }
    @Test fun parsesQuotedDelimitersAndRejectsMalformedHeaders() {
        assertEquals("/next?opaque=a,b", canvasNextLink(listOf("</previous>; title=\"x, y; rel=next\"; rel=prev, </next?opaque=a,b>; rel=\"next\"")))
        for (header in listOf("</next>; rel=", "</next>; rel=\"\"", "garbage; rel=next", "</next>; rel=\"next", "</one>; rel=next, </two>; rel=next")) {
            try { canvasNextLink(listOf(header)); fail("Malformed header") } catch (_: IOException) { }
        }
    }
}

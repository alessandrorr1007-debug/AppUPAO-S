package com.example.upaos

import com.example.upaos.data.api.*
import com.example.upaos.data.canvas.*
import com.example.upaos.data.local.CanvasCacheStore
import com.example.upaos.data.model.*
import com.example.upaos.ui.canvas.*
import java.io.IOException
import java.lang.reflect.Proxy
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import org.junit.*
import org.junit.Assert.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@OptIn(ExperimentalCoroutinesApi::class)
class CanvasNativeIntegrationTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() = Dispatchers.setMain(dispatcher)
    @After fun after() = Dispatchers.resetMain()
    private fun api(server: MockWebServer) = CanvasPagedApi(Retrofit.Builder().baseUrl(server.url("/"))
        .addConverterFactory(GsonConverterFactory.create()).build().create(CanvasApiService::class.java), server.url("/"))
    private fun repository(api: CanvasApiService, valid: () -> Boolean = { true }): DefaultCanvasRepository {
        val cache = Proxy.newProxyInstance(CanvasCacheStore::class.java.classLoader, arrayOf(CanvasCacheStore::class.java)) { _, _, _ -> error("Cache not expected") } as CanvasCacheStore
        return DefaultCanvasRepository("synthetic-user", "synthetic-token", api, cache, valid)
    }
    @Test fun writesUseDocumentedFormsAndEscapeDiscussionHtml() = runTest {
        MockWebServer().use { server ->
            server.start()
            val repo = repository(api(server))
            server.enqueue(MockResponse().setBody("""{"id":2,"title":"Synthetic","permissions":{"reply":true}}"""))
            server.enqueue(MockResponse().setBody("""{"id":3,"message":"confirmed"}"""))
            assertEquals(3L, repo.postEntry(1, 2, null, "<script>\nhello").id)
            assertEquals("GET", server.takeRequest().method)
            val write = server.takeRequest()
            assertEquals("POST", write.method)
            assertEquals("/api/v1/courses/1/discussion_topics/2/entries", write.path)
            assertEquals("Bearer synthetic-token", write.getHeader("Authorization"))
            val decoded = java.net.URLDecoder.decode(write.body.readUtf8(), "UTF-8")
            assertTrue(decoded.contains("&lt;script&gt;<br>hello"))
            server.enqueue(MockResponse().setBody("""[{"id":5,"subject":"Synthetic"}]"""))
            assertEquals(5L, repo.sendConversation(1, listOf(7), "Synthetic", "body").single().id)
            val request = server.takeRequest()
            val form = java.net.URLDecoder.decode(request.body.readUtf8(), "UTF-8")
            assertTrue(form.contains("recipients[]=7")); assertTrue(form.contains("context_code=course_1"))
            assertFalse(request.path!!.contains("synthetic-token"))
            server.enqueue(MockResponse().setBody("""{"id":5,"messages":[{"id":8,"body":"reply"}]}"""))
            repo.sendReply(5, "reply")
            assertEquals("/api/v1/conversations/5/add_message", server.takeRequest().path)
        }
    }
    @Test fun lockedTopicAndChangedAccountPreventWrites() = runTest {
        MockWebServer().use { server ->
            server.start()
            val repo = repository(api(server))
            server.enqueue(MockResponse().setBody("""{"id":2,"locked_for_user":true}"""))
            try { repo.postEntry(1, 2, null, "synthetic"); fail() } catch (_: IllegalStateException) { }
            assertEquals(1, server.requestCount)
            val revoked = repository(api(server)) { false }
            try { revoked.sendReply(1, "synthetic"); fail() } catch (_: CancellationException) { }
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun scopedInboxRecipientsAndRepliesFollowPagination() = runTest {
        MockWebServer().use { server ->
            server.start(); val service = api(server)
            server.enqueue(MockResponse().setBody("""[{"id":1}]""").addHeader("Link", "<${server.url("/api/v1/conversations?opaque=next")}>; rel=\"next\""))
            server.enqueue(MockResponse().setBody("""[{"id":2}]"""))
            assertEquals(2, service.getScopedConversations("Bearer synthetic", "sent").body()!!.size)
            assertEquals("sent", server.takeRequest().requestUrl!!.queryParameter("scope")); server.takeRequest()
            server.enqueue(MockResponse().setBody("""[{"id":7,"name":"Synthetic"}]"""))
            assertEquals(7L, service.findRecipients("Bearer synthetic", "Sy", "course_1").body()!!.single().id)
            assertEquals("course_1", server.takeRequest().requestUrl!!.queryParameter("context"))
            server.enqueue(MockResponse().setBody("""[{"id":8,"message":"Synthetic"}]"""))
            assertEquals(8L, service.getDiscussionReplies("Bearer synthetic", 1, 2, 3).body()!!.single().id)
        }
    }
    @Test fun writeStateCoalescesAndNeverRetriesAnAmbiguousFailure() = runTest {
        val vm = CanvasWriteViewModel()
        val ready = CompletableDeferred<Unit>()
        var calls = 0
        vm.send { calls++; ready.await(); throw IOException("synthetic disconnect") }
        repeat(3) { vm.send { calls++ } }; runCurrent()
        assertEquals(1, calls); assertTrue(vm.state.value.sending)
        ready.complete(Unit); runCurrent()
        assertTrue(vm.state.value.uncertain); assertFalse(vm.state.value.confirmed)
        vm.send { calls++ }; runCurrent(); assertEquals(1, calls)
        vm.newDraft(); vm.send { calls++ }; runCurrent()
        assertTrue(vm.state.value.confirmed)
        vm.send { calls++ }; runCurrent(); assertEquals(2, calls)
    }
    @Test fun nativeRoutingDoesNotCaptureDeliveryOrForeignOrigins() {
        assertEquals("pages", canvasNativeRoute("/courses/1/pages/synthetic")!!.kind)
        assertEquals(2L, canvasNativeRoute("https://upao.instructure.com/courses/2/files/3/download")!!.course)
        assertNull(canvasNativeRoute("https://evil.test/courses/1/pages/synthetic"))
        assertNull(canvasNativeRoute("/courses/1/assignments/2/submissions/new"))
        assertNull(canvasNativeRoute("javascript:alert(1)"))
        val visible = canvasVisibleTabs(listOf(CanvasTab("modules"), CanvasTab("people", hidden=true), CanvasTab("grades", visibility="admins")))
        assertEquals(setOf("modules"), visible)
    }
    @Test fun htmlAndSignedDownloadsRejectUnsafeContent() {
        val html = canvasSafeHtml("""<script>alert(1)</script><iframe src="https://evil.test"></iframe><a href="javascript:alert(1)">bad</a><table><tr><td>ok</td></tr></table><img src="/courses/1/files/2/preview" onerror="alert(1)">""")
        assertFalse(html.contains("<script")); assertFalse(html.contains("<iframe")); assertFalse(html.contains("javascript:")); assertFalse(html.contains("onerror"))
        assertTrue(html.contains("<table>")); assertTrue(html.contains("/courses/1/files/2/preview"))
        assertNull(CanvasDocuments.safeUrl("http://upao.instructure.com/file"))
        assertNull(CanvasDocuments.safeUrl("https://upao.instructure.com.evil.test/file"))
        assertNull(CanvasDocuments.safeUrl("https://127.0.0.1/file"))
        assertNotNull(CanvasDocuments.safeUrl("https://files.canvas-user-content.com/file"))
        assertNotEquals(canvasDocumentOwner("one"), canvasDocumentOwner("two"))
    }
    @Test fun taskFiltersUseStudentSubmissionAndNeverCourseWideFlag() {
        val now = Instant.parse("2026-10-31T12:00:00Z")
        val a = CanvasAssignment(1, "Synthetic", dueAt="2026-10-30T12:00:00Z", hasSubmittedSubmissions=true)
        assertTrue(canvasTaskMatches(a, "Vencidas", now))
        assertFalse(canvasTaskMatches(a, "Entregadas", now))
        assertTrue(canvasTaskMatches(a.copy(submission=CanvasSubmission(workflowState="submitted")), "Entregadas", now))
        assertFalse(canvasTaskMatches(a.copy(dueAt=null), "Vencidas", now))
        assertFalse(canvasCanReply(CanvasDiscussionTopic(1, "Synthetic", lockAt="2026-10-30T12:00:00Z"), now))
        assertFalse(canvasCanReply(CanvasDiscussionTopic(1, "Synthetic", permissions=CanvasDiscussionPermissions(false)), now))
    }
}

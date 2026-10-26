package com.example.upaos

import com.example.upaos.data.api.*
import com.example.upaos.data.canvas.*
import com.example.upaos.data.local.CanvasCacheStore
import com.example.upaos.data.model.*
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.ResponseBody.Companion.toResponseBody
import java.lang.reflect.Proxy
import java.time.LocalDate

/** All data in this suite is synthetic. No Canvas account or real token is used. */
class CanvasReadFeaturesTest {
    private val gson = Gson()
    private val fixture = JsonParser.parseString(javaClass.getResource("/canvas_models_synthetic.json")!!.readText()).asJsonObject
    private fun api(server: MockWebServer): CanvasApiService = CanvasPagedApi(Retrofit.Builder()
        .baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build()
        .create(CanvasApiService::class.java), server.url("/"))

    @Test fun everyNewWireModelAcceptsMissingNullAndUnknownFields() {
        val models = listOf(CanvasAnnouncement::class.java, CanvasSubmissionComment::class.java,
            CanvasRubricAssessment::class.java, CanvasGrades::class.java, CanvasCourseEnrollment::class.java,
            CanvasAssignmentGroup::class.java, CanvasCalendarEvent::class.java, CanvasPage::class.java,
            CanvasQuizAttempt::class.java, CanvasQuizSubmissions::class.java, CanvasDiscussionEntry::class.java,
            CanvasDiscussionView::class.java, CanvasFolder::class.java, CanvasCompletionRequirement::class.java)
        models.forEach { type ->
            assertNotNull(gson.fromJson(fixture["empty"], type))
            assertNotNull(gson.fromJson(fixture["nulls"], type))
        }
        assertEquals("course_1", gson.fromJson(fixture["announcement"], CanvasAnnouncement::class.java).contextCode)
        val sub = gson.fromJson(fixture["submission"], CanvasSubmission::class.java)
        assertEquals(8.5, sub.rubricAssessment!!["criterion_synthetic"]!!.points!!, 0.0)
        assertEquals("Synthetic author", sub.comments!!.single().authorName)
        assertEquals("Synthetic reply", gson.fromJson(fixture["discussion"], CanvasDiscussionView::class.java).view!!.single().replies!!.single().message)
        assertEquals(10.0, gson.fromJson(fixture["quiz_submissions"], CanvasQuizSubmissions::class.java).submissions!!.single().keptScore!!, 0.0)
        assertNull(gson.fromJson(fixture["enrollment"], CanvasCourseEnrollment::class.java).grades!!.finalScore)
        assertEquals(25.0, gson.fromJson(fixture["group"], CanvasAssignmentGroup::class.java).groupWeight!!, 0.0)
        assertNotNull(gson.fromJson(fixture["event"], CanvasCalendarEvent::class.java).assignment)
        assertEquals("1", gson.fromJson(fixture["event"], CanvasCalendarEvent::class.java).id)
        assertEquals("assignment_1", gson.fromJson(fixture["assignment_event"], CanvasCalendarEvent::class.java).id)
        assertNotNull(gson.fromJson(fixture["page"], CanvasPage::class.java).body)
        assertEquals(2, gson.fromJson(fixture["folder"], CanvasFolder::class.java).filesCount)
        assertEquals(false, gson.fromJson(fixture["requirement"], CanvasCompletionRequirement::class.java).completed)
    }

    @Test fun deadlinesDoNotInventDueDatesFromLockOrUnidentifiedOverrides() {
        val a = CanvasAssignment(id=1, name="Synthetic", lockAt="2026-10-30", allDates=listOf(
            CanvasAssignmentDate(dueAt="2026-10-28"), CanvasAssignmentDate(dueAt="2026-10-29")))
        assertNull(a.effectiveDueAt)
        assertEquals("2026-10-27", a.copy(dueAt="2026-10-27").effectiveDueAt)
        assertEquals("2026-10-26", a.copy(allDates=listOf(CanvasAssignmentDate(dueAt="2026-10-26", base=true))).effectiveDueAt)
        assertNull(CanvasQuiz(id=1,title="Synthetic",lockAt="2026-10-30").effectiveDueAt)
    }

    @Test fun attemptsPointsAndPassingPreserveMeaning() {
        assertEquals("Ilimitados", canvasAttempts(-1)); assertEquals("No especificado", canvasAttempts(null))
        assertEquals("0", canvasAttempts(0)); assertEquals("8.5", canvasPoints(8.5)); assertEquals("10", canvasPoints(10.0))
        assertEquals(true, canvasPassing(10.5,20.0)); assertEquals(false, canvasPassing(10.4,20.0))
        assertNull(canvasPassing(null,20.0)); assertNull(canvasPassing(10.5,null)); assertNull(canvasPassing(0.0,0.0))
        assertEquals("2026-10-23",canvasCalendarDay("2026-10-24T01:00:00Z",java.time.ZoneId.of("America/Lima")))
    }

    @Test fun httpErrorsAreTypedAndBusyRequestsBackOff() = runTest {
        fun error(code: Int) = HttpException(Response.error<Any>(code, "synthetic".toResponseBody()))
        assertEquals(CanvasError.TOKEN, canvasError(error(401)))
        assertEquals(CanvasError.FORBIDDEN, canvasError(error(403)))
        assertEquals(CanvasError.MISSING, canvasError(error(404)))
        assertEquals(CanvasError.BUSY, canvasError(error(429)))
        var attempts=0
        val value=canvasReadWithBackoff { if (++attempts < 3) throw error(503); "synthetic" }
        assertEquals("synthetic",value); assertEquals(3,attempts)
        attempts=0
        try { canvasReadWithBackoff { attempts++; throw error(403) }; fail() } catch (_: HttpException) { }
        assertEquals(1,attempts)
    }

    @Test fun everyNewListFollowsOpaqueLink() = runTest {
        val calls: List<suspend (CanvasApiService) -> Response<*>> = listOf(
            { it.getAnnouncements("Bearer synthetic",listOf("course_1"),"2026-01-01","2026-12-01") },
            { it.getEnrollments("Bearer synthetic",1) }, { it.getAssignmentGroups("Bearer synthetic",1) },
            { it.getCalendarEvents("Bearer synthetic","event",listOf("course_1"),"2026-01-01","2026-12-01") },
            { it.getPages("Bearer synthetic",1) }, { it.getQuizzes("Bearer synthetic",1) },
            { it.getFolders("Bearer synthetic",1) }, { it.getFolderFiles("Bearer synthetic",1) })
        calls.forEach { call -> MockWebServer().use { server ->
            server.start()
            val next=server.url("/api/v1/opaque?cursor=synthetic&include%5B%5D=submission")
            server.enqueue(MockResponse().setBody("[{}]").setHeader("Link","<$next>; rel=\"next\""))
            server.enqueue(MockResponse().setBody("[{}]"))
            val response=call(api(server))
            assertEquals(2,(response.body() as List<*>).size)
            assertEquals("GET",server.takeRequest().method)
            assertEquals(next.encodedPath + "?" + next.encodedQuery,server.takeRequest().path)
        } }
    }

    @Test fun quizSubmissionEnvelopeIsPaginatedAndConversationDoesNotMarkRead() = runTest {
        MockWebServer().use { server ->
            server.start()
            val body=fixture["quiz_submissions"].toString()
            server.enqueue(MockResponse().setBody(body).setHeader("Link","<${server.url("/api/v1/quiz_next")}>; rel=next"))
            server.enqueue(MockResponse().setBody(body))
            val api=api(server)
            assertEquals(2,api.getQuizSubmissions("Bearer synthetic",1,1).body()!!.submissions!!.size)
            server.takeRequest(); server.takeRequest()
            server.enqueue(MockResponse().setBody("{\"id\":1}"))
            api.getConversationDetail("Bearer synthetic",1)
            assertEquals("false",server.takeRequest().requestUrl!!.queryParameter("auto_mark_as_read"))
        }
    }

    @Test fun repositoriesBatchAtMostTenContextsAndUseExplicitWindows() = runTest {
        val today=LocalDate.of(2026,10,24)
        assertEquals(today.minusDays(180).toString(),canvasAnnouncementWindow(today).first)
        assertEquals(today.plusDays(120).toString(),canvasCalendarWindow(today).second)
        assertEquals(listOf(10,10,1),canvasContextBatches((1L..21L).toList()).map { it.size })
        MockWebServer().use { server ->
            server.start()
            val cache=Proxy.newProxyInstance(CanvasCacheStore::class.java.classLoader,arrayOf(CanvasCacheStore::class.java)) { _,_,_ -> null } as CanvasCacheStore
            val repo=DefaultCanvasRepository("synthetic","synthetic",api(server),cache) { true }
            repeat(3) { server.enqueue(MockResponse().setBody("[]")) }
            repo.announcements((1L..21L).toList())
            repeat(3) {
                val request=server.takeRequest().requestUrl!!
                assertTrue(request.queryParameterValues("context_codes[]").size <= 10)
                assertNotNull(request.queryParameter("start_date")); assertNotNull(request.queryParameter("end_date"))
                assertEquals("true",request.queryParameter("active_only"))
            }
            repeat(6) { server.enqueue(MockResponse().setBody("[]")) }
            repo.calendar((1L..21L).toList())
            repeat(6) { assertTrue(server.takeRequest().requestUrl!!.queryParameterValues("context_codes[]").size <= 10) }
        }
    }

    @Test fun detailsUseReadOnlyPathsAndMissingFrontPageIsNormal() = runTest {
        MockWebServer().use { server ->
            server.start()
            val api=api(server)
            server.enqueue(MockResponse().setBody(fixture["submission"].toString()))
            assertEquals(2,api.getSubmission("Bearer synthetic",1,2).body()!!.attempt)
            val submission=server.takeRequest()
            assertEquals("/api/v1/courses/1/assignments/2/submissions/self",submission.requestUrl!!.encodedPath)
            assertEquals(listOf("submission_comments","rubric_assessment"),submission.requestUrl!!.queryParameterValues("include[]"))
            server.enqueue(MockResponse().setBody(fixture["page"].toString()))
            api.getPage("Bearer synthetic",1,"synthetic-page")
            assertEquals("/api/v1/courses/1/pages/synthetic-page",server.takeRequest().path)
            server.enqueue(MockResponse().setBody(fixture["discussion"].toString()))
            api.getDiscussionView("Bearer synthetic",1,2)
            assertEquals("/api/v1/courses/1/discussion_topics/2/view",server.takeRequest().path)
            val cache=Proxy.newProxyInstance(CanvasCacheStore::class.java.classLoader,arrayOf(CanvasCacheStore::class.java)) { _,_,_ -> null } as CanvasCacheStore
            val repository=DefaultCanvasRepository("synthetic","synthetic",api,cache) { true }
            server.enqueue(MockResponse().setResponseCode(404).setBody("{}"))
            assertNull(repository.frontPage(1))
            assertEquals("/api/v1/courses/1/front_page",server.takeRequest().path)
        }
    }
}

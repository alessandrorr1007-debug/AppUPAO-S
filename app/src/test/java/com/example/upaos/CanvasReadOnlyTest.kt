package com.example.upaos

import com.example.upaos.data.canvas.CanvasRequestPolicy
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test
import org.junit.Assert.*

/** Regression: only explicit student communications can write, never assignments or exams. */
class CanvasReadOnlyTest {
    @Test fun onlyStudentCommunicationEndpointsCanWrite() {
        val origin = "https://upao.instructure.com"
        listOf("/api/v1/conversations", "/api/v1/conversations/1/add_message",
            "/api/v1/courses/1/discussion_topics/2/entries",
            "/api/v1/courses/1/discussion_topics/2/entries/3/replies").forEach {
            assertTrue(CanvasRequestPolicy.allowed("POST", (origin + it).toHttpUrl()))
        }
        listOf("/api/v1/courses/1/assignments/2/submissions", "/api/v1/courses/1/quizzes/2/submissions",
            "/api/v1/conversations/mark_all_as_read", "/api/v1/conversations?redirect=1", "/courses/1").forEach {
            assertFalse(it, CanvasRequestPolicy.allowed("POST", (origin + it).toHttpUrl()))
        }
        listOf("PUT", "DELETE", "PATCH").forEach { assertFalse(CanvasRequestPolicy.allowed(it, "$origin/api/v1/conversations".toHttpUrl())) }
        assertFalse(CanvasRequestPolicy.allowed("POST", "https://evil.test/api/v1/conversations".toHttpUrl()))
        assertFalse(CanvasRequestPolicy.allowed("GET", "$origin/login".toHttpUrl()))
    }
}

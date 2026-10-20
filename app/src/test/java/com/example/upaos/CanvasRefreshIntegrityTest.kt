package com.example.upaos

import com.example.upaos.data.canvas.*
import com.example.upaos.data.model.*
import com.example.upaos.data.refresh.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class CanvasRefreshIntegrityTest {
    private val a = CanvasCourse(id = 1, name = "Course A")
    private val b = CanvasCourse(id = 2, name = "Course B")
    private fun todo(course: Long, id: Long) = CanvasTodoItem(courseId = course,
        assignment = CanvasAssignment(id, "Task $id", submissionTypes = listOf("online_upload")))

    @Test fun failingCourseKeepsItsLastGoodTasksAndDoesNotPublishGlobalCache() = runTest {
        val old = todo(2, 20)
        val result = refreshCanvasTodos(listOf(old), mapOf(2L to listOf(old)),
            { listOf(a, b) }, { emptyList() }, { if (it.id == 2L) throw IOException("fixture") else listOf(todo(1, 10).assignment!!) })
        assertTrue(result is RefreshResult.Partial)
        val partial = result as RefreshResult.Partial
        assertEquals(setOf(10L, 20L), partial.value.items.mapNotNull { it.assignment?.id }.toSet())
        assertEquals(setOf(1L), partial.value.completedCourses)
        assertEquals("Course B", partial.issues.single().source)
        var cached = listOf(old)
        var freshness = 123L
        try { publishComplete(result) { cached = it.items; freshness = 999 }; fail("Partial update") }
        catch (_: IncompleteRefreshException) { }
        assertEquals(listOf(old), cached)
        assertEquals(123L, freshness)
    }
    @Test fun globalFailureAndCourseListFailureNeverProduceFreshCompleteData() = runTest {
        val old = todo(1, 10)
        val globalFailure = refreshCanvasTodos(listOf(old), emptyMap(), { listOf(a) }, { throw IOException("fixture") }, { emptyList() })
        assertTrue(globalFailure is RefreshResult.Partial)
        assertTrue((globalFailure as RefreshResult.Partial).value.completedCourses.isEmpty())
        val coursesFailure = refreshCanvasTodos(listOf(old), emptyMap(), { throw IOException("fixture") }, { emptyList() }, { emptyList() })
        assertTrue(coursesFailure is RefreshResult.Error)
    }
    @Test fun completeEmptyDataAndSubmittedTasksCanReplaceOldTasks() = runTest {
        val old = todo(1, 10)
        val result = refreshCanvasTodos(listOf(old), emptyMap(), { listOf(a) }, { listOf(old) }, {
            listOf(old.assignment!!.copy(submission = CanvasSubmission(workflowState = "submitted")))
        }).requireComplete()
        assertTrue(result.items.isEmpty())
        assertEquals(setOf(1L), result.completedCourses)
    }
    @Test fun cancelledAssignmentReadAlwaysPropagatesCancellation() = runTest {
        try { refreshCanvasTodos(emptyList(), emptyMap(), { listOf(a) }, { emptyList() }, { throw CancellationException("fixture") }); fail("Cancellation") }
        catch (_: CancellationException) { }
    }
    @Test fun reopeningOfflineRestoresNewerValidCourseDataWithoutResurrectingRemovedCourseCaches() {
        val old = todo(1, 10)
        val latest = todo(1, 11)
        val removedCourse = todo(2, 20)
        val restored = restoreCanvasTodos(listOf(old), mapOf(1L to listOf(latest), 2L to listOf(removedCourse)), setOf(1L))
        assertEquals(listOf(latest), restored)
        assertTrue(restoreCanvasTodos(listOf(old), mapOf(1L to emptyList()), setOf(1L)).isEmpty())
    }
    @Test fun aFirstPageCannotBeCachedAsACompleteList() {
        val response = retrofit2.Response.success(listOf(todo(1, 10)), okhttp3.Headers.headersOf("Link", "<https://fixture.invalid/?page=2>; rel=\"next\""))
        try { response.singlePageBody(); fail("First page is incomplete") } catch (_: IOException) { }
        assertTrue(retrofit2.Response.success(emptyList<CanvasTodoItem>()).singlePageBody().isEmpty())
    }
}

package com.example.upaos.data.canvas

import com.example.upaos.data.model.*
import com.example.upaos.data.refresh.*
import com.example.upaos.data.agenda.parseServerDate
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

internal fun <T> Response<T>.completeBody(): T {
    if (!isSuccessful) { errorBody()?.close(); throw HttpException(this) }
    return body() ?: throw IOException("Canvas devolvió una respuesta vacía")
}

/** Until the paged adapter is used, a first page must never be cached as a full list. */
internal fun <T> Response<T>.singlePageBody(): T {
    val value = completeBody()
    if (headers().values("Link").any { Regex("rel\\s*=\\s*\"?next", RegexOption.IGNORE_CASE).containsMatchIn(it) })
        throw IOException("Hay páginas de Canvas pendientes")
    return value
}

internal fun Throwable.isCanvasUnauthorized(): Boolean =
    this is HttpException && code() == 401 || cause?.takeIf { it !== this }?.isCanvasUnauthorized() == true

data class CanvasTodoSnapshot(
    val items: List<CanvasTodoItem>,
    val courses: List<CanvasCourse>,
    val byCourse: Map<Long, List<CanvasTodoItem>>,
    val completedCourses: Set<Long>
)

internal fun restoreCanvasTodos(global: List<CanvasTodoItem>, byCourse: Map<Long, List<CanvasTodoItem>>, activeCourseIds: Set<Long>): List<CanvasTodoItem> {
    val validCourses = byCourse.filterKeys { it in activeCourseIds }
    return global.filter { it.courseId !in validCourses.keys } + validCourses.values.flatten()
}

private fun pendingItems(course: CanvasCourse, assignments: List<CanvasAssignment>, global: List<CanvasTodoItem>, now: Long): List<CanvasTodoItem> {
    val submitted = assignments.filter { it.isSubmitted }.map { it.id }.toSet()
    val combined = global.filter { it.courseId == course.id && it.assignment?.isSubmitted != true && it.assignment?.id !in submitted }.toMutableList()
    val known = combined.mapNotNull { it.assignment?.id }.toMutableSet()
    for (assignment in assignments) {
        if (!known.add(assignment.id) || assignment.isSubmitted) continue
        if (assignment.submissionTypes.orEmpty().none { it in setOf("online_upload", "online_text_entry", "online_url", "media_recording", "student_annotation", "online_quiz") }) continue
        val lockedAt = parseServerDate(assignment.lockAt)
        if (assignment.lockedForUser == true && lockedAt != null && lockedAt < now - 7 * 86_400_000L) continue
        combined += CanvasTodoItem(type = "submitting", contextType = "Course", courseId = course.id,
            contextName = course.name, contextShortName = course.courseCode, htmlUrl = assignment.htmlUrl,
            assignment = assignment, quiz = if (assignment.isQuizAssignment == true || assignment.quizId != null)
                CanvasQuiz(assignment.quizId ?: assignment.id, assignment.name, assignment.effectiveDueAt,
                    assignment.lockAt, assignment.unlockAt, assignment.pointsPossible, assignment.htmlUrl) else null)
    }
    return combined
}

internal suspend fun refreshCanvasTodos(
    previous: List<CanvasTodoItem>,
    previousByCourse: Map<Long, List<CanvasTodoItem>>,
    loadCourses: suspend () -> List<CanvasCourse>,
    loadGlobal: suspend () -> List<CanvasTodoItem>,
    loadAssignments: suspend (CanvasCourse) -> List<CanvasAssignment>,
    now: Long = System.currentTimeMillis()
): RefreshResult<CanvasTodoSnapshot> = coroutineScope {
    val courses = when (val result = captureRefresh("Cursos de Canvas", loadCourses)) {
        is RefreshResult.Complete -> result.value.distinctBy { it.id }
        is RefreshResult.Error -> return@coroutineScope result
        is RefreshResult.Partial -> error("Se requiere una lista completa de cursos")
    }
    val global = async { captureRefresh("Pendientes globales de Canvas", loadGlobal) }
    val semaphore = Semaphore(4)
    val assignments = courses.map { course -> async {
        course to semaphore.withPermit { captureRefresh(course.displayTitle) { loadAssignments(course) } }
    } }.awaitAll()
    val globalResult = global.await()
    val globalItems = when (globalResult) {
        is RefreshResult.Complete -> globalResult.value
        is RefreshResult.Error -> previous
        is RefreshResult.Partial -> error("Se requiere una lista global completa")
    }
    val issues = mutableListOf<RefreshIssue>()
    if (globalResult is RefreshResult.Error) issues += globalResult.issue
    val byCourse = mutableMapOf<Long, List<CanvasTodoItem>>()
    val completed = mutableSetOf<Long>()
    for ((course, result) in assignments) {
        when (result) {
            is RefreshResult.Complete -> {
                byCourse[course.id] = pendingItems(course, result.value, globalItems, now)
                // Global data participates in each course snapshot; do not persist it if that read failed.
                if (globalResult is RefreshResult.Complete) completed += course.id
            }
            is RefreshResult.Error -> {
                issues += result.issue
                byCourse[course.id] = previousByCourse[course.id] ?: previous.filter { it.courseId == course.id }
            }
            is RefreshResult.Partial -> error("Se requieren todas las páginas de entregas del curso")
        }
    }
    val courseIds = courses.map { it.id }.toSet()
    val extras = globalItems.filter { it.courseId !in courseIds && it.assignment?.isSubmitted != true }
    val items = (byCourse.values.flatten() + extras).distinctBy { "${it.courseId}:${it.assignment?.id ?: it.quiz?.id}:${it.webUrl}:${it.title}" }
    val snapshot = CanvasTodoSnapshot(items, courses, byCourse, completed)
    if (issues.isEmpty()) RefreshResult.Complete(snapshot) else RefreshResult.Partial(snapshot, issues)
}

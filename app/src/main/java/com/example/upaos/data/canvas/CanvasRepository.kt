package com.example.upaos.data.canvas

import com.example.upaos.data.api.CanvasApiService
import com.example.upaos.data.local.CanvasCacheStore
import com.example.upaos.data.model.*
import com.example.upaos.data.refresh.RefreshResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface CanvasHubRepository {
    val cache: CanvasCacheStore
    fun requireSession()
    suspend fun courses(): List<CanvasCourse>
    suspend fun conversations(): List<CanvasConversation>
    suspend fun notifications(): List<CanvasNotificationItem>
    suspend fun groups(): List<CanvasGroup>
    suspend fun todos(previous: List<CanvasTodoItem>): RefreshResult<CanvasTodoSnapshot>
}

interface CanvasCourseRepository {
    suspend fun announcements(ids: List<Long>): List<CanvasAnnouncement> = error("Unsupported")
    suspend fun submission(course: Long, assignment: Long): CanvasSubmission = error("Unsupported")
    suspend fun enrollments(course: Long): List<CanvasCourseEnrollment> = error("Unsupported")
    suspend fun assignmentGroups(course: Long): List<CanvasAssignmentGroup> = error("Unsupported")
    suspend fun calendar(ids: List<Long>): List<CanvasCalendarEvent> = error("Unsupported")
    suspend fun pages(course: Long): List<CanvasPage> = error("Unsupported")
    suspend fun page(course: Long, page: String): CanvasPage = error("Unsupported")
    suspend fun frontPage(course: Long): CanvasPage? = error("Unsupported")
    suspend fun quizzes(course: Long): List<CanvasQuiz> = error("Unsupported")
    suspend fun quizAttempts(course: Long, quiz: Long): List<CanvasQuizAttempt> = error("Unsupported")
    suspend fun discussionView(course: Long, topic: Long): CanvasDiscussionView = error("Unsupported")
    suspend fun folders(course: Long): List<CanvasFolder> = error("Unsupported")
    suspend fun folderFiles(folder: Long): List<CanvasFileDetail> = error("Unsupported")
    suspend fun file(course: Long, file: Long): CanvasFileDetail = error("Unsupported")

    suspend fun modules(id: Long): List<CanvasModule>
    suspend fun assignments(id: Long): List<CanvasAssignment>
    suspend fun people(id: Long): List<CanvasUser>
    suspend fun courseGroups(id: Long): List<CanvasGroup>
    suspend fun discussions(id: Long): List<CanvasDiscussionTopic>
    suspend fun files(id: Long): List<CanvasFileDetail>
    suspend fun syllabus(id: Long): CanvasCourse
    suspend fun tabs(id: Long): List<CanvasTab>
    suspend fun groupPeople(id: Long): List<CanvasUser>
    suspend fun conversation(id: Long): CanvasConversation
    suspend fun fileByUrl(url: String): CanvasFileDetail
}

/** Immutable account/session/token; all dependencies can be replaced by fakes. */
class DefaultCanvasRepository(
    private val user: String,
    private val token: String,
    private val api: CanvasApiService,
    override val cache: CanvasCacheStore,
    private val validateSession: () -> Boolean
) : CanvasHubRepository, CanvasCourseRepository {
    private val auth = "Bearer $token"
    override fun requireSession() {
        if (!validateSession()) throw CancellationException("La cuenta de Canvas cambió")
    }
    private suspend fun <T> read(load: suspend () -> T): T = withContext(Dispatchers.IO) {
        requireSession()
        val result = load()
        requireSession()
        result
    }
    override suspend fun courses() = read { api.getActiveCourses(auth).completeBody().filter { it.name.isNotBlank() } }
    override suspend fun conversations() = read { api.getConversations(auth).completeBody() }
    override suspend fun notifications() = read { api.getActivityStream(auth).completeBody() }
    override suspend fun groups() = read { api.getSelfGroups(auth).completeBody() }
    override suspend fun todos(previous: List<CanvasTodoItem>) = read {
        refreshCanvasTodos(previous, cache.cargarPendientesPorCurso(user),
            loadCourses = { canvasReadWithBackoff { courses() } }, loadGlobal = { canvasReadWithBackoff { api.getTodoList(auth).completeBody() } },
            loadAssignments = { course -> canvasReadWithBackoff { assignments(course.id) } })
    }
    override suspend fun modules(id: Long) = read { api.getCourseModules(auth,id).completeBody() }
    override suspend fun assignments(id: Long) = read { api.getCourseAssignments(auth,id).completeBody() }
    override suspend fun people(id: Long) = read { api.getCourseUsers(auth,id).completeBody() }
    override suspend fun courseGroups(id: Long) = read { api.getCourseGroups(auth,id).completeBody() }
    override suspend fun discussions(id: Long) = read { api.getCourseDiscussions(auth,id).completeBody() }
    override suspend fun files(id: Long) = read { api.getCourseFiles(auth,id).completeBody() }
    override suspend fun syllabus(id: Long) = read { api.getCourseDetail(auth,id).completeBody() }
    override suspend fun tabs(id: Long) = read { api.getCourseTabs(auth,id).completeBody() }
    override suspend fun groupPeople(id: Long) = read { api.getGroupUsers(auth,id).completeBody() }
    override suspend fun conversation(id: Long) = read { api.getConversationDetail(auth,id).completeBody() }
    override suspend fun fileByUrl(url: String) = read { api.getFileByUrl(auth,url).completeBody() }
    override suspend fun announcements(ids: List<Long>) = read {
        val (start, end) = canvasAnnouncementWindow()
        canvasContextBatches(ids).flatMap { api.getAnnouncements(auth, it, start, end).completeBody() }
            .distinctBy { it.contextCode to it.id }
    }
    override suspend fun submission(course: Long, assignment: Long) = read { api.getSubmission(auth, course, assignment).completeBody() }
    override suspend fun enrollments(course: Long) = read { api.getEnrollments(auth, course).completeBody() }
    override suspend fun assignmentGroups(course: Long) = read { api.getAssignmentGroups(auth, course).completeBody() }
    override suspend fun calendar(ids: List<Long>) = read {
        val (start, end) = canvasCalendarWindow()
        canvasContextBatches(ids).flatMap { contexts -> listOf("assignment", "event").flatMap { type ->
            api.getCalendarEvents(auth, type, contexts, start, end).completeBody()
        } }.distinctBy { it.contextCode to it.id }
    }
    override suspend fun pages(course: Long) = read { api.getPages(auth, course).completeBody() }
    override suspend fun page(course: Long, page: String) = read { api.getPage(auth, course, page).completeBody() }
    override suspend fun frontPage(course: Long) = read {
        val response = api.getFrontPage(auth, course)
        if (response.code() == 404) { response.errorBody()?.close(); null } else response.completeBody()
    }
    override suspend fun quizzes(course: Long) = read { api.getQuizzes(auth, course).completeBody() }
    override suspend fun quizAttempts(course: Long, quiz: Long) = read { api.getQuizSubmissions(auth, course, quiz).completeBody().submissions.orEmpty() }
    override suspend fun discussionView(course: Long, topic: Long) = read { api.getDiscussionView(auth, course, topic).completeBody() }
    override suspend fun folders(course: Long) = read { api.getFolders(auth, course).completeBody() }
    override suspend fun folderFiles(folder: Long) = read { api.getFolderFiles(auth, folder).completeBody() }
    override suspend fun file(course: Long, file: Long) = read { api.getCourseFile(auth, course, file).completeBody() }

}

fun interface CanvasResourceRepository<T> { suspend fun load(): T }

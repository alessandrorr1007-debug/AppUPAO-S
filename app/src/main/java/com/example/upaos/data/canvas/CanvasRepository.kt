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
            loadCourses = { courses() }, loadGlobal = { api.getTodoList(auth).completeBody() },
            loadAssignments = { assignments(it.id) })
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
}

fun interface CanvasResourceRepository<T> { suspend fun load(): T }

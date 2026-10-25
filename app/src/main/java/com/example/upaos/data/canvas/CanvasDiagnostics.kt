package com.example.upaos.data.canvas

import com.example.upaos.data.api.CanvasApiClient
import com.example.upaos.data.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

/** Reports contain no request parameters, identifiers, content, or credentials. */
data class CanvasDiagnosticRow(val endpoint: String, val http: Int?, val count: Int?, val millis: Long)

class CanvasDiagnostics(private val validate: () -> Boolean,
    private val api: com.example.upaos.data.api.CanvasApiService = CanvasApiClient.pagedApiService) {
    suspend fun run(token: String, emit: (CanvasDiagnosticRow) -> Unit) = withContext(Dispatchers.IO) {
        val auth = "Bearer $token"
        val tested = mutableSetOf<String>()
        val names = listOf("conversations/{id} (auto_mark_as_read=false)", "groups/{G}/users", "courses/{C}",
            "courses/{C}/tabs", "courses/{C}/modules", "courses/{C}/modules/{M}/items", "courses/{C}/assignments",
            "courses/{C}/assignments/{A}/submissions/self", "courses/{C}/users", "courses/{C}/groups", "courses/{C}/files",
            "courses/{C}/files/{F}", "file metadata (URL)", "courses/{C}/discussion_topics", "courses/{C}/discussion_topics/{T}/view",
            "announcements", "courses/{C}/enrollments", "courses/{C}/assignment_groups", "calendar_events (assignment)",
            "calendar_events (event)", "courses/{C}/pages", "courses/{C}/pages/{page}", "courses/{C}/front_page",
            "courses/{C}/quizzes", "courses/{C}/quizzes/{Q}/submissions", "courses/{C}/folders", "folders/{F}/files")
        fun missing() { names.filter { it !in tested }.forEach { emit(CanvasDiagnosticRow(it, null, null, 0)) } }
        suspend fun <T> probe(name: String, call: suspend () -> Response<T>): T? {
            if (!validate()) throw CancellationException("Session changed")
            tested += name
            val start = System.nanoTime()
            var code: Int? = null
            var count: Int? = null
            var result: T? = null
            try {
                val response = call()
                code = response.code()
                if (response.isSuccessful) {
                    result = response.body()
                    count = when (val body = result) {
                        is List<*> -> body.size
                        is CanvasQuizSubmissions -> body.submissions?.size
                        else -> null
                    }
                }
                response.errorBody()?.close()
            } catch (e: CancellationException) { throw e }
            catch (e: retrofit2.HttpException) { code = e.code(); e.response()?.errorBody()?.close() }
            catch (_: Exception) { /* Deliberately do not expose exception text or URLs. */ }
            if (!validate()) throw CancellationException("Session changed")
            emit(CanvasDiagnosticRow(name, code, count, (System.nanoTime() - start) / 1_000_000))
            return result
        }
        probe("users/self") { api.getUserSelf(auth) }
        val courses = probe("courses") { api.getActiveCourses(auth) }.orEmpty()
        probe("users/self/todo") { api.getTodoList(auth) }
        probe("users/self/activity_stream") { api.getActivityStream(auth) }
        val conversations = probe("conversations") { api.getConversations(auth) }.orEmpty()
        conversations.firstOrNull()?.let { probe("conversations/{id} (auto_mark_as_read=false)") { api.getConversationDetail(auth, it.id) } }
        val groups = probe("users/self/groups") { api.getSelfGroups(auth) }.orEmpty()
        groups.firstOrNull()?.let { probe("groups/{G}/users") { api.getGroupUsers(auth, it.id) } }
        val course = courses.firstOrNull()?.id ?: run { missing(); return@withContext }
        probe("courses/{C}") { api.getCourseDetail(auth, course) }
        probe("courses/{C}/tabs") { api.getCourseTabs(auth, course) }
        val modules = probe("courses/{C}/modules") { api.getCourseModules(auth, course) }.orEmpty()
        modules.firstOrNull()?.let { probe("courses/{C}/modules/{M}/items") { api.getModuleItems(auth, course, it.id) } }
        val assignments = probe("courses/{C}/assignments") { api.getCourseAssignments(auth, course) }.orEmpty()
        assignments.firstOrNull()?.let { probe("courses/{C}/assignments/{A}/submissions/self") { api.getSubmission(auth, course, it.id) } }
        probe("courses/{C}/users") { api.getCourseUsers(auth, course) }
        probe("courses/{C}/groups") { api.getCourseGroups(auth, course) }
        val files = probe("courses/{C}/files") { api.getCourseFiles(auth, course) }.orEmpty()
        files.firstOrNull()?.let { probe("courses/{C}/files/{F}") { api.getCourseFile(auth, course, it.id) } }
        files.firstOrNull()?.let { file -> probe("file metadata (URL)") { api.getFileByUrl(auth, "https://upao.instructure.com/api/v1/courses/$course/files/${file.id}") } }
        val topics = probe("courses/{C}/discussion_topics") { api.getCourseDiscussions(auth, course) }.orEmpty()
        topics.firstOrNull()?.let { probe("courses/{C}/discussion_topics/{T}/view") { api.getDiscussionView(auth, course, it.id) } }
        val (start, end) = canvasAnnouncementWindow()
        probe("announcements") { api.getAnnouncements(auth, listOf("course_$course"), start, end) }
        probe("courses/{C}/enrollments") { api.getEnrollments(auth, course) }
        probe("courses/{C}/assignment_groups") { api.getAssignmentGroups(auth, course) }
        val (calendarStart, calendarEnd) = canvasCalendarWindow()
        for (type in listOf("assignment", "event")) probe("calendar_events ($type)") { api.getCalendarEvents(auth, type, listOf("course_$course"), calendarStart, calendarEnd) }
        val pages = probe("courses/{C}/pages") { api.getPages(auth, course) }.orEmpty()
        pages.firstOrNull()?.let { page -> (page.url ?: page.pageId?.let { "page_id:$it" })?.let { probe("courses/{C}/pages/{page}") { api.getPage(auth, course, it) } } }
        probe("courses/{C}/front_page") { api.getFrontPage(auth, course) }
        val quizzes = probe("courses/{C}/quizzes") { api.getQuizzes(auth, course) }.orEmpty()
        quizzes.firstOrNull()?.let { probe("courses/{C}/quizzes/{Q}/submissions") { api.getQuizSubmissions(auth, course, it.id) } }
        val folders = probe("courses/{C}/folders") { api.getFolders(auth, course) }.orEmpty()
        folders.firstOrNull()?.id?.let { probe("folders/{F}/files") { api.getFolderFiles(auth, it) } }
        missing()
    }
}

package com.example.upaos.data.canvas

import android.content.Context
import com.example.upaos.data.model.*
import com.example.upaos.data.refresh.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CanvasZoomSnapshot(
    val meetings: List<CourseZoomMeeting> = emptyList(),
    val ltiUrl: String? = null,
    val custom: CourseZoomMeeting? = null,
    val failedSources: Set<String> = emptySet()
)

interface CanvasZoomRepository {
    suspend fun load(): CanvasZoomSnapshot
    fun save(meeting: CourseZoomMeeting)
    fun delete()
}

class DefaultCanvasZoomRepository(
    private val context: Context,
    private val repository: CanvasCourseRepository,
    private val course: CanvasCourse,
    private val validateSession: () -> Unit
) : CanvasZoomRepository {
    override suspend fun load(): CanvasZoomSnapshot = withContext(Dispatchers.IO) {
        validateSession()
        val custom = CourseZoomPreferences.getCustomZoomMeeting(context, course.id)
        val found = mutableListOf<CourseZoomMeeting>()
        custom?.let { found += it }
        val failed = mutableSetOf<String>()
        var lti: String? = null
        suspend fun attempt(source: String, action: suspend () -> Unit) {
            when (captureRefresh(source, action)) {
                is RefreshResult.Error -> failed += source
                else -> Unit
            }
        }
        attempt("TABS") {
            repository.tabs(course.id).firstOrNull {
                it.label?.contains("zoom",true) == true || it.id.contains("zoom",true)
            }?.let {
                lti = it.fullUrl ?: it.htmlUrl?.let { url ->
                    if (url.startsWith("http")) url else "https://upao.instructure.com$url"
                }
            }
        }
        attempt("MODULE") {
            for (module in repository.modules(course.id)) for (item in module.items.orEmpty()) {
                val target = item.url ?: item.htmlUrl ?: continue
                if (item.title?.contains("zoom",true) != true && !target.contains("zoom.us",true)) continue
                val extracted = ZoomUrlExtractor.extractZoomMeetingsFromText(target,course.id,
                    item.title ?: "Enlace Zoom en " + module.name,"MODULE")
                found += extracted
                if (extracted.isEmpty() && target.contains("zoom",true)) found += CourseZoomMeeting(
                    courseId=course.id, title=item.title ?: "Enlace Zoom (" + module.name + ")",
                    joinUrl=target, origin="MODULE")
            }
        }
        attempt("ANNOUNCEMENT") {
            for (topic in repository.announcements(listOf(course.id))) {
                found += ZoomUrlExtractor.extractZoomMeetingsFromText(
                    topic.title.orEmpty() + "\n" + topic.message.orEmpty(), course.id,
                    topic.title ?: "Aviso con Zoom","ANNOUNCEMENT")
            }
        }
        attempt("SYLLABUS") {
            val syllabus = repository.syllabus(course.id).syllabusBody.orEmpty()
            found += ZoomUrlExtractor.extractZoomMeetingsFromText(syllabus,
                course.id,"Zoom en Sílabo","SYLLABUS")
        }
        validateSession()
        CanvasZoomSnapshot(found.distinctBy { it.joinUrl.trim().lowercase() },lti,custom,failed)
    }

    override fun save(meeting: CourseZoomMeeting) {
        validateSession()
        require(meeting.courseId == course.id)
        CourseZoomPreferences.saveCustomZoomMeeting(context,meeting)
    }
    override fun delete() {
        validateSession()
        CourseZoomPreferences.deleteCustomZoomMeeting(context,course.id)
    }
}

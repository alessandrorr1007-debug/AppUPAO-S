package com.example.upaos.data.canvas

import android.content.Context
import com.example.upaos.data.model.CourseZoomMeeting

object CourseZoomPreferences {
    private const val PREFS_NAME = "course_zoom_preferences"

    fun getCustomZoomMeeting(context: Context, courseId: Long): CourseZoomMeeting? {
        val prefs = context.getSharedPreferences("${PREFS_NAME}_${com.example.upaos.data.local.TokenManager(context).getSavedUser() ?: "anonymous"}", Context.MODE_PRIVATE)
        // Migrate the old course preference on first use, preserving saved meeting links.
        if (!prefs.contains("zoom_url_$courseId")) {
            val legacy = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (legacy.contains("zoom_url_$courseId")) {
                val editor = prefs.edit()
                val oldEditor = legacy.edit()
                listOf("zoom_url", "zoom_title", "zoom_id", "zoom_pwd").forEach { prefix ->
                    val key = "${prefix}_$courseId"
                    legacy.getString(key, null)?.let { editor.putString(key, it) }
                    oldEditor.remove(key)
                }
                if (editor.commit()) oldEditor.commit()
            }
        }
        val url = prefs.getString("zoom_url_$courseId", null) ?: return null
        val title = prefs.getString("zoom_title_$courseId", "Clase Virtual Zoom") ?: "Clase Virtual Zoom"
        val meetingId = prefs.getString("zoom_id_$courseId", null)
        val passcode = prefs.getString("zoom_pwd_$courseId", null)
        return CourseZoomMeeting(
            courseId = courseId,
            title = title,
            joinUrl = url,
            meetingId = meetingId,
            passcode = passcode,
            origin = "CUSTOM"
        )
    }

    fun saveCustomZoomMeeting(context: Context, meeting: CourseZoomMeeting) {
        val prefs = context.getSharedPreferences("${PREFS_NAME}_${com.example.upaos.data.local.TokenManager(context).getSavedUser() ?: "anonymous"}", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("zoom_url_${meeting.courseId}", meeting.joinUrl)
            .putString("zoom_title_${meeting.courseId}", meeting.title)
            .putString("zoom_id_${meeting.courseId}", meeting.meetingId)
            .putString("zoom_pwd_${meeting.courseId}", meeting.passcode)
            .apply()
    }

    fun deleteCustomZoomMeeting(context: Context, courseId: Long) {
        val prefs = context.getSharedPreferences("${PREFS_NAME}_${com.example.upaos.data.local.TokenManager(context).getSavedUser() ?: "anonymous"}", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("zoom_url_$courseId")
            .remove("zoom_title_$courseId")
            .remove("zoom_id_$courseId")
            .remove("zoom_pwd_$courseId")
            .apply()
    }
}

object ZoomUrlExtractor {
    private val ZOOM_URL_REGEX = """https?://[a-zA-Z0-9.\-_]*zoom\.us/[jsw]/[0-9]+[^\s<>"']*""".toRegex(RegexOption.IGNORE_CASE)
    private val MEETING_ID_REGEX = """(?:ID(?:\s*de\s*reuni[oó]n)?|Meeting\s*ID)\s*[:：]?\s*([0-9\s]{9,14})""".toRegex(RegexOption.IGNORE_CASE)
    private val PASSCODE_REGEX = """(?:C[oó]digo(?:\s*de\s*acceso)?|Contrase[ñn]a|Clave|Passcode|Password)\s*[:：]?\s*([A-Za-z0-9@#*_\-]+)""".toRegex(RegexOption.IGNORE_CASE)

    fun extractZoomMeetingsFromText(
        text: String,
        courseId: Long,
        defaultTitle: String,
        origin: String
    ): List<CourseZoomMeeting> {
        val matches = ZOOM_URL_REGEX.findAll(text).map { it.value.trim() }.distinct().toList()
        if (matches.isEmpty()) return emptyList()

        val foundMeetingId = MEETING_ID_REGEX.find(text)?.groupValues?.getOrNull(1)?.replace(" ", "")?.trim()
        val foundPasscode = PASSCODE_REGEX.find(text)?.groupValues?.getOrNull(1)?.trim()

        return matches.map { url ->
            val idFromUrl = if (foundMeetingId.isNullOrBlank()) {
                """zoom\.us/[jsw]/([0-9]+)""".toRegex().find(url)?.groupValues?.getOrNull(1)
            } else foundMeetingId

            val pwdFromUrl = if (foundPasscode.isNullOrBlank()) {
                """[?&]pwd=([^&#]+)""".toRegex().find(url)?.groupValues?.getOrNull(1)
            } else foundPasscode

            CourseZoomMeeting(
                courseId = courseId,
                title = defaultTitle,
                joinUrl = url,
                meetingId = idFromUrl,
                passcode = pwdFromUrl,
                origin = origin
            )
        }
    }
}

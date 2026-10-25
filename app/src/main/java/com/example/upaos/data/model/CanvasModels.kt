package com.example.upaos.data.model

import com.google.gson.annotations.SerializedName

data class CanvasTerm(
    val id: Long? = null,
    val name: String? = null
)

data class CanvasEnrollment(
    val type: String? = null,
    val role: String? = null
)

data class CanvasUser(
    val id: Long,
    val name: String?,
    @SerializedName("short_name") val shortName: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    val email: String? = null,
    val enrollments: List<CanvasEnrollment>? = emptyList()
) {
    val isTeacher: Boolean
        get() = enrollments?.any {
            it.type?.contains("Teacher", ignoreCase = true) == true ||
            it.role?.contains("Teacher", ignoreCase = true) == true ||
            it.role?.contains("Docente", ignoreCase = true) == true ||
            it.role?.contains("Profesor", ignoreCase = true) == true
        } == true
}

data class CanvasDiscussionTopic(
    val id: Long,
    val title: String?,
    val message: String? = null,
    @SerializedName("posted_at") val postedAt: String? = null,
    @SerializedName("user_name") val userName: String? = null,
    @SerializedName("discussion_subentry_count") val repliesCount: Int? = 0,
    @SerializedName("unread_count") val unreadCount: Int? = 0,
    @SerializedName("html_url") val htmlUrl: String? = null,
    val author: CanvasParticipant? = null
)

data class CanvasQuiz(
    val id: Long,
    val title: String?,
    @SerializedName("due_at") val dueAt: String? = null,
    @SerializedName("lock_at") val lockAt: String? = null,
    @SerializedName("unlock_at") val unlockAt: String? = null,
    @SerializedName("points_possible") val pointsPossible: Double? = null,
    @SerializedName("html_url") val htmlUrl: String? = null
) {
    val effectiveDueAt: String?
        get() = dueAt?.takeIf { it.isNotBlank() }
}

data class CanvasRubricRating(
    val id: String? = null,
    val points: Double? = null,
    val description: String? = null
)

data class CanvasRubricCriterion(
    val id: String? = null,
    val points: Double? = null,
    val description: String? = null,
    @SerializedName("long_description") val longDescription: String? = null,
    val ratings: List<CanvasRubricRating>? = emptyList()
)

data class CanvasSubmission(
    val id: Long? = null,
    @SerializedName("workflow_state") val workflowState: String? = null, // "unsubmitted", "submitted", "graded", "pending_review"
    @SerializedName("submitted_at") val submittedAt: String? = null,
    val attempt: Int? = null,
    val score: Double? = null,
    val grade: String? = null
) {
    val isSubmitted: Boolean
        get() = !submittedAt.isNullOrBlank() ||
                workflowState.equals("submitted", ignoreCase = true) ||
                workflowState.equals("graded", ignoreCase = true) ||
                workflowState.equals("pending_review", ignoreCase = true) ||
                (attempt != null && attempt > 0)
}

data class CanvasAssignmentDate(
    val id: Long? = null,
    @SerializedName("due_at") val dueAt: String? = null,
    @SerializedName("unlock_at") val unlockAt: String? = null,
    @SerializedName("lock_at") val lockAt: String? = null,
    val title: String? = null,
    @SerializedName("set_type") val setType: String? = null,
    val base: Boolean? = null
)

data class CanvasAssignment(
    val id: Long,
    val name: String?,
    val description: String? = null,
    @SerializedName("due_at") val dueAt: String? = null,
    @SerializedName("points_possible") val pointsPossible: Double? = null,
    @SerializedName("submission_types") val submissionTypes: List<String>? = emptyList(),
    @SerializedName("is_quiz_assignment") val isQuizAssignment: Boolean? = false,
    @SerializedName("quiz_id") val quizId: Long? = null,
    @SerializedName("locked_for_user") val lockedForUser: Boolean? = false,
    @SerializedName("lock_at") val lockAt: String? = null,
    @SerializedName("unlock_at") val unlockAt: String? = null,
    @SerializedName("all_dates") val allDates: List<CanvasAssignmentDate>? = emptyList(),
    @SerializedName("allowed_attempts") val allowedAttempts: Int? = null,
    val rubric: List<CanvasRubricCriterion>? = emptyList(),
    @SerializedName("html_url") val htmlUrl: String? = null,
    @SerializedName("has_submitted_submissions") val hasSubmittedSubmissions: Boolean? = false,
    val submission: CanvasSubmission? = null,
    @SerializedName("course_id") val courseId: Long? = null
) {
    val isSubmitted: Boolean
        get() = hasSubmittedSubmissions == true || submission?.isSubmitted == true

    val effectiveDueAt: String?
        get() = dueAt?.takeIf { it.isNotBlank() }
            ?: allDates?.singleOrNull { it.base == true }?.dueAt?.takeIf { it.isNotBlank() }

}

data class CanvasTodoItem(
    val type: String? = null,
    @SerializedName("context_type") val contextType: String? = null,
    @SerializedName("course_id") val courseId: Long? = null,
    @SerializedName("context_name") val contextName: String? = null,
    @SerializedName("context_short_name") val contextShortName: String? = null,
    @SerializedName("html_url") val htmlUrl: String? = null,
    val assignment: CanvasAssignment? = null,
    val quiz: CanvasQuiz? = null
) {
    val title: String
        get() = assignment?.name ?: quiz?.title ?: "Sin título"

    val dueAt: String?
        get() = assignment?.effectiveDueAt
            ?: quiz?.effectiveDueAt

    val courseDisplayName: String
        get() = contextName ?: contextShortName ?: "Curso"

    val isQuiz: Boolean
        get() = quiz != null ||
                assignment?.isQuizAssignment == true ||
                assignment?.quizId != null ||
                (assignment?.submissionTypes?.contains("online_quiz") == true)

    val pointsPossible: Double?
        get() = assignment?.pointsPossible ?: quiz?.pointsPossible

    val webUrl: String?
        get() = htmlUrl ?: assignment?.htmlUrl ?: quiz?.htmlUrl
}

data class CanvasCourse(
    val id: Long,
    val name: String,
    @SerializedName("course_code") val courseCode: String? = null,
    @SerializedName("workflow_state") val workflowState: String? = null,
    @SerializedName("syllabus_body") val syllabusBody: String? = null,
    val term: CanvasTerm? = null
) {
    val displayTitle: String
        get() {
            // Remueve " - T1 - Nrc 5045" para una vista más limpia si existe
            val regex = """\s*-\s*[A-Z0-9]+\s*-\s*Nrc\s*\d+""".toRegex(RegexOption.IGNORE_CASE)
            return name.replace(regex, "").trim()
        }

    val nrcNumber: String?
        get() {
            val match = """Nrc\s*(\d+)""".toRegex(RegexOption.IGNORE_CASE).find(name)
            return match?.groupValues?.getOrNull(1)
        }
}

data class CanvasParticipant(
    val id: Long,
    val name: String?,
    @SerializedName("full_name") val fullName: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null
)

data class CanvasConversationMessage(
    val id: Long,
    @SerializedName("created_at") val createdAt: String? = null,
    val body: String? = null,
    @SerializedName("author_id") val authorId: Long? = null
)

data class CanvasConversation(
    val id: Long,
    val subject: String?,
    @SerializedName("workflow_state") val workflowState: String? = null, // "read" or "unread"
    @SerializedName("last_message") val lastMessage: String? = null,
    @SerializedName("last_message_at") val lastMessageAt: String? = null,
    @SerializedName("message_count") val messageCount: Int? = 1,
    @SerializedName("context_name") val contextName: String? = null,
    val participants: List<CanvasParticipant>? = emptyList(),
    @SerializedName("audience") val audience: List<Long>? = emptyList(),
    val messages: List<CanvasConversationMessage>? = emptyList()
) {
    val authorName: String
        get() = participants?.firstOrNull()?.name ?: "Profesor / Canvas"

    val isUnread: Boolean
        get() = workflowState.equals("unread", ignoreCase = true)
}

data class CanvasFileDetail(
    val id: Long,
    @SerializedName("display_name") val displayName: String? = null,
    val filename: String? = null,
    val size: Long? = null,
    @SerializedName("content-type") val contentType: String? = null,
    val url: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
) {
    val fileName: String
        get() = displayName ?: filename ?: "Archivo"

    val formattedSize: String
        get() {
            val bytes = size ?: return ""
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                else -> String.format(java.util.Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
            }
        }
}

data class CanvasModuleItem(
    val id: Long,
    val title: String?,
    val type: String?, // "File", "SubHeader", "Discussion", "Assignment", "ExternalUrl", "Page"
    @SerializedName("html_url") val htmlUrl: String? = null,
    val url: String? = null,
    val position: Int? = 0,
    @SerializedName("indent") val indent: Int? = 0,
    @SerializedName("content_id") val contentId: Long? = null
) {
    val isHeader: Boolean
        get() = type.equals("SubHeader", ignoreCase = true)

    val isFile: Boolean
        get() = type.equals("File", ignoreCase = true)
}

data class CanvasModule(
    val id: Long,
    val name: String,
    val position: Int? = 0,
    val items: List<CanvasModuleItem>? = emptyList(),
    @SerializedName("items_count") val itemsCount: Int? = null
)

data class CanvasGroup(
    val id: Long,
    val name: String,
    val description: String? = null,
    @SerializedName("context_type") val contextType: String? = null, // "Course"
    @SerializedName("course_id") val courseId: Long? = null,
    @SerializedName("course_name") val courseName: String? = null,
    @SerializedName("members_count") val membersCount: Int? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("is_public") val isPublic: Boolean? = false,
    @SerializedName("followed_by_user") val followedByUser: Boolean? = false,
    @SerializedName("join_level") val joinLevel: String? = null
)

data class CanvasNotificationItem(
    val id: Long,
    val title: String?,
    val message: String? = null,
    val type: String? = null, // "DiscussionTopic", "Conversation", "Message", "Submission", "Announcement"
    @SerializedName("read_state") val readState: Boolean? = true,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("context_type") val contextType: String? = null,
    @SerializedName("course_id") val courseId: Long? = null,
    @SerializedName("course_name") val courseName: String? = null,
    @SerializedName("html_url") val htmlUrl: String? = null,
    @SerializedName("notification_category") val notificationCategory: String? = null
) {
    val isUnread: Boolean
        get() = readState == false
}

data class CanvasTab(
    val id: String,
    @SerializedName("html_url") val htmlUrl: String? = null,
    @SerializedName("full_url") val fullUrl: String? = null,
    val label: String? = null,
    val type: String? = null, // "internal", "external"
    val hidden: Boolean? = false,
    val visibility: String? = null,
    val position: Int? = null,
    val url: String? = null
)

data class CourseZoomMeeting(
    val courseId: Long,
    val title: String,
    val joinUrl: String,
    val meetingId: String? = null,
    val passcode: String? = null,
    val origin: String = "CANVAS_TAB", // "CANVAS_TAB", "MODULE", "ANNOUNCEMENT", "SYLLABUS", "CUSTOM"
    val description: String? = null
)



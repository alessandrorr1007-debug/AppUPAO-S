package com.example.upaos.data.model

import com.google.gson.annotations.SerializedName

data class CanvasAnnouncement(
    val id: Long? = null, val title: String? = null, val message: String? = null,
    @SerializedName("context_code") val contextCode: String? = null,
    @SerializedName("posted_at") val postedAt: String? = null,
    @SerializedName("html_url") val htmlUrl: String? = null,
    val attachments: List<CanvasFileDetail>? = null
)
data class CanvasSubmissionComment(
    val id: Long? = null, @SerializedName("author_name") val authorName: String? = null,
    val comment: String? = null, @SerializedName("created_at") val createdAt: String? = null,
    val attachments: List<CanvasFileDetail>? = null
)
data class CanvasRubricAssessment(
    val points: Double? = null, val comments: String? = null,
    @SerializedName("rating_id") val ratingId: String? = null
)
data class CanvasGrades(
    @SerializedName("current_score") val currentScore: Double? = null,
    @SerializedName("current_grade") val currentGrade: String? = null,
    @SerializedName("final_score") val finalScore: Double? = null,
    @SerializedName("final_grade") val finalGrade: String? = null
)
data class CanvasCourseEnrollment(val id: Long? = null, val grades: CanvasGrades? = null)
data class CanvasAssignmentGroup(
    val id: Long? = null, val name: String? = null,
    @SerializedName("group_weight") val groupWeight: Double? = null,
    val assignments: List<CanvasAssignment>? = null
)
data class CanvasCalendarEvent(
    val id: String? = null, val title: String? = null, val description: String? = null,
    @SerializedName("start_at") val startAt: String? = null,
    @SerializedName("end_at") val endAt: String? = null,
    @SerializedName("context_code") val contextCode: String? = null,
    @SerializedName("html_url") val htmlUrl: String? = null,
    val assignment: CanvasAssignment? = null,
    @SerializedName("location_name") val locationName: String? = null,
    @SerializedName("location_address") val locationAddress: String? = null
)
data class CanvasPage(
    @SerializedName("page_id") val pageId: Long? = null, val url: String? = null,
    val title: String? = null, val body: String? = null,
    @SerializedName("html_url") val htmlUrl: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)
data class CanvasQuizAttempt(
    val id: Long? = null, val attempt: Int? = null, val score: Double? = null,
    @SerializedName("kept_score") val keptScore: Double? = null,
    @SerializedName("started_at") val startedAt: String? = null,
    @SerializedName("finished_at") val finishedAt: String? = null,
    @SerializedName("end_at") val endAt: String? = null,
    @SerializedName("workflow_state") val workflowState: String? = null
)
data class CanvasQuizSubmissions(
    @SerializedName("quiz_submissions") val submissions: List<CanvasQuizAttempt>? = null
)
data class CanvasDiscussionEntry(
    val id: Long? = null, @SerializedName("user_id") val userId: Long? = null,
    val message: String? = null, @SerializedName("created_at") val createdAt: String? = null,
    val replies: List<CanvasDiscussionEntry>? = null, val deleted: Boolean? = null,
    @SerializedName("has_more_replies") val hasMoreReplies: Boolean? = null
)
data class CanvasDiscussionView(
    val participants: List<CanvasParticipant>? = null, val view: List<CanvasDiscussionEntry>? = null,
    @SerializedName("unread_entries") val unreadEntries: List<Long>? = null
)
data class CanvasFolder(
    val id: Long? = null, val name: String? = null,
    @SerializedName("parent_folder_id") val parentFolderId: Long? = null,
    @SerializedName("files_count") val filesCount: Int? = null
)
data class CanvasCompletionRequirement(
    val type: String? = null, val completed: Boolean? = null,
    @SerializedName("min_score") val minScore: Double? = null
)

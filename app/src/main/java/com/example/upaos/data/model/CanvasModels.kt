package com.example.upaos.data.model

import com.google.gson.annotations.SerializedName

data class CanvasUser(
    val id: Long,
    val name: String?,
    @SerializedName("short_name") val shortName: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null
)

data class CanvasQuiz(
    val id: Long,
    val title: String?,
    @SerializedName("due_at") val dueAt: String? = null,
    @SerializedName("points_possible") val pointsPossible: Double? = null,
    @SerializedName("html_url") val htmlUrl: String? = null
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
    @SerializedName("html_url") val htmlUrl: String? = null,
    @SerializedName("has_submitted_submissions") val hasSubmittedSubmissions: Boolean? = false
)

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
        get() = assignment?.dueAt ?: quiz?.dueAt

    val courseDisplayName: String
        get() {
            val name = contextName ?: contextShortName ?: "Curso"
            // Limpia texto largo si tiene formato "CURSO - T1 - Nrc 1234"
            return name
        }

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

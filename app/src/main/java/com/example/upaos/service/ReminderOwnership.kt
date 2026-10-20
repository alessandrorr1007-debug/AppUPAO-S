package com.example.upaos.service

/** Keys are exact: a missing schedule never falls back to another account or term. */
fun reminderScheduleKey(user: String, term: String?): String? =
    if (user.matches(Regex("[0-9]{9}")) && term != null && term.matches(Regex("[0-9]{6}")) && !term.endsWith("90"))
        "horario_${user}_$term" else null

data class ReminderOwner(val user: String, val sessionId: String) {
    fun matches(currentUser: String?, currentSession: String?): Boolean =
        user.isNotBlank() && sessionId.isNotBlank() && user == currentUser && sessionId == currentSession
    fun identity(id: Int): String = "upaos://class/$user/$sessionId/$id"
}

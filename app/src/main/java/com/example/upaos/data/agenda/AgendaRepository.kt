package com.example.upaos.data.agenda

import android.content.Context
import com.example.upaos.data.api.CanvasApiClient
import com.example.upaos.data.local.ApiCache
import com.example.upaos.data.local.TokenManager
import com.example.upaos.data.model.CanvasCourse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Locale

data class AgendaItem(
    val id: Long, val courseId: Long, val course: String, val title: String,
    val dueAt: String?, val submitted: Boolean, val graded: Boolean, val url: String?
) {
    val key: String get() = "$courseId:$id"
    val dueMillis: Long? get() = parseDeadline(dueAt)
    fun status(now: Long): String = when {
        graded -> "Calificada"
        submitted -> "Entregada"
        dueMillis != null && dueMillis!! < now -> "Vencida · sin entregar"
        else -> "Pendiente"
    }
}

fun parseDeadline(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    for (pattern in listOf("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX")) {
        try {
            val format = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
            val position = java.text.ParsePosition(0)
            val parsed = format.parse(value, position)
            if (parsed != null && position.index == value.length) return parsed.time
        } catch (_: IllegalArgumentException) { }
    }
    return null
}

fun parseServerDate(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    val normalized = Regex("\\.(\\d{1,9})").replace(value) { "." + it.groupValues[1].padEnd(3, '0').take(3) }
    val withZone = if (Regex("(Z|[+-]\\d{2}:\\d{2})$").containsMatchIn(normalized)) normalized else "${normalized}Z"
    return parseDeadline(withZone)
}

fun prioritizeAgenda(items: List<AgendaItem>, now: Long = System.currentTimeMillis()): List<AgendaItem> = items.distinctBy { it.key }
    .sortedWith(compareBy<AgendaItem> {
        when {
            it.submitted || it.graded -> 3
            it.dueMillis == null -> 2
            it.dueMillis!! < now -> 1
            else -> 0
        }
    }.thenBy { it.dueMillis ?: Long.MAX_VALUE }.thenBy { it.title })

class AgendaRepository(context: Context) {
    private val cache = ApiCache(context.applicationContext)
    private val tokens = TokenManager(context.applicationContext)
    private val gson = Gson()
    suspend fun cached(user: String): Pair<List<AgendaItem>, Long?> {
        val key = "agenda_${user.trim().lowercase()}"
        val items = try {
            cache.cargar(key)?.let { gson.fromJson<List<AgendaItem>>(it, object : TypeToken<List<AgendaItem>>() {}.type) } ?: emptyList()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { android.util.Log.w("Agenda", "Caché de agenda inválida"); emptyList() }
        return prioritizeAgenda(items) to cache.actualizado(key)
    }

    suspend fun refresh(user: String): List<AgendaItem> = lock.withLock {
        val canvasToken = tokens.getCanvasToken(user)?.takeIf { it.isNotBlank() }
            ?: error("Conecta tu cuenta Canvas desde la pestaña Canvas para cargar tus entregas.")
        val auth = "Bearer $canvasToken"
        val api = CanvasApiClient.pagedApiService
        val courses = api.getActiveCourses(auth).body() ?: error("Canvas devolvió una respuesta vacía.")
        val items = mutableListOf<AgendaItem>()
        for (course in courses.distinctBy { it.id }) {
            val assignments = api.getCourseAssignments(auth, course.id).body() ?: error("Canvas devolvió una respuesta vacía.")
            for (a in assignments) {
                items += AgendaItem(a.id, course.id, course.displayTitle, a.name ?: "Entrega", a.effectiveDueAt,
                    a.submission?.isSubmitted == true, a.submission?.workflowState == "graded", a.htmlUrl)
            }
        }
        // Never publish a partial refresh or repopulate an account deleted during the request.
        check(tokens.getCanvasToken(user) == canvasToken) { "La cuenta de Canvas cambió. Actualiza de nuevo." }
        val sorted = prioritizeAgenda(items)
        cache.guardar("agenda_${user.trim().lowercase()}", gson.toJson(sorted))
        sorted
    }
    companion object { private val lock = Mutex() }
}

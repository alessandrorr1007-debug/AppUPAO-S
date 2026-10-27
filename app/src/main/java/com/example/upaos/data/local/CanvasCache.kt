package com.example.upaos.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.upaos.data.model.CanvasConversation
import com.example.upaos.data.model.CanvasCourse
import com.example.upaos.data.model.CanvasGroup
import com.example.upaos.data.model.CanvasNotificationItem
import com.example.upaos.data.model.CanvasTodoItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first

private val Context.canvasDataStore by preferencesDataStore("canvas_cache")

class CanvasCache(private val context: Context) : CanvasCacheStore {
    private val gson = Gson()

    private fun jsonKey(user: String) = stringPreferencesKey("canvas_todo_${user.trim().lowercase()}")
    private fun timeKey(user: String) = longPreferencesKey("canvas_time_${user.trim().lowercase()}")
    private fun convKey(user: String) = stringPreferencesKey("canvas_conv_${user.trim().lowercase()}")
    private fun coursesKey(user: String) = stringPreferencesKey("canvas_courses_${user.trim().lowercase()}")
    private fun notifsKey(user: String) = stringPreferencesKey("canvas_notifs_${user.trim().lowercase()}")
    private fun groupsKey(user: String) = stringPreferencesKey("canvas_groups_${user.trim().lowercase()}")

    override suspend fun guardar(user: String, items: List<CanvasTodoItem>) {
        if (AccountPrivacy.isDeleting(user) || !TokenManager(context).containsAccount(user)) return
        val json = gson.toJson(items)
        context.canvasDataStore.edit { prefs ->
            prefs[jsonKey(user)] = json
            prefs[timeKey(user)] = System.currentTimeMillis()
        }
    }

    override suspend fun cargar(user: String): Pair<List<CanvasTodoItem>, Long?> {
        val prefs = context.canvasDataStore.data.first()
        val json = prefs[jsonKey(user)]
        val timestamp = prefs[timeKey(user)]

        if (json.isNullOrBlank()) {
            return Pair(emptyList(), null)
        }

        return try {
            val type = object : TypeToken<List<CanvasTodoItem>>() {}.type
            val list = gson.fromJson<List<CanvasTodoItem>>(json, type) ?: emptyList()
            Pair(list, timestamp)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            android.util.Log.w("CanvasCache", "Caché de Canvas inválida")
            Pair(emptyList(), null)
        }
    }

    override suspend fun guardarConversaciones(user: String, convs: List<CanvasConversation>) {
        if (AccountPrivacy.isDeleting(user) || !TokenManager(context).containsAccount(user)) return
        val json = gson.toJson(convs)
        context.canvasDataStore.edit { prefs ->
            prefs[convKey(user)] = json
        }
    }

    override suspend fun cargarConversaciones(user: String): List<CanvasConversation> {
        val prefs = context.canvasDataStore.data.first()
        val json = prefs[convKey(user)] ?: return emptyList()
        return try {
            val type = object : TypeToken<List<CanvasConversation>>() {}.type
            gson.fromJson<List<CanvasConversation>>(json, type) ?: emptyList()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            android.util.Log.w("CanvasCache", "Caché de Canvas inválida")
            emptyList()
        }
    }

    override suspend fun guardarCursos(user: String, courses: List<CanvasCourse>) {
        if (AccountPrivacy.isDeleting(user) || !TokenManager(context).containsAccount(user)) return
        val json = gson.toJson(courses)
        context.canvasDataStore.edit { prefs ->
            prefs[coursesKey(user)] = json
        }
    }

    override suspend fun cargarCursos(user: String): List<CanvasCourse> {
        val prefs = context.canvasDataStore.data.first()
        val json = prefs[coursesKey(user)] ?: return emptyList()
        return try {
            val type = object : TypeToken<List<CanvasCourse>>() {}.type
            gson.fromJson<List<CanvasCourse>>(json, type) ?: emptyList()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            android.util.Log.w("CanvasCache", "Caché de Canvas inválida")
            emptyList()
        }
    }

    override suspend fun guardarNotificaciones(user: String, notifs: List<CanvasNotificationItem>) {
        if (AccountPrivacy.isDeleting(user) || !TokenManager(context).containsAccount(user)) return
        val json = gson.toJson(notifs)
        context.canvasDataStore.edit { prefs ->
            prefs[notifsKey(user)] = json
        }
    }

    override suspend fun cargarNotificaciones(user: String): List<CanvasNotificationItem> {
        val prefs = context.canvasDataStore.data.first()
        val json = prefs[notifsKey(user)] ?: return emptyList()
        return try {
            val type = object : TypeToken<List<CanvasNotificationItem>>() {}.type
            gson.fromJson<List<CanvasNotificationItem>>(json, type) ?: emptyList()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            android.util.Log.w("CanvasCache", "Caché de Canvas inválida")
            emptyList()
        }
    }

    override suspend fun guardarGrupos(user: String, groups: List<CanvasGroup>) {
        if (AccountPrivacy.isDeleting(user) || !TokenManager(context).containsAccount(user)) return
        val json = gson.toJson(groups)
        context.canvasDataStore.edit { prefs ->
            prefs[groupsKey(user)] = json
        }
    }

    override suspend fun cargarGrupos(user: String): List<CanvasGroup> {
        val prefs = context.canvasDataStore.data.first()
        val json = prefs[groupsKey(user)] ?: return emptyList()
        return try {
            val type = object : TypeToken<List<CanvasGroup>>() {}.type
            gson.fromJson<List<CanvasGroup>>(json, type) ?: emptyList()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            android.util.Log.w("CanvasCache", "Caché de Canvas inválida")
            emptyList()
        }
    }

    override suspend fun guardarPendientesCurso(user: String, courseId: Long, items: List<CanvasTodoItem>) {
        if (AccountPrivacy.isDeleting(user) || TokenManager(context).getSavedUser() != user) return
        context.canvasDataStore.edit { prefs ->
            prefs[stringPreferencesKey("canvas_course_todo_${user}_${courseId}")] = gson.toJson(items)
        }
    }

    override suspend fun cargarPendientesPorCurso(user: String): Map<Long, List<CanvasTodoItem>> {
        val prefix = "canvas_course_todo_${user}_"
        val result = mutableMapOf<Long, List<CanvasTodoItem>>()
        val preferences = context.canvasDataStore.data.first()
        for ((key, value) in preferences.asMap()) {
            if (!key.name.startsWith(prefix)) continue
            val id = key.name.removePrefix(prefix).toLongOrNull() ?: continue
            try {
                val type = object : TypeToken<List<CanvasTodoItem>>() {}.type
                val items: List<CanvasTodoItem> = gson.fromJson(value as String, type) ?: emptyList()
                result[id] = items
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { android.util.Log.w("CanvasCache", "Caché de pendientes de curso inválida") }
        }
        return result
    }

    suspend fun readCanvasList(user: String, key: String): String? =
        context.canvasDataStore.data.first()[stringPreferencesKey("canvas_read_${user.trim().lowercase()}_$key")]

    suspend fun saveCanvasList(user: String, key: String, json: String) {
        if (AccountPrivacy.isDeleting(user) || TokenManager(context).getSavedUser() != user) return
        context.canvasDataStore.edit { it[stringPreferencesKey("canvas_read_${user.trim().lowercase()}_$key")] = json }
    }

    suspend fun seenAnnouncements(user: String): Set<String> =
        context.canvasDataStore.data.first()[androidx.datastore.preferences.core.stringSetPreferencesKey("canvas_seen_${user.trim().lowercase()}")] ?: emptySet()

    suspend fun markAnnouncementSeen(user: String, id: String) {
        if (AccountPrivacy.isDeleting(user) || TokenManager(context).getSavedUser() != user) return
        val key = androidx.datastore.preferences.core.stringSetPreferencesKey("canvas_seen_${user.trim().lowercase()}")
        context.canvasDataStore.edit { it[key] = (it[key] ?: emptySet()) + id }
    }

    override suspend fun limpiar(user: String) {
        context.canvasDataStore.edit { prefs ->
            prefs.asMap().keys.filter { it.name.startsWith("canvas_course_todo_${user}_") }.forEach { prefs.remove(it) }
            prefs.asMap().keys.filter { it.name.startsWith("canvas_read_${user.trim().lowercase()}_") || it.name == "canvas_seen_${user.trim().lowercase()}" }.forEach { prefs.remove(it) }
            prefs.remove(jsonKey(user))
            prefs.remove(timeKey(user))
            prefs.remove(convKey(user))
            prefs.remove(coursesKey(user))
            prefs.remove(notifsKey(user))
            prefs.remove(groupsKey(user))
        }
    }
}

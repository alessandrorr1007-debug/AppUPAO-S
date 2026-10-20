package com.example.upaos.ui.canvas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.upaos.data.canvas.*
import com.example.upaos.data.model.*
import com.example.upaos.data.refresh.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CanvasHubState(
    val courses: List<CanvasCourse> = emptyList(),
    val isLoadingCourses: Boolean = false,
    val isRefreshingCourses: Boolean = false,
    val items: List<CanvasTodoItem> = emptyList(),
    val isLoadingTodo: Boolean = false,
    val isRefreshingTodo: Boolean = false,
    val lastUpdatedMillis: Long? = null,
    val conversations: List<CanvasConversation> = emptyList(),
    val isLoadingConvs: Boolean = false,
    val isRefreshingConvs: Boolean = false,
    val notifications: List<CanvasNotificationItem> = emptyList(),
    val isLoadingNotifs: Boolean = false,
    val isRefreshingNotifs: Boolean = false,
    val groups: List<CanvasGroup> = emptyList(),
    val isLoadingGroups: Boolean = false,
    val isRefreshingGroups: Boolean = false,
    val errorMessage: String? = null,
    val isTokenExpired: Boolean = false,
    val isOffline: Boolean = false,
    val completeTodoSnapshot: Boolean = false,
    val refreshFailures: Map<String, String> = emptyMap()
)

class CanvasHubViewModel(
    private val userKey: String,
    private val canvasToken: String,
    private val repository: CanvasHubRepository
) : ViewModel() {
    private val cache get() = repository.cache
    private val mutable = MutableStateFlow(CanvasHubState())
    val state = mutable.asStateFlow()
    private var courses: List<CanvasCourse>
        get() = mutable.value.courses
        set(value) { mutable.value = mutable.value.copy(courses = value) }

    private var isLoadingCourses: Boolean
        get() = mutable.value.isLoadingCourses
        set(value) { mutable.value = mutable.value.copy(isLoadingCourses = value) }

    private var isRefreshingCourses: Boolean
        get() = mutable.value.isRefreshingCourses
        set(value) { mutable.value = mutable.value.copy(isRefreshingCourses = value) }

    private var items: List<CanvasTodoItem>
        get() = mutable.value.items
        set(value) { mutable.value = mutable.value.copy(items = value) }

    private var isLoadingTodo: Boolean
        get() = mutable.value.isLoadingTodo
        set(value) { mutable.value = mutable.value.copy(isLoadingTodo = value) }

    private var isRefreshingTodo: Boolean
        get() = mutable.value.isRefreshingTodo
        set(value) { mutable.value = mutable.value.copy(isRefreshingTodo = value) }

    private var lastUpdatedMillis: Long?
        get() = mutable.value.lastUpdatedMillis
        set(value) { mutable.value = mutable.value.copy(lastUpdatedMillis = value) }

    private var conversations: List<CanvasConversation>
        get() = mutable.value.conversations
        set(value) { mutable.value = mutable.value.copy(conversations = value) }

    private var isLoadingConvs: Boolean
        get() = mutable.value.isLoadingConvs
        set(value) { mutable.value = mutable.value.copy(isLoadingConvs = value) }

    private var isRefreshingConvs: Boolean
        get() = mutable.value.isRefreshingConvs
        set(value) { mutable.value = mutable.value.copy(isRefreshingConvs = value) }

    private var notifications: List<CanvasNotificationItem>
        get() = mutable.value.notifications
        set(value) { mutable.value = mutable.value.copy(notifications = value) }

    private var isLoadingNotifs: Boolean
        get() = mutable.value.isLoadingNotifs
        set(value) { mutable.value = mutable.value.copy(isLoadingNotifs = value) }

    private var isRefreshingNotifs: Boolean
        get() = mutable.value.isRefreshingNotifs
        set(value) { mutable.value = mutable.value.copy(isRefreshingNotifs = value) }

    private var groups: List<CanvasGroup>
        get() = mutable.value.groups
        set(value) { mutable.value = mutable.value.copy(groups = value) }

    private var isLoadingGroups: Boolean
        get() = mutable.value.isLoadingGroups
        set(value) { mutable.value = mutable.value.copy(isLoadingGroups = value) }

    private var isRefreshingGroups: Boolean
        get() = mutable.value.isRefreshingGroups
        set(value) { mutable.value = mutable.value.copy(isRefreshingGroups = value) }

    private var errorMessage: String?
        get() = mutable.value.errorMessage
        set(value) { mutable.value = mutable.value.copy(errorMessage = value) }

    private var isTokenExpired: Boolean
        get() = mutable.value.isTokenExpired
        set(value) { mutable.value = mutable.value.copy(isTokenExpired = value) }

    private var isOffline: Boolean
        get() = mutable.value.isOffline
        set(value) { mutable.value = mutable.value.copy(isOffline = value) }

    private var completeTodoSnapshot: Boolean
        get() = mutable.value.completeTodoSnapshot
        set(value) { mutable.value = mutable.value.copy(completeTodoSnapshot = value) }

    private var refreshFailures: Map<String, String>
        get() = mutable.value.refreshFailures
        set(value) { mutable.value = mutable.value.copy(refreshFailures = value) }
    private fun markRefreshFailure(source: String, message: String) {
        refreshFailures = refreshFailures + (source to message)
        isOffline = true
        errorMessage = refreshFailures.values.joinToString(" · ")
    }
    private fun clearRefreshFailure(source: String) {
        refreshFailures = refreshFailures - source
        isOffline = !completeTodoSnapshot || refreshFailures.isNotEmpty()
        errorMessage = refreshFailures.values.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private fun sortItems(list: List<CanvasTodoItem>): List<CanvasTodoItem> {
        val now = System.currentTimeMillis()
        return list.sortedWith(
            compareBy<CanvasTodoItem> { item ->
                val dueMillis = com.example.upaos.data.agenda.parseServerDate(item.dueAt)
                when {
                    dueMillis == null -> 2 // Sin fecha: después de las próximas
                    dueMillis >= now -> 1 // Próximas a vencer: primero
                    else -> 3 // Vencidas: al final
                }
            }.thenBy { item ->
                com.example.upaos.data.agenda.parseServerDate(item.dueAt) ?: Long.MAX_VALUE
            }.thenBy { it.title }
        )
    }

    private var todoFetchInProgress = false
    fun fetchTodo(isManual: Boolean = false) {
        val currentToken = canvasToken.takeIf { it.isNotBlank() } ?: return
        if (todoFetchInProgress) return
        todoFetchInProgress = true
        viewModelScope.launch {
            if (isManual) isRefreshingTodo = true else if (items.isEmpty()) isLoadingTodo = true
            try {
                val previous = items
                val result = repository.todos(previous)
                repository.requireSession()
                val snapshot = when (result) {
                    is RefreshResult.Complete -> result.value
                    is RefreshResult.Partial -> result.value
                    is RefreshResult.Error -> null
                }
                if (snapshot != null) {
                    cache.guardarCursos(userKey, snapshot.courses)
                    for (id in snapshot.completedCourses) cache.guardarPendientesCurso(userKey, id, snapshot.byCourse.getValue(id))
                    items = sortItems(snapshot.items)
                    courses = snapshot.courses
                }
                when (result) {
                    is RefreshResult.Complete -> {
                        publishComplete(result) { cache.guardar(userKey, sortItems(it.items)) }
                        lastUpdatedMillis = System.currentTimeMillis()
                        completeTodoSnapshot = true
                        clearRefreshFailure("cache")
                        clearRefreshFailure("todo")
                        clearRefreshFailure("courses")
                    }
                    is RefreshResult.Partial -> {
                        isOffline = true
                        markRefreshFailure("todo", "Actualización incompleta: " + result.issues.joinToString { it.source } + ". Se conservan los últimos datos válidos.")
                        if (result.issues.any { it.cause.isCanvasUnauthorized() }) isTokenExpired = true
                    }
                    is RefreshResult.Error -> {
                        isOffline = true
                        markRefreshFailure("todo", "No se pudo actualizar " + result.issue.source + ". Se conservan los datos guardados.")
                        if (result.issue.cause.isCanvasUnauthorized()) isTokenExpired = true
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                isOffline = true
                markRefreshFailure("todo", "No se pudo completar la actualización de pendientes. Se conservan los datos guardados.")
            } finally {
                todoFetchInProgress = false
                isLoadingTodo = false
                isRefreshingTodo = false
            }
        }
    }

    fun fetchConversations(isManual: Boolean = false) {
        if (isLoadingConvs || isRefreshingConvs) return
        val currentToken = canvasToken.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            if (isManual) isRefreshingConvs = true else if (conversations.isEmpty()) isLoadingConvs = true
            try {
                val data = repository.conversations()
                repository.requireSession()
                cache.guardarConversaciones(userKey, data)
                conversations = data
                clearRefreshFailure("conversations")
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                markRefreshFailure("conversations", "No se pudo actualizar la bandeja de entrada. Se conservan los datos guardados.")
                if (e.isCanvasUnauthorized()) isTokenExpired = true
            } finally { isLoadingConvs = false; isRefreshingConvs = false }
        }
    }

    fun fetchNotifications(isManual: Boolean = false) {
        if (isLoadingNotifs || isRefreshingNotifs) return
        val currentToken = canvasToken.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            if (isManual) isRefreshingNotifs = true else if (notifications.isEmpty()) isLoadingNotifs = true
            try {
                val data = repository.notifications()
                repository.requireSession()
                cache.guardarNotificaciones(userKey, data)
                notifications = data
                clearRefreshFailure("notifications")
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                markRefreshFailure("notifications", "No se pudieron actualizar las notificaciones. Se conservan los datos guardados.")
                if (e.isCanvasUnauthorized()) isTokenExpired = true
            } finally { isLoadingNotifs = false; isRefreshingNotifs = false }
        }
    }

    fun fetchGroups(isManual: Boolean = false) {
        if (isLoadingGroups || isRefreshingGroups) return
        val currentToken = canvasToken.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            if (isManual) isRefreshingGroups = true else if (groups.isEmpty()) isLoadingGroups = true
            try {
                val data = repository.groups()
                repository.requireSession()
                cache.guardarGrupos(userKey, data)
                groups = data
                clearRefreshFailure("groups")
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                markRefreshFailure("groups", "No se pudieron actualizar los grupos. Se conservan los datos guardados.")
                if (e.isCanvasUnauthorized()) isTokenExpired = true
            } finally { isLoadingGroups = false; isRefreshingGroups = false }
        }
    }

    fun fetchCourses(isManual: Boolean = false) {
        if (isLoadingCourses || isRefreshingCourses) return
        val currentToken = canvasToken.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            if (isManual) isRefreshingCourses = true else if (courses.isEmpty()) isLoadingCourses = true
            try {
                val cleanCourses = repository.courses()
                repository.requireSession()
                cache.guardarCursos(userKey, cleanCourses)
                courses = cleanCourses
                clearRefreshFailure("courses")
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                isOffline = true
                markRefreshFailure("courses", "No se pudieron actualizar los cursos. Se conservan los datos guardados.")
                if (e.isCanvasUnauthorized()) isTokenExpired = true
            } finally {
                isLoadingCourses = false
                isRefreshingCourses = false
            }
        }
    }

    init {
        if (canvasToken.isNotBlank()) viewModelScope.launch {
            try {
            // Cargar caché local
            val (cachedTodo, cachedTime) = cache.cargar(userKey)
            val cachedCourses = cache.cargarCursos(userKey)
            val cachedByCourse = cache.cargarPendientesPorCurso(userKey)
            val restoredTodos = restoreCanvasTodos(cachedTodo, cachedByCourse, cachedCourses.map { it.id }.toSet())
            if (restoredTodos.isNotEmpty() || cachedTime != null) {
                items = sortItems(restoredTodos)
                lastUpdatedMillis = cachedTime
                isOffline = true
            }
            val cachedConvs = cache.cargarConversaciones(userKey)
            if (cachedConvs.isNotEmpty()) {
                conversations = cachedConvs
            }
            val cachedNotifs = cache.cargarNotificaciones(userKey)
            if (cachedNotifs.isNotEmpty()) {
                notifications = cachedNotifs
            }
            val cachedGroups = cache.cargarGrupos(userKey)
            if (cachedGroups.isNotEmpty()) {
                groups = cachedGroups
            }
            if (cachedCourses.isNotEmpty()) {
                courses = cachedCourses
            }

            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { markRefreshFailure("cache", "No se pudieron cargar algunos datos guardados de Canvas.") }
            repository.requireSession()
            fetchCourses()
            fetchTodo()
            fetchConversations()
            fetchNotifications()
            fetchGroups()
        }
    }
}

package com.example.upaos.data.api

import com.example.upaos.data.model.*
import com.google.gson.Gson
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** RFC-style Link relation parsing; URLs are opaque and are never reconstructed from page numbers. */
private fun splitLinkParts(value: String, delimiter: Char): List<String> {
    val parts = mutableListOf<String>()
    val part = StringBuilder()
    var target = false
    var quoted = false
    var escaped = false
    for (char in value) {
        if (escaped) { part.append(char); escaped = false; continue }
        if (quoted && char == '\\') { part.append(char); escaped = true; continue }
        if (!target && char == '"') quoted = !quoted
        if (!quoted) {
            if (char == '<') { if (target) throw IOException("Header Link inválido"); target = true }
            if (char == '>') { if (!target) throw IOException("Header Link inválido"); target = false }
        }
        if (char == delimiter && !target && !quoted) { parts += part.toString(); part.clear() }
        else part.append(char)
    }
    if (target || quoted || escaped) throw IOException("Header Link incompleto")
    parts += part.toString()
    return parts
}

internal fun canvasNextLink(headers: List<String>): String? {
    val targets = mutableListOf<String>()
    for (header in headers.filter { it.isNotBlank() }) {
        for (part in splitLinkParts(header, ',')) {
            val link = Regex("""^\s*<([^>]+)>\s*((?:;.*)?)$""").matchEntire(part.trim())
                ?: throw IOException("Header Link de Canvas inválido")
            var relation: String? = null
            for (attribute in splitLinkParts(link.groupValues[2], ';').filter { it.isNotBlank() }) {
                val separator = attribute.indexOf('=')
                if (separator < 0) {
                    if (attribute.trim().equals("rel", ignoreCase = true)) throw IOException("Relación Link sin valor")
                    continue
                }
                if (!attribute.substring(0, separator).trim().equals("rel", ignoreCase = true)) continue
                if (relation != null) throw IOException("Relación Link duplicada")
                relation = attribute.substring(separator + 1).trim().removeSurrounding("\"")
                if (relation.isBlank()) throw IOException("Relación Link sin valor")
            }
            if (relation.orEmpty().split(Regex("""\s+""")).any { it.equals("next", ignoreCase = true) }) targets += link.groupValues[1]
        }
    }
    if (targets.size > 1) throw IOException("Canvas devolvió más de un enlace next")
    return targets.singleOrNull()
}

private fun isCanvasApiPath(url: HttpUrl) = url.encodedPath.startsWith("/api/v1/")

private fun sameOrigin(a: HttpUrl, b: HttpUrl) = a.scheme == b.scheme && a.host == b.host && a.port == b.port &&
    b.username.isEmpty() && b.password.isEmpty()

internal suspend fun <T> collectCanvasPages(
    origin: HttpUrl,
    firstPage: suspend () -> Response<List<T>>,
    nextPage: suspend (String) -> Response<List<T>>,
    maxPages: Int = 100
): Response<List<T>> {
    require(maxPages > 0)
    var response = firstPage()
    val first = response.raw().request.url
    if (!sameOrigin(origin, first) || !isCanvasApiPath(first)) { response.errorBody()?.close(); throw IOException("Origen de Canvas no autorizado") }
    val firstRaw = response.raw()
    val seen = mutableSetOf(first.toString())
    val items = mutableListOf<T>()
    var pages = 0
    while (true) {
        val actual = response.raw().request.url
        if (!sameOrigin(origin, actual) || !isCanvasApiPath(actual)) {
            response.errorBody()?.close()
            throw IOException("Redirección de paginación no autorizada")
        }
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            throw HttpException(response)
        }
        items += response.body() ?: throw IOException("Página de Canvas sin contenido")
        pages++
        val target = canvasNextLink(response.headers().values("Link")) ?: break
        if (pages >= maxPages) throw IOException("La lista de Canvas supera el límite de páginas")
        val next = actual.resolve(target) ?: throw IOException("Enlace de paginación inválido")
        if (!sameOrigin(origin, next) || !isCanvasApiPath(next))
            throw IOException("Destino de paginación no autorizado")
        if (!seen.add(next.toString())) throw IOException("Bucle en la paginación de Canvas")
        response = nextPage(next.toString())
    }
    // The returned body is complete, so consumers must not see a stale first-page next header.
    return Response.success(items, firstRaw.newBuilder().removeHeader("Link").build())
}

/** All list consumers share the same all-or-error pagination contract. Other API methods delegate unchanged. */
class CanvasPagedApi(
    private val delegate: CanvasApiService,
    private val origin: HttpUrl = "https://upao.instructure.com/".toHttpUrl(),
    private val gson: Gson = Gson()
) : CanvasApiService by delegate {
    private suspend inline fun <reified T> all(auth: String, noinline first: suspend () -> Response<List<T>>): Response<List<T>> =
        collectCanvasPages(origin, first, { url ->
            val raw = delegate.getListPage(auth, url)
            if (!raw.isSuccessful) Response.error(raw.errorBody() ?: "".toResponseBody(), raw.raw())
            else {
                val json = raw.body() ?: throw IOException("Página de Canvas sin contenido")
                val items = json.map { gson.fromJson(it, T::class.java) ?: throw IOException("Registro de Canvas inválido") }
                Response.success(items, raw.raw())
            }
        })


    override suspend fun getAnnouncements(auth: String, contexts: List<String>, start: String, end: String, active: Boolean, perPage: Int) =
        all(auth) { delegate.getAnnouncements(auth, contexts, start, end, active, perPage) }
    override suspend fun getEnrollments(auth: String, course: Long, user: String, type: String, perPage: Int) =
        all(auth) { delegate.getEnrollments(auth, course, user, type, perPage) }
    override suspend fun getAssignmentGroups(auth: String, course: Long, include: List<String>, perPage: Int) =
        all(auth) { delegate.getAssignmentGroups(auth, course, include, perPage) }
    override suspend fun getCalendarEvents(auth: String, type: String, contexts: List<String>, start: String, end: String, perPage: Int) =
        all(auth) { delegate.getCalendarEvents(auth, type, contexts, start, end, perPage) }
    override suspend fun getPages(auth: String, course: Long, sort: String, perPage: Int) = all(auth) { delegate.getPages(auth, course, sort, perPage) }
    override suspend fun getQuizzes(auth: String, course: Long, perPage: Int) = all(auth) { delegate.getQuizzes(auth, course, perPage) }
    override suspend fun getFolders(auth: String, course: Long, perPage: Int) = all(auth) { delegate.getFolders(auth, course, perPage) }
    override suspend fun getFolderFiles(auth: String, folder: Long, perPage: Int) = all(auth) { delegate.getFolderFiles(auth, folder, perPage) }
    override suspend fun getQuizSubmissions(auth: String, course: Long, quiz: Long, perPage: Int): Response<CanvasQuizSubmissions> {
        fun unwrap(response: Response<CanvasQuizSubmissions>): Response<List<CanvasQuizAttempt>> {
            if (!response.isSuccessful) throw HttpException(response)
            return Response.success(response.body()?.submissions ?: emptyList(), response.raw())
        }
        val result = collectCanvasPages(origin,
            { unwrap(delegate.getQuizSubmissions(auth, course, quiz, perPage)) },
            { unwrap(delegate.getQuizSubmissionPage(auth, it)) })
        return Response.success(CanvasQuizSubmissions(result.body()), result.raw())
    }

    override suspend fun getActiveCourses(authHeader: String, state: String, perPage: Int, page: Int) =
        all(authHeader) { delegate.getActiveCourses(authHeader, state, perPage, page) }
    override suspend fun getCourseAssignments(authHeader: String, courseId: Long, perPage: Int, page: Int) =
        all(authHeader) { delegate.getCourseAssignments(authHeader, courseId, perPage, page) }
    override suspend fun getTodoList(authHeader: String, perPage: Int) = all(authHeader) { delegate.getTodoList(authHeader, perPage) }
    override suspend fun getConversations(authHeader: String, perPage: Int) = all(authHeader) { delegate.getConversations(authHeader, perPage) }
    override suspend fun getActivityStream(authHeader: String, perPage: Int) = all(authHeader) { delegate.getActivityStream(authHeader, perPage) }
    override suspend fun getSelfGroups(authHeader: String, perPage: Int) = all(authHeader) { delegate.getSelfGroups(authHeader, perPage) }
    override suspend fun getGroupUsers(authHeader: String, groupId: Long, perPage: Int) = all(authHeader) { delegate.getGroupUsers(authHeader, groupId, perPage) }
    override suspend fun getCourseUsers(authHeader: String, courseId: Long, include: String, perPage: Int) =
        all(authHeader) { delegate.getCourseUsers(authHeader, courseId, include, perPage) }
    override suspend fun getCourseFiles(authHeader: String, courseId: Long, sort: String, order: String, perPage: Int) =
        all(authHeader) { delegate.getCourseFiles(authHeader, courseId, sort, order, perPage) }
    override suspend fun getCourseDiscussions(authHeader: String, courseId: Long, perPage: Int) =
        all(authHeader) { delegate.getCourseDiscussions(authHeader, courseId, perPage) }
    override suspend fun getCourseGroups(authHeader: String, courseId: Long, perPage: Int) = all(authHeader) { delegate.getCourseGroups(authHeader, courseId, perPage) }
    override suspend fun getCourseTabs(authHeader: String, courseId: Long, include: String, perPage: Int) = all(authHeader) { delegate.getCourseTabs(authHeader, courseId, include, perPage) }
    override suspend fun getModuleItems(authHeader: String, courseId: Long, moduleId: Long, perPage: Int, page: Int) =
        all(authHeader) { delegate.getModuleItems(authHeader, courseId, moduleId, perPage, page) }

    override suspend fun getCourseModules(authHeader: String, courseId: Long, include: String, perPage: Int): Response<List<CanvasModule>> = coroutineScope {
        val response = all(authHeader) { delegate.getCourseModules(authHeader, courseId, include, perPage) }
        val limit = Semaphore(4)
        val modules = response.body().orEmpty().map { module -> async {
            if (module.itemsCount != null && module.itemsCount == module.items.orEmpty().size) module
            else limit.withPermit {
                val items = getModuleItems(authHeader, courseId, module.id).body() ?: throw IOException("Módulo sin contenido")
                module.copy(items = items)
            }
        } }.awaitAll()
        Response.success(modules, response.raw())
    }
}

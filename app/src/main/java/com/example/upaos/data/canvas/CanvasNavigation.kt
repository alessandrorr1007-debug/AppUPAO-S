package com.example.upaos.data.canvas

import com.example.upaos.data.model.CanvasTab
import com.example.upaos.data.api.TrustedServers
import okhttp3.HttpUrl.Companion.toHttpUrl

data class CanvasNativeRoute(val course: Long, val kind: String, val value: String)
fun canvasNativeRoute(raw: String): CanvasNativeRoute? {
    val url = "https://upao.instructure.com/".toHttpUrl().resolve(raw) ?: return null
    if (!TrustedServers.isCanvas(url)) return null
    val parts = url.pathSegments
    if (parts.size >= 2 && parts[0] == "files" && parts[1].toLongOrNull()?.let { it > 0 } == true)
        return CanvasNativeRoute(0, "files", parts[1])
    if (parts.firstOrNull() == "equation_images") return CanvasNativeRoute(0, "image", url.toString())
    if (parts.size < 4 || parts[0] != "courses") return null
    val course = parts[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
    val kind = parts[2]
    if (kind !in setOf("pages", "files", "assignments", "discussion_topics", "quizzes")) return null
    if (kind != "pages" && parts[3].toLongOrNull()?.let { it > 0 } != true) return null
    if (kind == "assignments" && parts.size > 4) return null // submission URLs stay web
    return CanvasNativeRoute(course, kind, parts[3])
}
fun canvasVisibleTabs(tabs: List<CanvasTab>): Set<String> = tabs.filter {
    it.hidden != true && it.visibility !in setOf("admins", "none")
}.map { it.id }.toSet()

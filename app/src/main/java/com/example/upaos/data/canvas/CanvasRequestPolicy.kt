package com.example.upaos.data.canvas

import com.example.upaos.data.api.TrustedServers
import okhttp3.HttpUrl

/** A closed list: no uploads, submissions, administrative writes or quiz attempts. */
object CanvasRequestPolicy {
    private val writes = listOf(
        Regex("/api/v1/courses/[0-9]+/discussion_topics/[0-9]+/entries"),
        Regex("/api/v1/courses/[0-9]+/discussion_topics/[0-9]+/entries/[0-9]+/replies"),
        Regex("/api/v1/conversations"),
        Regex("/api/v1/conversations/[0-9]+/add_message")
    )
    fun allowed(method: String, url: HttpUrl): Boolean = TrustedServers.isCanvas(url) &&
        url.encodedPath.startsWith("/api/v1/") &&
        (method == "GET" || (method == "POST" && url.query == null && writes.any { it.matches(url.encodedPath) }))
}

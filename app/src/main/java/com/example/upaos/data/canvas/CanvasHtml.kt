package com.example.upaos.data.canvas

import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

/** No scripts, forms, remote frames, CSS URLs, event handlers or privileged bridges. */
fun canvasSafeHtml(html: String): String {
    val safe = Safelist.relaxed().removeTags("img").addAttributes("a", "href")
        .addProtocols("a", "href", "https").preserveRelativeLinks(true)
    val document = Jsoup.parseBodyFragment(html, "https://upao.instructure.com/")
    document.select("img").forEach { image ->
        val src = image.absUrl("src")
        image.replaceWith(org.jsoup.nodes.Element("a").attr("href", src).text(image.attr("alt").ifBlank { "Ver imagen" }))
    }
    return Jsoup.clean(document.body().html(), "https://upao.instructure.com/", safe)
}

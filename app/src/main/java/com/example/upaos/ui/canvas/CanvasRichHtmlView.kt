package com.example.upaos.ui.canvas

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Sandboxed display component for tables. No network, JS, credentials, storage or bridge. */
@Composable
internal fun CanvasRichHtmlView(safeHtml: String, modifier: Modifier, open: (String) -> Unit) {
    val text = MaterialTheme.colorScheme.onSurface.toArgb()
    val bg = MaterialTheme.colorScheme.surface.toArgb()
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val latestOpen by rememberUpdatedState(open)
    val css = "body{font-family:sans-serif;color:#${"%06x".format(text and 0xffffff)};background:#${"%06x".format(bg and 0xffffff)};overflow-wrap:anywhere}table{border-collapse:collapse;max-width:100%}td,th{border:1px solid;padding:8px}a{color:#${"%06x".format(accent and 0xffffff)}}"
    AndroidView(modifier = modifier.fillMaxWidth().height(400.dp), factory = { context -> WebView(context).apply {
        setBackgroundColor(bg)
        settings.javaScriptEnabled = false
        settings.domStorageEnabled = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.blockNetworkLoads = true
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean { latestOpen(request.url.toString()); return true }
            @Deprecated("Legacy Android callback")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean { latestOpen(url); return true }
        }
    } }, update = { web ->
        val content = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; style-src 'unsafe-inline'\"><style>$css</style></head><body>$safeHtml</body></html>"
        if (web.tag != content) { web.tag = content; web.loadDataWithBaseURL(null, content, "text/html", "UTF-8", null) }
    }, onRelease = { it.stopLoading(); it.destroy() })
}

package com.example.upaos.ui.canvas

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import com.example.upaos.data.api.TrustedServers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun openCanvasExternal(context: Context, url: String?) {
    val parsed = url?.toHttpUrlOrNull() ?: return
    if (!TrustedServers.isCanvas(parsed)) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(parsed.toString()))) }
}

/** Links supplied in API HTML open outside the app; no Canvas credentials are forwarded. */
internal fun openCanvasContentLink(context: Context, url: String?) {
    val absolute = runCatching { java.net.URI("https://upao.instructure.com/").resolve(url ?: "").toString() }.getOrNull()
    val parsed = absolute?.toHttpUrlOrNull() ?: return
    if (TrustedServers.isCanvas(parsed)) { openCanvasExternal(context, absolute); return }
    if (!parsed.isHttps || parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(absolute))) }
}

@Composable
internal fun CanvasExternalButton(url: String?, label: String = "Abrir en Canvas") {
    val context = LocalContext.current
    OutlinedButton(enabled = url?.toHttpUrlOrNull()?.let(TrustedServers::isCanvas) == true,
        onClick = { openCanvasExternal(context, url) }) { Text(label) }
}

package com.example.upaos.ui.canvas

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File

private val privatePdfClient = OkHttpClient.Builder().followSslRedirects(false).build()

/** Renders in private app storage; document links are never sent to a third-party viewer. */
@Composable
fun PrivatePdfViewer(url: String) {
    val context = LocalContext.current
    var document by remember(url) { mutableStateOf<File?>(null) }
    var pageCount by remember(url) { mutableIntStateOf(0) }
    var error by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        val file = File.createTempFile("document-", ".pdf", context.cacheDir)
        try {
            pageCount = withContext(Dispatchers.IO) {
                require(url.toHttpUrlOrNull()?.isHttps == true)
                privatePdfClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        check(response.isSuccessful)
                        val body = checkNotNull(response.body)
                        check(body.contentLength() <= 32L * 1024 * 1024)
                        body.byteStream().use { input ->
                            file.outputStream().use { output ->
                                val buffer = ByteArray(8192)
                                var total = 0L
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    check(total <= 32L * 1024 * 1024)
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                    }
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { it.pageCount }
                }
            }
            document = file
            kotlinx.coroutines.awaitCancellation()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            error = true
        } finally {
            file.delete()
        }
    }
    when {
        error -> Text("No se pudo abrir el PDF. Puedes usar Descargar al celular.", Modifier.padding(24.dp))
        document == null -> CircularProgressIndicator()
        else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items((0 until pageCount).toList()) { index ->
                val bitmap by produceState<Bitmap?>(null, document, index) {
                    value = withContext(Dispatchers.IO) {
                        try {
                            val sourceFile = document ?: return@withContext null
                            ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                                PdfRenderer(fd).use { renderer ->
                                    renderer.openPage(index).use { page ->
                                        val scale = minOf(1200f / page.width, 1800f / page.height)
                                        Bitmap.createBitmap(
                                            (page.width * scale).toInt().coerceAtLeast(1),
                                            (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888
                                        ).also {
                                            it.eraseColor(Color.WHITE)
                                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) { null }
                    }
                }
                Box(Modifier.fillMaxWidth().heightIn(min = 240.dp)) {
                    bitmap?.let { Image(it.asImageBitmap(), "Página ${index + 1}", Modifier.fillMaxWidth()) }
                        ?: Text("Cargando página ${index + 1}…", Modifier.padding(16.dp))
                }
            }
        }
    }
}

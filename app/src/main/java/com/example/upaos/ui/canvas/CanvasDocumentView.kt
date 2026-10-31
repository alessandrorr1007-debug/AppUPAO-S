package com.example.upaos.ui.canvas

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.upaos.data.canvas.*
import com.example.upaos.data.model.CanvasFileDetail
import com.example.upaos.data.local.TokenManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class CanvasDocumentState(val file: File? = null, val loading: Boolean = false, val error: String? = null)
class CanvasDocumentViewModel : ViewModel() {
    private val mutable = MutableStateFlow(CanvasDocumentState())
    val state = mutable.asStateFlow()
    fun download(metadata: CanvasFileDetail, dir: File, repository: DefaultCanvasRepository) {
        if (state.value.loading || state.value.file != null) return
        mutable.value = CanvasDocumentState(loading = true)
        viewModelScope.launch {
            try {
                val file = CanvasDocuments.download(metadata, dir, repository::requireSession)
                ensureActive()
                mutable.value = CanvasDocumentState(file)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = CanvasDocumentState(error = "No se pudo obtener el documento. Comprueba el acceso y vuelve a intentar.") }
        }
    }
    override fun onCleared() { state.value.file?.delete() }
}

@Composable
internal fun CanvasDocument(metadata: CanvasFileDetail, token: String, inline: Boolean = false) {
    val context = LocalContext.current
    val tokens = remember(context) { TokenManager(context) }
    val repository = rememberCanvasRepository(token)
    val vm: CanvasDocumentViewModel = viewModel(key = canvasViewModelKey("document:${metadata.id}", tokens.getSavedUser().orEmpty(), tokens.getSessionId(), "$token:${metadata.url.orEmpty()}"),
        factory = canvasViewModelFactory { CanvasDocumentViewModel() })
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(metadata.id, inline) {
        if (inline && metadata.contentType?.startsWith("image/") == true && metadata.size != null && metadata.size <= 2L * 1024 * 1024)
            vm.download(metadata, File(File(context.cacheDir, "canvas_documents"), canvasDocumentOwner(tokens.getSavedUser().orEmpty())), repository)
    }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(metadata.contentType ?: "application/octet-stream")) { uri ->
        val file = state.file
        if (uri != null && file != null) scope.launch {
            try {
                withContext(Dispatchers.IO) { repository.requireSession(); context.contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } } ?: error("No se pudo guardar") }
                saveMessage = "Documento guardado"
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { saveMessage = "No se pudo guardar el documento" }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${metadata.fileName} · ${metadata.formattedSize}")
        if (metadata.lockedForUser == true || metadata.hiddenForUser == true || metadata.locked == true) Text("Archivo restringido por Canvas")
        else if (state.file == null) Button(enabled = !state.loading, onClick = {
            vm.download(metadata, File(File(context.cacheDir, "canvas_documents"), canvasDocumentOwner(tokens.getSavedUser().orEmpty())), repository)
        }) { Text("Descargar para consultar") }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        saveMessage?.let { Text(it) }
        state.file?.let { file ->
            Text("Descarga lista · almacenamiento privado")
            TextButton(onClick = { export.launch(metadata.fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")) }) { Text("Guardar una copia…") }
            when {
                metadata.contentType == "application/pdf" || file.extension == "pdf" -> LocalCanvasPdf(file)
                metadata.contentType?.startsWith("image/") == true -> {
                    val bitmap by produceState<android.graphics.Bitmap?>(null, file) {
                        value = withContext(Dispatchers.IO) {
                            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(file.path, opts)
                            var sample = 1
                            while (opts.outWidth / sample > 1600 || opts.outHeight / sample > 1600) sample *= 2
                            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                        }
                    }
                    bitmap?.let { Image(it.asImageBitmap(), metadata.fileName, Modifier.fillMaxWidth()) }
                }
                else -> TextButton(onClick = {
                    runCatching {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, metadata.contentType ?: "application/octet-stream")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    }.onFailure { saveMessage = "No hay un visor instalado para este formato" }
                }) { Text("Abrir con una aplicación instalada") }
            }
        }
    }
}

@Composable
internal fun CanvasInlineImages(html: String, token: String) {
    val routes = remember(html) { org.jsoup.Jsoup.parse(html, "https://upao.instructure.com/")
        .select("img[src]").mapNotNull { canvasNativeRoute(it.absUrl("src")) }
        .filter { it.kind == "files" }.distinct().take(8) }
    routes.forEach { route ->
        val vm = canvasResourceViewModel("inline:${route.course}:${route.value}", token, null as CanvasFileDetail?) {
            if (route.course == 0L) fileByUrl("https://upao.instructure.com/api/v1/files/${route.value}") else file(route.course, route.value.toLong())
        }
        val state by vm.state.collectAsState()
        state.data?.takeIf { it.contentType?.startsWith("image/") == true }?.let { CanvasDocument(it, token, inline = true) }
    }
}

@Composable
internal fun LocalCanvasPdf(file: File) {
    val pages by produceState(0, file) { value = withContext(Dispatchers.IO) { runCatching {
        android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> android.graphics.pdf.PdfRenderer(fd).use { it.pageCount } }
    }.getOrDefault(0) } }
    // Nested LazyColumns must have a bounded height inside the document sheet.
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().height(480.dp)) {
        items(pages) { index ->
            val bitmap by produceState<android.graphics.Bitmap?>(null, file, index) {
                value = withContext(Dispatchers.IO) { runCatching {
                    android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                        android.graphics.pdf.PdfRenderer(fd).use { renderer -> renderer.openPage(index).use { page ->
                            val scale = minOf(1200f / page.width, 1800f / page.height)
                            android.graphics.Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888).also {
                                it.eraseColor(android.graphics.Color.WHITE); page.render(it, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        } }
                    }
                }.getOrNull() }
            }
            bitmap?.let { Image(it.asImageBitmap(), "Página ${index + 1}", Modifier.fillMaxWidth()) }
        }
    }
}

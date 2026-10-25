package com.example.upaos.ui.canvas

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.upaos.data.canvas.CanvasDiagnosticRow
import com.example.upaos.data.canvas.CanvasDiagnostics
import com.example.upaos.data.local.TokenManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class CanvasDiagnosticsViewModel(private val token: String, private val diagnostics: CanvasDiagnostics) : ViewModel() {
    private val mutable = MutableStateFlow(emptyList<CanvasDiagnosticRow>())
    val rows = mutable.asStateFlow()
    private val working = MutableStateFlow(false)
    val busy = working.asStateFlow()
    fun run() {
        if (working.value || token.isBlank()) return
        working.value = true
        mutable.value = emptyList()
        viewModelScope.launch {
            try { diagnostics.run(token) { mutable.value = mutable.value + it } }
            catch (e: CancellationException) { throw e }
            finally { working.value = false }
        }
    }
}

@Composable
fun CanvasDiagnosticsEntry(usuario: String) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Text("Diagnóstico de Canvas") }
    if (open) CanvasDiagnosticsScreen(usuario) { open = false }
}

@Composable
private fun CanvasDiagnosticsScreen(user: String, close: () -> Unit) {
    val context = LocalContext.current
    val tokens = remember(context) { TokenManager(context.applicationContext) }
    val token = tokens.getCanvasToken(user).orEmpty()
    val session = tokens.getSessionId()
    val vm: CanvasDiagnosticsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = canvasViewModelKey("diagnostics", user, session, token),
        factory = canvasViewModelFactory {
            CanvasDiagnosticsViewModel(token, CanvasDiagnostics(validate = {
                tokens.getSavedUser() == user && tokens.getSessionId() == session && tokens.getCanvasToken(user) == token
            }))
        })
    val rows by vm.rows.collectAsState()
    val busy by vm.busy.collectAsState()
    androidx.compose.ui.window.Dialog(onDismissRequest = close,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("Diagnóstico de Canvas", style = MaterialTheme.typography.titleLarge)
                Text("Solo GET. Listas paginadas completas. HTTP — con 0 ms: no probado por falta de recurso previo; con tiempo: sin respuesta HTTP. Se usa el primer curso activo.")
                if (token.isBlank()) Text("Configura el token desde Canvas.")
                Row {
                    TextButton(enabled = !busy && token.isNotBlank(), onClick = vm::run) { Text("Ejecutar") }
                    TextButton(enabled = rows.isNotEmpty(), onClick = {
                        val report = rows.joinToString("\n") { "${it.endpoint} | HTTP ${it.http ?: "—"} | cantidad ${it.count ?: "—"} | ${it.millis} ms" }
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("Diagnóstico de Canvas", report))
                    }) { Text("Copiar") }
                    TextButton(onClick = close) { Text("Cerrar") }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn {
                    items(rows) { row -> ListItem(headlineContent = { Text(row.endpoint) },
                        supportingContent = { Text("HTTP ${row.http ?: "—"} · cantidad ${row.count ?: "—"} · ${row.millis} ms") }) }
                }
            }
        }
    }
}

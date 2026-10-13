package com.example.upaos.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.upaos.data.api.RetrofitClient
import com.example.upaos.data.local.AccountPrivacy
import com.example.upaos.data.local.TokenManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PrivacyControls(usuario: String, onAccountRemoved: () -> Unit) {
    val context = LocalContext.current
    val tokens = remember { TokenManager(context) }
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf((tokens.getCuentas().map { it.usuario } + usuario).distinct()) }
    var selected by remember { mutableStateOf(usuario) }
    var action by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Privacidad y datos", style = MaterialTheme.typography.titleMedium)
            Text("Elige una cuenta guardada. Esto solo afecta a UPAO S; tu cuenta universitaria y Canvas se conservan.", style = MaterialTheme.typography.bodySmall)
            accounts.forEach { user -> FilterChip(selected = selected == user, enabled = !busy, onClick = { selected = user; message = null }, label = { Text(user) }) }
            OutlinedButton(enabled = !busy, onClick = { action = "cache" }) { Text("Borrar caché de esta cuenta") }
            OutlinedButton(enabled = !busy, onClick = { action = "local" }) { Text("Eliminar cuenta de este dispositivo") }
            Button(enabled = !busy && selected == usuario, onClick = { action = "server" }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Eliminar mis datos de UPAO S") }
            if (selected != usuario) Text("Para eliminar datos del backend, inicia sesión con esa cuenta.", style = MaterialTheme.typography.bodySmall)
            Text("Eliminar del dispositivo borra la contraseña, el token de Canvas, la caché y los recordatorios. Eliminar mis datos también borra ajustes, notas guardadas, historial, ranking, notificaciones y sugerencias del backend, y cierra todas tus sesiones.", style = MaterialTheme.typography.bodySmall)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
    action?.let { mode ->
        AlertDialog(onDismissRequest = { if (!busy) action = null },
            title = { Text(when (mode) { "cache" -> "¿Borrar datos guardados?"; "local" -> "¿Quitar $selected de este dispositivo?"; else -> "¿Eliminar tus datos de UPAO S?" }) },
            text = { Text(when (mode) {
                "cache" -> "Se eliminará la información descargada de $selected. Necesitarás conexión para volver a cargarla. La cuenta seguirá guardada."
                "local" -> "Se eliminarán la cuenta $selected, sus credenciales, datos guardados y recordatorios de este teléfono. Los datos del backend permanecerán."
                else -> "Se eliminarán tus datos del backend, todas tus sesiones y la cuenta de este teléfono. Esta acción no se puede deshacer. Si vuelves a iniciar sesión, se creará un nuevo registro en UPAO S."
            }) },
            dismissButton = { TextButton(enabled = !busy, onClick = { action = null }) { Text("Cancelar") } },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true; message = null
                scope.launch {
                    val deletingUser = selected
                    if (mode != "cache") AccountPrivacy.markDeleting(deletingUser, true)
                    try {
                        if (mode == "server") {
                            val response = RetrofitClient.apiService.eliminarDatosCuenta(usuario)
                            check(response.isSuccessful && response.body()?.get("success") == true) {
                                when (response.code()) { 404, 405 -> "El backend todavía no admite la eliminación. Actualízalo y vuelve a intentar."; 401 -> "Inicia sesión de nuevo antes de eliminar tus datos."; else -> "No se pudo eliminar en el backend. Tus datos locales se conservan; vuelve a intentar." }
                            }
                        }
                        if (mode == "cache") {
                            AccountPrivacy.clearCache(context, selected)
                            message = "Caché eliminada. La próxima consulta descargará los datos de nuevo."
                        } else {
                            AccountPrivacy.removeLocal(context, selected)
                            accounts = accounts.filter { it != selected }
                            if (selected == usuario) { action = null; onAccountRemoved() }
                            else { message = "Cuenta eliminada del dispositivo."; selected = usuario }
                        }
                        action = null
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { action = null; message = e.message ?: "No se pudieron eliminar los datos." }
                    finally { AccountPrivacy.markDeleting(deletingUser, false); busy = false }
                }
            }) { Text(if (busy) "Eliminando…" else "Eliminar") } })
    }
}

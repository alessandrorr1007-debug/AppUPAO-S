package com.example.upaos.ui.settings
import com.example.upaos.ui.components.CampusCard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

    CampusCard(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Privacidad y datos", style = MaterialTheme.typography.titleMedium)
            Text("Elige una cuenta guardada. Esto solo afecta a UPAO S; tu cuenta universitaria y Canvas se conservan.", style = MaterialTheme.typography.bodySmall)
            accounts.forEach { user -> FilterChip(selected = selected == user, enabled = !busy, onClick = { selected = user; message = null }, label = { Text(user) }) }
            OutlinedButton(enabled = !busy, onClick = { action = "cache" }) { Text("Borrar caché de esta cuenta") }
            Button(
                enabled = !busy,
                onClick = { action = "local" },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Cerrar sesión y borrar datos locales")
            }
            Text("Eliminar del dispositivo borra las credenciales guardadas, el token de Canvas, la caché, las notificaciones locales y los recordatorios.", style = MaterialTheme.typography.bodySmall)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }

    action?.let { mode ->
        AlertDialog(
            onDismissRequest = { if (!busy) action = null },
            title = { Text(if (mode == "cache") "¿Borrar datos guardados?" else "¿Cerrar sesión y borrar datos de $selected?") },
            text = { Text(if (mode == "cache")
                "Se eliminará la información descargada de $selected. Necesitarás conexión para volver a cargarla. La cuenta seguirá guardada."
            else
                "Se eliminarán las credenciales, notas, horarios, asistencias en caché y recordatorios de $selected en este dispositivo."
            ) },
            dismissButton = { TextButton(enabled = !busy, onClick = { action = null }) { Text("Cancelar") } },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true
                message = null
                scope.launch {
                    val deletingUser = selected
                    if (mode != "cache") AccountPrivacy.markDeleting(deletingUser, true)
                    try {
                        if (mode == "cache") {
                            AccountPrivacy.clearCache(context, selected)
                            message = "Caché eliminada. La próxima consulta descargará los datos de nuevo."
                        } else {
                            AccountPrivacy.removeLocal(context, selected)
                            accounts = accounts.filter { it != selected }
                            if (selected == usuario) {
                                action = null
                                onAccountRemoved()
                            } else {
                                message = "Cuenta eliminada del dispositivo."
                                selected = usuario
                            }
                        }
                        action = null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        action = null
                        message = e.message ?: "No se pudieron eliminar los datos."
                    } finally {
                        AccountPrivacy.markDeleting(deletingUser, false)
                        busy = false
                    }
                }
            }) { Text(if (busy) "Eliminando…" else "Eliminar") } }
        )
    }
}

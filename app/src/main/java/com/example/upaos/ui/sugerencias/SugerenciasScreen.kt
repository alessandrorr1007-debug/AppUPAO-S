package com.example.upaos.ui.sugerencias

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.EmojiObjects
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.upaos.data.api.RetrofitClient
import com.example.upaos.data.model.SugerenciaItem
import com.example.upaos.data.model.SugerenciaRequest
import com.example.upaos.ui.components.tiempoRelativo
import kotlinx.coroutines.launch

private fun textoEstado(estado: String): String = when (estado) {
    "en_revision" -> "En revisión"
    "aprobada" -> "Aprobada"
    "rechazada" -> "Rechazada"
    else -> "Pendiente"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SugerenciasScreen(
    usuario: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var texto by remember { mutableStateOf("") }
    var enviando by remember { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(true) }
    var sugerencias by remember { mutableStateOf<List<SugerenciaItem>>(emptyList()) }
    var errorCarga by remember { mutableStateOf<String?>(null) }

    suspend fun cargarMisSugerencias() {
        if (usuario == null) return
        try {
            val res = RetrofitClient.apiService.getMisSugerencias(usuario)
            if (res.isSuccessful && res.body() != null) {
                sugerencias = res.body()!!.sugerencias
                errorCarga = null
            } else {
                errorCarga = "No se pudieron cargar tus sugerencias"
            }
        } catch (e: Exception) {
            errorCarga = "Error de conexión: ${e.localizedMessage}"
        }
    }

    LaunchedEffect(usuario) {
        cargarMisSugerencias()
        cargando = false
    }

    fun enviar() {
        if (usuario == null) return
        if (texto.trim().length < 10) {
            Toast.makeText(context, "Escribe al menos 10 caracteres", Toast.LENGTH_SHORT).show()
            return
        }
        enviando = true
        scope.launch {
            try {
                val res = RetrofitClient.apiService.postSugerencia(SugerenciaRequest(usuario, texto.trim()))
                if (res.isSuccessful && res.body() != null) {
                    Toast.makeText(context, "Sugerencia enviada. ¡Gracias!", Toast.LENGTH_SHORT).show()
                    texto = ""
                    cargarMisSugerencias()
                } else {
                    val detalle = res.errorBody()?.string()
                    val msg = if (!detalle.isNullOrBlank() && detalle.contains("\"detail\"")) {
                        detalle.substringAfter("\"detail\":").substringAfter("\"").substringBefore("\"")
                    } else "No se pudo enviar (${res.code()})"
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error de conexión: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
            enviando = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Buzón de sugerencias", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.EmojiObjects,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "¿Qué mejorarías en UPAO Móvil?",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = texto,
                            onValueChange = { texto = it.take(500) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Ej: me gustaría ver el promedio ponderado por ciclo...") },
                            minLines = 4,
                            maxLines = 8,
                            supportingText = { Text("${texto.length}/500 · mínimo 10") }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { enviar() },
                            enabled = !enviando && texto.trim().length >= 10,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            if (enviando) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Enviando...")
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Enviar sugerencia")
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Mis sugerencias",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (cargando) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (errorCarga != null && sugerencias.isEmpty()) {
                item {
                    Text(text = errorCarga!!, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                }
            } else if (sugerencias.isEmpty()) {
                item {
                    Text(
                        text = "Aún no has enviado sugerencias.",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 14.sp
                    )
                }
            } else {
                items(sugerencias, key = { it.id }) { s ->
                    SugerenciaCard(s)
                }
            }
        }
    }
}

@Composable
fun SugerenciaCard(sugerencia: SugerenciaItem) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = sugerencia.texto,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SuggestionChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(textoEstado(sugerencia.estado), fontSize = 12.sp) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = when (sugerencia.estado) {
                            "aprobada" -> MaterialTheme.colorScheme.primaryContainer
                            "rechazada" -> MaterialTheme.colorScheme.errorContainer
                            "en_revision" -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        }
                    )
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = tiempoRelativo(sugerencia.fechaCreacion),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

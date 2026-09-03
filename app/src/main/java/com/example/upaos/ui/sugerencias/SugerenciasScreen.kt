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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.upaos.data.api.RetrofitClient
import com.example.upaos.data.model.SugerenciaItem
import com.example.upaos.data.model.SugerenciaRequest
import com.example.upaos.ui.components.AppCard
import com.example.upaos.ui.components.EmptyState
import com.example.upaos.ui.components.ErrorView
import com.example.upaos.ui.components.ModernTextField
import com.example.upaos.ui.components.PrimaryButton
import com.example.upaos.ui.components.SectionHeader
import com.example.upaos.ui.components.StatusBadge
import com.example.upaos.ui.components.tiempoRelativo
import com.example.upaos.ui.theme.UpaoAmber
import com.example.upaos.ui.theme.UpaoBlue
import com.example.upaos.ui.theme.UpaoGreen
import com.example.upaos.ui.theme.UpaoRed
import kotlinx.coroutines.launch

private fun textoEstado(estado: String): String = when (estado) {
    "en_revision" -> "En revisión"
    "aprobada" -> "Aprobada"
    "rechazada" -> "Rechazada"
    else -> "Pendiente"
}

private fun estadoColor(estado: String): Color = when (estado) {
    "aprobada" -> UpaoGreen
    "rechazada" -> UpaoRed
    "en_revision" -> UpaoAmber
    else -> UpaoBlue
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
                        detalle.substringAfter("\"detail\"").substringAfter("\"").substringBefore("\"")
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
                    containerColor = MaterialTheme.colorScheme.background
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AppCard(corner = 24.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.EmojiObjects,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "¿Qué mejorarías en UPAO Móvil?",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    ModernTextField(
                        value = texto,
                        onValueChange = { texto = it.take(500) },
                        label = "Tu sugerencia",
                        singleLine = false,
                        minLines = 4,
                        maxLines = 8,
                        placeholder = "Ej: me gustaría ver el promedio ponderado por ciclo...",
                        supportingText = "${texto.length}/500 · mínimo 10",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PrimaryButton(
                        text = "Enviar sugerencia",
                        onClick = { enviar() },
                        enabled = texto.trim().length >= 10,
                        loading = enviando,
                        icon = Icons.AutoMirrored.Filled.Send,
                        height = 48.dp,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }

            item {
                SectionHeader(title = "Mis sugerencias")
            }

            if (cargando) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (errorCarga != null && sugerencias.isEmpty()) {
                item {
                    ErrorView(message = errorCarga!!)
                }
            } else if (sugerencias.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.EmojiObjects,
                        title = "Aún no has enviado sugerencias",
                        subtitle = "Tus sugerencias enviadas aparecerán aquí."
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
    AppCard(corner = 20.dp, modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = sugerencia.texto,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(
                    text = textoEstado(sugerencia.estado),
                    color = estadoColor(sugerencia.estado)
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

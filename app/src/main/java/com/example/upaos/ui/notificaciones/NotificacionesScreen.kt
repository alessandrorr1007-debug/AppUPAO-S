package com.example.upaos.ui.notificaciones

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.upaos.data.api.RetrofitClient
import com.example.upaos.data.model.NotificacionItem
import com.example.upaos.ui.components.tiempoRelativo
import com.example.upaos.ui.theme.UpaoOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificacionesScreen(
    usuario: String?,
    onBack: () -> Unit
) {
    var notificaciones by remember { mutableStateOf<List<NotificacionItem>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    suspend fun cargar() {
        if (usuario == null) return
        try {
            val res = RetrofitClient.apiService.getNotificaciones(usuario)
            if (res.isSuccessful && res.body() != null) {
                notificaciones = res.body()!!.notificaciones
                errorMessage = null
            } else {
                errorMessage = "No se pudieron cargar las notificaciones"
            }
        } catch (e: Exception) {
            errorMessage = "Error de conexión: ${e.localizedMessage}"
        }
    }

    LaunchedEffect(usuario) {
        cargar()
        // Al abrir la pantalla se marcan todas como leídas (el badge vuelve a 0).
        try {
            RetrofitClient.apiService.marcarNotificacionesLeidas(usuario ?: return@LaunchedEffect)
        } catch (e: Exception) {
            // Silencioso: si falla, la siguiente apertura lo reintenta.
        }
        cargar()
        cargando = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Notificaciones", fontWeight = FontWeight.Bold) },
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
        when {
            cargando -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            errorMessage != null && notificaciones.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
            notificaciones.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsNone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Aún no hay notificaciones",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Cuando cambie una nota te avisaremos aquí.",
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notificaciones, key = { it.id }) { notif ->
                        NotificacionCard(notif)
                    }
                }
            }
        }
    }
}

@Composable
fun NotificacionCard(notif: NotificacionItem) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (notif.leida) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            }
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (notif.leida) 1.dp else 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (notif.leida) MaterialTheme.colorScheme.outlineVariant else UpaoOrange)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (!notif.curso.isNullOrBlank()) {
                    Text(
                        text = notif.curso,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = notif.mensaje,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (notif.leida) FontWeight.Normal else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = tiempoRelativo(notif.fechaCreacion),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

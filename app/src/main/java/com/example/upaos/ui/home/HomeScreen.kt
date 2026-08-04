package com.example.upaos.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.upaos.data.api.RetrofitClient
import com.example.upaos.data.local.TokenManager
import com.example.upaos.ui.asistencia.AsistenciaContent
import com.example.upaos.ui.grades.GradesContent
import com.example.upaos.ui.horario.HorarioContent
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    token: String,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit,
    onOpenCalculator: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotifications: () -> Unit
) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var unreadCount by remember { mutableIntStateOf(0) }
    var semanaEtiqueta by remember { mutableStateOf<String?>(null) }

    val usuario = tokenManager.getSavedUser()

    fun cerrarSesion() {
        tokenManager.clearSession()
        onLogout()
    }

    // 1.3 Semana académica: chip con la etiqueta del ciclo (si el admin la configuró).
    LaunchedEffect(usuario) {
        if (usuario == null) return@LaunchedEffect
        try {
            val res = RetrofitClient.apiService.getSemana()
            if (res.isSuccessful && res.body() != null) {
                val info = res.body()!!
                if (info.configurada && info.fueraDeCiclo != true && !info.etiqueta.isNullOrBlank()) {
                    semanaEtiqueta = info.etiqueta
                }
            }
        } catch (e: Exception) {
            // Silencioso: sin chip si el servidor no responde.
        }
    }

    // Consulta periódica del contador de notificaciones no leídas (badge).
    LaunchedEffect(usuario) {
        if (usuario == null) return@LaunchedEffect
        while (true) {
            try {
                val res = RetrofitClient.apiService.getNotificaciones(usuario)
                if (res.isSuccessful && res.body() != null) {
                    unreadCount = res.body()!!.noLeidas
                }
            } catch (e: Exception) {
                // Silencioso: se reintenta en el siguiente ciclo.
            }
            delay(30_000)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (selectedTab) {
                            0 -> "Mis Notas UPAO"
                            1 -> "Horario UPAO"
                            else -> "Asistencia UPAO"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                actions = {
                    BadgedBox(
                        badge = {
                            if (unreadCount > 0) {
                                Badge {
                                    Text(if (unreadCount > 9) "9+" else "$unreadCount")
                                }
                            }
                        }
                    ) {
                        IconButton(onClick = onOpenNotifications) {
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = "Notificaciones",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Ajustes",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = if (isDarkTheme) "Cambiar a tema claro" else "Cambiar a tema oscuro",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onOpenCalculator) {
                        Icon(
                            imageVector = Icons.Filled.Calculate,
                            contentDescription = "Calculadora de Notas",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { cerrarSesion() }) {
                        Text("Salir", fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.Grade, contentDescription = null) },
                    label = { Text("Notas") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.Schedule, contentDescription = null) },
                    label = { Text("Horario") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Filled.EventAvailable, contentDescription = null) },
                    label = { Text("Asistencia") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (semanaEtiqueta != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.EventNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = semanaEtiqueta!!,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(durationMillis = 300),
                label = "tabCrossfade",
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) { tab ->
                when (tab) {
                    0 -> GradesContent(
                        token = token,
                        usuario = usuario,
                        onSesionExpirada = { cerrarSesion() }
                    )
                    1 -> HorarioContent(
                        token = token,
                        usuario = usuario,
                        onSesionExpirada = { cerrarSesion() }
                    )
                    else -> AsistenciaContent(
                        token = token,
                        usuario = usuario,
                        onSesionExpirada = { cerrarSesion() }
                    )
                }
            }
        }
    }
}

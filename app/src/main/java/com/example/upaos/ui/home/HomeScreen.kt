package com.example.upaos.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
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
    onSwitchAccount: () -> Unit = onLogout,
    onOpenCalculator: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenCourse: (String, String, String, String) -> Unit = { _, _, _, _ -> }
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
            // Silencioso
        }
    }

    LaunchedEffect(usuario) {
        if (usuario == null) return@LaunchedEffect
        while (true) {
            try {
                val res = RetrofitClient.apiService.getNotificaciones(usuario)
                if (res.isSuccessful && res.body() != null) {
                    unreadCount = res.body()!!.noLeidas
                }
            } catch (e: Exception) {
                // Silencioso
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
                            0 -> "Mis Notas"
                            1 -> "Horario"
                            else -> "Asistencia"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Ajustes",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Más opciones",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isDarkTheme) "Tema claro" else "Tema oscuro", fontSize = 14.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onToggleTheme()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Calculadora de notas", fontSize = 14.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Calculate,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onOpenCalculator()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Cambiar de cuenta", fontSize = 14.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onSwitchAccount()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Cerrar sesión", fontSize = 14.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    cerrarSesion()
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.Grade, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    label = { Text("Notas", fontSize = 12.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    label = { Text("Horario", fontSize = 12.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Filled.EventAvailable, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    label = { Text("Asistencia", fontSize = 12.sp) }
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
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.EventNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = semanaEtiqueta!!,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "tabCrossfade",
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) { tab ->
                when (tab) {
                    0 -> GradesContent(
                        token = token,
                        usuario = usuario,
                        onOpenCourse = { periodo, carrera, crn, nombre ->
                            onOpenCourse(periodo, carrera, crn, nombre)
                        }
                    )
                    1 -> HorarioContent(
                        token = token,
                        usuario = usuario
                    )
                    else -> AsistenciaContent(
                        token = token,
                        usuario = usuario
                    )
                }
            }
        }
    }
}

package com.example.upaos.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.EmojiObjects
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.NotificationsActive
import com.example.upaos.data.local.NotificationPreferences
import com.example.upaos.service.AsistenciaWorker
import com.example.upaos.service.NotificationService
import com.example.upaos.data.api.RetrofitClient
import com.example.upaos.data.model.AutoCheckRequest
import com.example.upaos.data.model.IntervaloRequest
import com.example.upaos.data.model.RankingOptinRequest
import com.example.upaos.ui.components.AppCard
import com.example.upaos.ui.components.StatusBadge
import com.example.upaos.ui.components.cursoColor
import com.example.upaos.ui.components.toTitleCase
import kotlinx.coroutines.launch

private const val INTERVALO_FIJO_MINUTOS = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    usuario: String?,
    onBack: () -> Unit,
    onOpenSugerencias: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notificationPrefs = remember { NotificationPreferences(context) }

    var checkNotasEnabled by remember { mutableStateOf(notificationPrefs.checkNotasEnabled) }
    var checkAsistenciaEnabled by remember { mutableStateOf(notificationPrefs.checkAsistenciaEnabled) }
    var tieneTokenFcm by remember { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(true) }
    var guardando by remember { mutableStateOf(false) }

    var nombreEstudiante by remember { mutableStateOf<String?>(null) }
    var rankingOptin by remember { mutableStateOf(false) }

    var buscandoActualizacion by remember { mutableStateOf(false) }
    var dialogActualizacionInfo by remember { mutableStateOf<com.example.upaos.util.UpdateInfo?>(null) }
    var descargandoActualizacion by remember { mutableStateOf(false) }

    LaunchedEffect(usuario) {
        if (usuario == null) {
            cargando = false
            return@LaunchedEffect
        }
        try {
            val res = RetrofitClient.apiService.getSettings(usuario)
            if (res.isSuccessful && res.body() != null) {
                val body = res.body()!!
                checkNotasEnabled = body.autoCheckEnabled
                notificationPrefs.checkNotasEnabled = body.autoCheckEnabled
                tieneTokenFcm = body.tieneTokenFcm
                // El servidor revisará siempre cada 5 minutos internamente
                if (body.intervaloMinutos != INTERVALO_FIJO_MINUTOS) {
                    try {
                        RetrofitClient.apiService.updateIntervalo(usuario, IntervaloRequest(INTERVALO_FIJO_MINUTOS))
                    } catch (e: Exception) {
                        // Silencioso
                    }
                }
            }
        } catch (e: Exception) {
            // Silencioso
        }
        if (checkAsistenciaEnabled) {
            AsistenciaWorker.schedule(context, 15)
        }
        try {
            val resCuenta = RetrofitClient.apiService.getCuenta(usuario)
            if (resCuenta.isSuccessful && resCuenta.body() != null) {
                val cuenta = resCuenta.body()!!
                nombreEstudiante = cuenta.nombre
                rankingOptin = cuenta.rankingOptin
            }
        } catch (e: Exception) {
            // El perfil es opcional
        }
        cargando = false
    }

    fun guardar(
        cambiarNotas: Boolean? = null,
        cambiarAsistencia: Boolean? = null
    ) {
        if (usuario == null) return
        guardando = true
        scope.launch {
            try {
                if (cambiarNotas != null) {
                    checkNotasEnabled = cambiarNotas
                    notificationPrefs.checkNotasEnabled = cambiarNotas
                    RetrofitClient.apiService.updateAutoCheck(usuario, AutoCheckRequest(cambiarNotas))
                    RetrofitClient.apiService.updateIntervalo(usuario, IntervaloRequest(INTERVALO_FIJO_MINUTOS))
                    val msg = if (cambiarNotas) "Notificaciones de notas activadas" else "Notificaciones de notas desactivadas"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
                if (cambiarAsistencia != null) {
                    checkAsistenciaEnabled = cambiarAsistencia
                    notificationPrefs.checkAsistenciaEnabled = cambiarAsistencia
                    if (cambiarAsistencia) {
                        AsistenciaWorker.schedule(context, 15)
                        Toast.makeText(context, "Notificaciones de asistencia activadas", Toast.LENGTH_SHORT).show()
                    } else {
                        AsistenciaWorker.cancel(context)
                        Toast.makeText(context, "Notificaciones de asistencia desactivadas", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al guardar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
            guardando = false
        }
    }

    fun cambiarRankingOptin(nuevo: Boolean) {
        if (usuario == null) return
        rankingOptin = nuevo
        scope.launch {
            try {
                val res = RetrofitClient.apiService.postRankingOptin(RankingOptinRequest(usuario, nuevo))
                if (!res.isSuccessful) {
                    rankingOptin = !nuevo
                    Toast.makeText(context, "No se pudo actualizar el ranking (${res.code()})", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                rankingOptin = !nuevo
                Toast.makeText(context, "Error de conexión: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", fontWeight = FontWeight.Bold) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (cargando) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 48.dp))
                }
                return@Column
            }

            if (usuario == null) {
                AppCard(corner = 20.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Sin sesión guardada",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Inicia sesión para activar la revisión automática de notas en segundo plano.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                return@Column
            }

            val avatarColor = cursoColor(nombreEstudiante ?: usuario)
            AppCard(corner = 20.dp, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(avatarColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (nombreEstudiante ?: usuario!!).trim().firstOrNull()?.uppercase() ?: "?",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Estudiante: ${nombreEstudiante?.let { toTitleCase(it) } ?: usuario}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (nombreEstudiante != null) "Código: $usuario" else usuario!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            AppCard(
                corner = 20.dp,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("Buzón de sugerencias") },
                        supportingContent = { Text("Envíanos ideas y revisa su estado") },
                        leadingContent = {
                            Surface(
                                shape = CircleShape,
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
                        },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                        modifier = Modifier.clickable(onClick = onOpenSugerencias)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            AppCard(corner = 20.dp, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Activar notificaciones",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Elige qué alertas deseas recibir en tu celular:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Opción 1: Notificaciones de Notas
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notificaciones de Notas",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Avisa cuando publiquen notas o cambien evaluaciones",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = checkNotasEnabled,
                            onCheckedChange = { nuevo ->
                                checkNotasEnabled = nuevo
                                guardar(cambiarNotas = nuevo)
                            },
                            enabled = !guardando
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Opción 2: Notificaciones de Asistencia
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.FactCheck,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notificaciones de Asistencias",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Avisa cuando te pongan una asistencia o falta en clase",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = checkAsistenciaEnabled,
                            onCheckedChange = { nuevo ->
                                checkAsistenciaEnabled = nuevo
                                guardar(cambiarAsistencia = nuevo)
                            },
                            enabled = !guardando
                        )
                    }

                    if (checkNotasEnabled || checkAsistenciaEnabled) {
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = {
                                if (checkAsistenciaEnabled) {
                                    NotificationService.mostrarNotificacionAsistencia(
                                        context,
                                        "✅ Asistencia registrada (Prueba)",
                                        "Redes y Comunicaciones: ¡Se registró tu asistencia! (8 asistencias, 1 falta)."
                                    )
                                } else {
                                    NotificationService.mostrarNotificacionNotas(
                                        context,
                                        "📢 Nueva nota publicada (Prueba)",
                                        "Redes y Comunicaciones: Se publicó la nota de Evaluación Parcial (17.5)."
                                    )
                                }
                                Toast.makeText(context, "Notificación de prueba enviada al móvil", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Probar notificación en este celular")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sección Actualizaciones
            AppCard(corner = 20.dp, modifier = Modifier.fillMaxWidth()) {
                Column {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = "Buscar actualizaciones",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        supportingContent = {
                            val currentVer = com.example.upaos.util.AppUpdater.getCurrentVersionName(context)
                            Text(
                                text = "Versión actual: v$currentVer",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.EmojiObjects,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        trailingContent = {
                            if (buscandoActualizacion) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            }
                        },
                        modifier = Modifier.clickable(enabled = !buscandoActualizacion) {
                            buscandoActualizacion = true
                            scope.launch {
                                try {
                                    val info = com.example.upaos.util.AppUpdater.checkForUpdates(context)
                                    buscandoActualizacion = false
                                    if (info.hasUpdate) {
                                        dialogActualizacionInfo = info
                                    } else {
                                        Toast.makeText(context, "¡Tienes instalada la última versión disponible (v${info.currentVersion})!", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    buscandoActualizacion = false
                                    Toast.makeText(context, "No se pudo verificar: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!tieneTokenFcm) {
                Text(
                    text = "Nota: este dispositivo aún no ha registrado su token push. Abre la app con conexión para sincronizarlo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                Text(
                    text = "Tu dispositivo está listo para recibir notificaciones de notas y asistencias.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            dialogActualizacionInfo?.let { info ->
                com.example.upaos.ui.components.UpdateDialog(
                    updateInfo = info,
                    isDownloading = descargandoActualizacion,
                    onDismiss = { dialogActualizacionInfo = null },
                    onConfirmUpdate = {
                        if (info.downloadUrl != null) {
                            descargandoActualizacion = true
                            com.example.upaos.util.AppUpdater.downloadAndInstall(
                                context = context,
                                downloadUrl = info.downloadUrl,
                                versionName = info.latestVersion
                            )
                            Toast.makeText(
                                context,
                                "Descargando actualización en segundo plano...",
                                Toast.LENGTH_LONG
                            ).show()
                            dialogActualizacionInfo = null
                            descargandoActualizacion = false
                        } else {
                            val browserIntent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(info.releasePageUrl)
                            )
                            context.startActivity(browserIntent)
                            dialogActualizacionInfo = null
                        }
                    }
                )
            }
        }
    }
}

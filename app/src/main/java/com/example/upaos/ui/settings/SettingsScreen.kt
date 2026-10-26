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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import com.example.upaos.data.local.BiometricHelper
import com.example.upaos.data.local.NotificationPreferences
import com.example.upaos.data.local.SecurityPreferences
import com.example.upaos.data.local.TokenManager
import com.example.upaos.service.AsistenciaWorker
import com.example.upaos.service.NotificationService
import com.example.upaos.service.SyncScheduler
import com.example.upaos.ui.components.AppCard
import com.example.upaos.ui.components.StatusBadge
import com.example.upaos.ui.components.cursoColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    usuario: String?,
    onBack: () -> Unit,
    onAccountRemoved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notificationPrefs = remember { NotificationPreferences(context) }
    val securityPrefs = remember { SecurityPreferences(context) }
    val puedeUsarBiometria = remember { BiometricHelper.puedeAutenticar(context) }
    var biometricLockEnabled by remember { mutableStateOf(securityPrefs.isBiometricLockEnabled) }

    var checkNotasEnabled by remember { mutableStateOf(notificationPrefs.checkNotasEnabled) }
    var checkAsistenciaEnabled by remember { mutableStateOf(notificationPrefs.checkAsistenciaEnabled) }
    var recordatorioClasesEnabled by remember { mutableStateOf(notificationPrefs.recordatorioClasesEnabled) }
    var cargando by remember { mutableStateOf(true) }
    var guardando by remember { mutableStateOf(false) }

    var nombreEstudiante by remember { mutableStateOf<String?>(null) }


    LaunchedEffect(usuario) {
        if (usuario == null) {
            cargando = false
            return@LaunchedEffect
        }
        nombreEstudiante = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val tokens = TokenManager(context)
            val savedName = tokens.getCuentas().firstOrNull { it.usuario == usuario }?.nombre
            val profileName = try {
                tokens.getProfileJson(usuario)?.let { com.example.upaos.data.profile.StudentProfile.parse(it).fullName }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { null }
            com.example.upaos.data.profile.StudentProfile.normalizeName(profileName ?: savedName)
        }
        checkAsistenciaEnabled = notificationPrefs.checkAsistenciaEnabled
        checkNotasEnabled = notificationPrefs.checkNotasEnabled
        recordatorioClasesEnabled = notificationPrefs.recordatorioClasesEnabled
        if (checkAsistenciaEnabled || checkNotasEnabled) {
            SyncScheduler.start(context)
        }
        if (recordatorioClasesEnabled) {
            com.example.upaos.service.ClassReminderScheduler.reprogramar(context)
        }
        cargando = false
    }

    fun guardar(
        cambiarNotas: Boolean? = null,
        cambiarAsistencia: Boolean? = null,
        cambiarRecordatorioClases: Boolean? = null
    ) {
        if (usuario == null) return
        guardando = true
        scope.launch {
            try {
                if (cambiarNotas != null) {
                    checkNotasEnabled = cambiarNotas
                    notificationPrefs.checkNotasEnabled = cambiarNotas

                    if (cambiarNotas || checkAsistenciaEnabled) {
                        SyncScheduler.start(context)
                    } else {
                        SyncScheduler.cancel(context)
                    }
                    val msg = if (cambiarNotas) "Notificaciones de notas activadas" else "Notificaciones de notas desactivadas"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
                if (cambiarAsistencia != null) {
                    checkAsistenciaEnabled = cambiarAsistencia
                    notificationPrefs.checkAsistenciaEnabled = cambiarAsistencia
                    if (cambiarAsistencia || checkNotasEnabled) {
                        SyncScheduler.start(context)
                        SyncScheduler.runNow(context)
                        Toast.makeText(context, "Notificaciones de asistencias activadas", Toast.LENGTH_SHORT).show()
                    } else {
                        SyncScheduler.cancel(context)
                        Toast.makeText(context, "Notificaciones de asistencia desactivadas", Toast.LENGTH_SHORT).show()
                    }
                }
                if (cambiarRecordatorioClases != null) {
                    recordatorioClasesEnabled = cambiarRecordatorioClases
                    notificationPrefs.recordatorioClasesEnabled = cambiarRecordatorioClases
                    if (cambiarRecordatorioClases) {
                        com.example.upaos.service.ClassReminderScheduler.reprogramar(context)
                        Toast.makeText(context, "Recordatorios de clase activados", Toast.LENGTH_SHORT).show()
                    } else {
                        com.example.upaos.service.ClassReminderScheduler.cancelarTodos(context)
                        Toast.makeText(context, "Recordatorios de clase desactivados", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al guardar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
            guardando = false
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
                            text = "Estudiante",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Código: $usuario",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Nombre completo: ${nombreEstudiante ?: "No disponible"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            PrivacyControls(usuario, onAccountRemoved)

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
                                text = "Revisión automática en segundo plano",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
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

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Opción 3: Recordatorio de Clases
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
                                    imageVector = Icons.Filled.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Recordatorio de Clases",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Avisa 15 minutos antes con aula y pabellón",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = recordatorioClasesEnabled,
                            onCheckedChange = { nuevo ->
                                recordatorioClasesEnabled = nuevo
                                guardar(cambiarRecordatorioClases = nuevo)
                            },
                            enabled = !guardando
                        )
                    }

                    if (checkNotasEnabled || checkAsistenciaEnabled) {
                        // Verificación de optimización de batería
                        val powerManager = remember {
                            context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
                        }
                        val isIgnoringBattery = remember(powerManager) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && powerManager != null) {
                                powerManager.isIgnoringBatteryOptimizations(context.packageName)
                            } else true
                        }

                        if (!isIgnoringBattery) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "⚡ Ahorro de batería detectado",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Xiaomi: activa Inicio automático y selecciona Batería > Sin restricciones para UPAO S.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                                    val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                        data = android.net.Uri.parse("package:${context.packageName}")
                                                    }
                                                    context.startActivity(intent)
                                                }
                                            } catch (_: Exception) {
                                                try {
                                                    val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                                    context.startActivity(intent)
                                                } catch (_: Exception) {
                                                    Toast.makeText(context, "Abre Ajustes > Batería > UPAO S y desactiva el ahorro de batería", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Desactivar ahorro de batería")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sección Seguridad y Bloqueo de la App
            AppCard(corner = 20.dp, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bloqueo de la app",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (puedeUsarBiometria) {
                                    "Solicitar huella, rostro o PIN al abrir o tras 1 min en segundo plano"
                                } else {
                                    "No disponible: configura un PIN, patrón o huella en tu teléfono para activar el bloqueo"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (puedeUsarBiometria) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = biometricLockEnabled,
                            onCheckedChange = { nuevo ->
                                if (nuevo && !puedeUsarBiometria) {
                                    Toast.makeText(
                                        context,
                                        "Tu dispositivo no tiene biometría ni PIN/patrón configurado en los Ajustes del sistema.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    biometricLockEnabled = nuevo
                                    securityPrefs.isBiometricLockEnabled = nuevo
                                    (context as? android.app.Activity)?.let { act ->
                                        if (nuevo) {
                                            act.window.setFlags(
                                                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                                                android.view.WindowManager.LayoutParams.FLAG_SECURE
                                            )
                                        } else {
                                            act.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                                        }
                                    }
                                }
                            },
                            enabled = puedeUsarBiometria || biometricLockEnabled
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Tu dispositivo está listo para recibir notificaciones de notas y asistencias en segundo plano.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

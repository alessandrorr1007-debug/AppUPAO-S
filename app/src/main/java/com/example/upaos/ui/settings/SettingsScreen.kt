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
import androidx.compose.material.icons.filled.AdminPanelSettings
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
import com.example.upaos.data.api.RetrofitClient
import com.example.upaos.data.model.AutoCheckRequest
import com.example.upaos.data.model.IntervaloRequest
import com.example.upaos.data.model.RankingOptinRequest
import com.example.upaos.ui.components.AppCard
import com.example.upaos.ui.components.StatusBadge
import com.example.upaos.ui.components.cursoColor
import com.example.upaos.ui.components.toTitleCase
import kotlinx.coroutines.launch

private val OPCIONES_INTERVALO = listOf(5, 10, 15, 30)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    usuario: String?,
    onBack: () -> Unit,
    onOpenSugerencias: () -> Unit,
    onOpenRanking: () -> Unit,
    onOpenAdmin: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var autoCheckEnabled by remember { mutableStateOf(false) }
    var intervalo by remember { mutableIntStateOf(10) }
    var tieneTokenFcm by remember { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(true) }
    var guardando by remember { mutableStateOf(false) }

    var nombreEstudiante by remember { mutableStateOf<String?>(null) }
    var esAdmin by remember { mutableStateOf(false) }
    var rankingOptin by remember { mutableStateOf(false) }

    LaunchedEffect(usuario) {
        if (usuario == null) {
            cargando = false
            return@LaunchedEffect
        }
        try {
            val res = RetrofitClient.apiService.getSettings(usuario)
            if (res.isSuccessful && res.body() != null) {
                val body = res.body()!!
                autoCheckEnabled = body.autoCheckEnabled
                intervalo = body.intervaloMinutos.takeIf { it in OPCIONES_INTERVALO } ?: 10
                tieneTokenFcm = body.tieneTokenFcm
            } else {
                Toast.makeText(context, "No se pudieron cargar los ajustes (${res.code()})", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error al cargar ajustes: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
        try {
            val resCuenta = RetrofitClient.apiService.getCuenta(usuario)
            if (resCuenta.isSuccessful && resCuenta.body() != null) {
                val cuenta = resCuenta.body()!!
                nombreEstudiante = cuenta.nombre
                esAdmin = cuenta.esAdmin
                rankingOptin = cuenta.rankingOptin
            }
        } catch (e: Exception) {
            // El perfil es opcional
        }
        cargando = false
    }

    fun guardar(cambiarSwitch: Boolean? = null, cambiarIntervalo: Int? = null) {
        if (usuario == null) return
        guardando = true
        scope.launch {
            try {
                if (cambiarSwitch != null) {
                    RetrofitClient.apiService.updateAutoCheck(usuario, AutoCheckRequest(cambiarSwitch))
                }
                if (cambiarIntervalo != null) {
                    RetrofitClient.apiService.updateIntervalo(usuario, IntervaloRequest(cambiarIntervalo))
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
                    if (esAdmin) {
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusBadge(text = "Administrador", color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            AppCard(corner = 20.dp, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Participar en el ranking de cursos",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Comparte solo tu nota por curso (de forma anónima) para ver tu posición relativa. Puedes desactivarlo cuando quieras.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = rankingOptin,
                        onCheckedChange = { nuevo -> cambiarRankingOptin(nuevo) }
                    )
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
                        headlineContent = { Text("Ranking de cursos") },
                        supportingContent = { Text("Tu posición anónima en cada curso") },
                        leadingContent = {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Leaderboard,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                        modifier = Modifier.clickable(onClick = onOpenRanking)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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

                    if (esAdmin) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ListItem(
                            headlineContent = { Text("Panel Administrador", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                            supportingContent = { Text("Métricas, usuarios registrados y configuración del ciclo") },
                            leadingContent = {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.AdminPanelSettings,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                            modifier = Modifier.clickable(onClick = onOpenAdmin)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            AppCard(corner = 20.dp, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Revisión automática en 2do plano",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Revisa tus notas cada cierto tiempo y te notifica cuando un componente cambia. El servidor guarda tu contraseña cifrada.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoCheckEnabled,
                        onCheckedChange = { nuevo ->
                            autoCheckEnabled = nuevo
                            guardar(cambiarSwitch = nuevo)
                        },
                        enabled = !guardando
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            AppCard(corner = 20.dp, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "Frecuencia de revisión",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Cada cuánto revisa el servidor si cambiaron tus notas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OPCIONES_INTERVALO.forEach { opt ->
                            FilterChip(
                                selected = intervalo == opt,
                                onClick = {
                                    intervalo = opt
                                    guardar(cambiarIntervalo = opt)
                                },
                                enabled = autoCheckEnabled && !guardando,
                                label = { Text("$opt min") }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!tieneTokenFcm) {
                Text(
                    text = "Nota: este dispositivo aún no ha registrado su token de notificaciones. Abre la app una vez más o reiníciala para activar las notificaciones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                Text(
                    text = "Tu dispositivo está listo para recibir notificaciones de notas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

package com.example.upaos.ui.notificaciones
import com.example.upaos.ui.components.CampusTopBar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.upaos.data.model.NotificacionItem
import com.example.upaos.data.upao.UpaoRepository
import com.example.upaos.ui.components.AppCard
import com.example.upaos.ui.components.EmptyState
import com.example.upaos.ui.components.ErrorView
import com.example.upaos.ui.components.SkeletonBox
import com.example.upaos.ui.components.courseAccent
import com.example.upaos.ui.components.tiempoRelativo
import com.example.upaos.ui.components.toTitleCase
import com.example.upaos.ui.components.limpiarMensajeNotificacion
import com.example.upaos.ui.theme.UpaoOrange
import kotlinx.coroutines.launch

private enum class NotifFilter(val label: String) {
    TODAS("Todas"),
    SIN_LEER("Sin leer"),
    NOTAS("Notas"),
    ASISTENCIA("Asistencias")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificacionesScreen(
    usuario: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val upaoRepository = remember { UpaoRepository(context) }
    val scope = rememberCoroutineScope()
    var notificaciones by remember { mutableStateOf<List<NotificacionItem>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf(NotifFilter.TODAS) }

    suspend fun cargar() {
        if (usuario == null) return
        try {
            val res = upaoRepository.getNotificaciones(usuario)
            notificaciones = res.notificaciones
            errorMessage = null
        } catch (e: Exception) {
            errorMessage = "Error al cargar notificaciones: ${e.localizedMessage}"
        } finally {
            cargando = false
        }
    }

    fun marcarLeida(notif: NotificacionItem) {
        if (notif.leida) return
        scope.launch {
            notificaciones = notificaciones.map {
                if (it.id == notif.id) it.copy(leida = true) else it
            }
            if (usuario != null) {
                upaoRepository.marcarNotificacionLeida(notif.id, usuario)
            }
        }
    }

    fun marcarTodasLeidas() {
        val noLeidas = notificaciones.filter { !it.leida }
        if (noLeidas.isEmpty() || usuario == null) return
        scope.launch {
            notificaciones = notificaciones.map { it.copy(leida = true) }
            upaoRepository.marcarTodasNotificacionesLeidas(usuario)
        }
    }

    LaunchedEffect(usuario) {
        cargar()
        cargando = false
    }

    val noLeidasTotal = notificaciones.count { !it.leida }

    val notificacionesFiltradas = remember(notificaciones, selectedFilter) {
        when (selectedFilter) {
            NotifFilter.TODAS -> notificaciones
            NotifFilter.SIN_LEER -> notificaciones.filter { !it.leida }
            NotifFilter.NOTAS -> notificaciones.filter { notif ->
                val esAsist = notif.componente?.contains("asistencia", ignoreCase = true) == true ||
                        notif.mensaje.contains("asistencia", ignoreCase = true) ||
                        notif.mensaje.contains("falta", ignoreCase = true)
                !esAsist
            }
            NotifFilter.ASISTENCIA -> notificaciones.filter { notif ->
                notif.componente?.contains("asistencia", ignoreCase = true) == true ||
                        notif.mensaje.contains("asistencia", ignoreCase = true) ||
                        notif.mensaje.contains("falta", ignoreCase = true)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CampusTopBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Notificaciones", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        if (noLeidasTotal > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (noLeidasTotal > 99) "99+" else "$noLeidasTotal",
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (noLeidasTotal > 0) {
                        IconButton(onClick = { marcarTodasLeidas() }) {
                            Icon(
                                imageVector = Icons.Filled.DoneAll,
                                contentDescription = "Marcar todas como leídas",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filtros de categoría si hay notificaciones
            if (notificaciones.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.NotificationsNone, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(if (noLeidasTotal > 0) "Tienes $noLeidasTotal avisos sin leer" else "Estás al día",
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Tu actividad académica, en un solo lugar", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NotifFilter.values().forEach { filtro ->
                        val selected = selectedFilter == filtro
                        FilterChip(
                            selected = selected,
                            onClick = { selectedFilter = filtro },
                            label = { Text(filtro.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            when {
                cargando -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        repeat(4) { SkeletonNotificacion() }
                    }
                }
                errorMessage != null && notificaciones.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        ErrorView(
                            message = errorMessage!!,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
                notificacionesFiltradas.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            icon = Icons.Filled.NotificationsNone,
                            title = when (selectedFilter) {
                                NotifFilter.TODAS -> "Aún no hay notificaciones"
                                NotifFilter.SIN_LEER -> "Todo leído"
                                else -> "Sin notificaciones en esta categoría"
                            },
                            subtitle = if (selectedFilter == NotifFilter.SIN_LEER) "Ya revisaste todos tus avisos. Puedes consultarlos en Todas."
                                else "Aquí aparecerán los cambios de notas y asistencias. Puedes elegir la frecuencia de revisión en Ajustes."
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val today = java.time.LocalDate.now(java.time.ZoneId.of("America/Lima"))
                        val groups = notificacionesFiltradas.sortedByDescending {
                            com.example.upaos.ui.home.notificationMillis(it.fechaCreacion) ?: Long.MIN_VALUE
                        }.groupBy { notif ->
                            com.example.upaos.ui.home.notificationMillis(notif.fechaCreacion)?.let {
                                java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.of("America/Lima")).toLocalDate()
                            }
                        }
                        groups.forEach { (date, notices) ->
                            item(key = "date_$date") {
                                Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(when (date) {
                                        today -> "Hoy"
                                        today.minusDays(1) -> "Ayer"
                                        null -> "Sin fecha"
                                        else -> date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.forLanguageTag("es-PE")))
                                    }, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                                    Text(notices.size.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            items(notices, key = { it.id }) { notif ->
                                NotificacionCard(notif = notif, onClick = { marcarLeida(notif) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificacionCard(notif: NotificacionItem, onClick: () -> Unit) {
    com.example.upaos.ui.components.NotificationCard(notice = notif, onClick = onClick)
}
@Composable
private fun SkeletonNotificacion() {
    AppCard(corner = 18.dp, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            SkeletonBox(modifier = Modifier.size(44.dp), corner = 14.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                SkeletonBox(modifier = Modifier.fillMaxWidth(0.35f).height(14.dp), corner = 6.dp)
                Spacer(modifier = Modifier.height(8.dp))
                SkeletonBox(modifier = Modifier.fillMaxWidth().height(16.dp), corner = 7.dp)
                Spacer(modifier = Modifier.height(8.dp))
                SkeletonBox(modifier = Modifier.fillMaxWidth(0.3f).height(12.dp), corner = 5.dp)
            }
        }
    }
}

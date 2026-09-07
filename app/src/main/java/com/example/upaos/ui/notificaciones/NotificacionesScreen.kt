package com.example.upaos.ui.notificaciones

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.example.upaos.data.api.RetrofitClient
import com.example.upaos.data.model.NotificacionItem
import com.example.upaos.ui.components.AppCard
import com.example.upaos.ui.components.EmptyState
import com.example.upaos.ui.components.ErrorView
import com.example.upaos.ui.components.SkeletonBox
import com.example.upaos.ui.components.cursoColor
import com.example.upaos.ui.components.tiempoRelativo
import com.example.upaos.ui.components.toTitleCase
import com.example.upaos.ui.theme.UpaoOrange
import kotlinx.coroutines.launch

private enum class NotifFilter(val label: String) {
    TODAS("Todas"),
    NOTAS("Notas"),
    ASISTENCIA("Asistencias")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificacionesScreen(
    usuario: String?,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var notificaciones by remember { mutableStateOf<List<NotificacionItem>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf(NotifFilter.TODAS) }

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

    fun marcarLeida(notif: NotificacionItem) {
        if (notif.leida) return
        scope.launch {
            notificaciones = notificaciones.map {
                if (it.id == notif.id) it.copy(leida = true) else it
            }
            try {
                RetrofitClient.apiService.marcarNotificacionLeida(notif.id, usuario ?: return@launch)
            } catch (_: Exception) {
                // Silencioso
            }
        }
    }

    fun marcarTodasLeidas() {
        val noLeidas = notificaciones.filter { !it.leida }
        if (noLeidas.isEmpty() || usuario == null) return
        scope.launch {
            notificaciones = notificaciones.map { it.copy(leida = true) }
            noLeidas.forEach { notif ->
                try {
                    RetrofitClient.apiService.marcarNotificacionLeida(notif.id, usuario)
                } catch (_: Exception) {
                    // Silencioso
                }
            }
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
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Notificaciones", fontWeight = FontWeight.Bold)
                        if (noLeidasTotal > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = UpaoOrange,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "$noLeidasTotal",
                                        color = Color.White,
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
                            title = if (selectedFilter == NotifFilter.TODAS) "Aún no hay notificaciones" else "Sin notificaciones en esta categoría",
                            subtitle = "Te avisaremos cuando haya actualizaciones de notas o asistencias cada 5 minutos."
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(notificacionesFiltradas, key = { it.id }) { notif ->
                            NotificacionCard(
                                notif = notif,
                                onClick = { marcarLeida(notif) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificacionCard(
    notif: NotificacionItem,
    onClick: () -> Unit
) {
    val esFalta = notif.mensaje.contains("falta", ignoreCase = true)
    val esAsistencia = notif.componente?.contains("asistencia", ignoreCase = true) == true ||
            notif.mensaje.contains("asistencia", ignoreCase = true) || esFalta

    val categoriaLabel = when {
        esFalta -> "FALTA"
        esAsistencia -> "ASISTENCIA"
        else -> "NOTAS"
    }

    val iconVector = when {
        esFalta -> Icons.Filled.WarningAmber
        esAsistencia -> Icons.AutoMirrored.Filled.FactCheck
        else -> Icons.AutoMirrored.Filled.MenuBook
    }

    val iconContainerColor = when {
        esFalta -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
        esAsistencia -> Color(0xFFE8F5E9)
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
    }

    val iconTintColor = when {
        esFalta -> MaterialTheme.colorScheme.error
        esAsistencia -> Color(0xFF2E7D32)
        else -> MaterialTheme.colorScheme.primary
    }

    val cardColor = if (notif.leida) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }

    val borderModifier = if (!notif.leida) {
        Modifier.border(
            width = 1.dp,
            color = if (esFalta) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else UpaoOrange.copy(alpha = 0.5f),
            shape = RoundedCornerShape(18.dp)
        )
    } else {
        Modifier
    }

    AppCard(
        color = cardColor,
        onClick = onClick,
        corner = 18.dp,
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Icono grande de categoría
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = iconContainerColor,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = iconTintColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Fila superior: Chip de categoría + estado no leída
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = iconTintColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = categoriaLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = iconTintColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (!notif.componente.isNullOrBlank() && !esAsistencia) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = notif.componente.trim(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (!notif.leida) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(UpaoOrange)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "NUEVA",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = UpaoOrange
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Nombre del curso (resaltado)
                if (!notif.curso.isNullOrBlank()) {
                    Text(
                        text = toTitleCase(notif.curso),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = cursoColor(notif.curso)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                }

                // Mensaje legible y ordenado
                Text(
                    text = notif.mensaje,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (notif.leida) FontWeight.Normal else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Fila inferior: Tiempo relativo + Marcar como leída
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = tiempoRelativo(notif.fechaCreacion),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )

                    if (!notif.leida) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onClick)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Marcar leída",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
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

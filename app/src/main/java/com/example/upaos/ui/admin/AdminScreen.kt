package com.example.upaos.ui.admin

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EmojiObjects
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.upaos.data.model.AdminCuentaItem
import com.example.upaos.data.model.AdminSugerenciaItem
import com.example.upaos.data.model.DauPunto
import com.example.upaos.data.local.TokenManager
import com.example.upaos.ui.components.AppCard
import com.example.upaos.ui.components.EmptyState
import com.example.upaos.ui.components.PrimaryButton
import com.example.upaos.ui.components.SectionHeader
import com.example.upaos.ui.components.StatusBadge
import com.example.upaos.ui.components.cursoColor
import com.example.upaos.ui.theme.UpaoAmber
import com.example.upaos.ui.theme.UpaoGreen
import com.example.upaos.ui.theme.UpaoRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    usuario: String?,
    onBack: () -> Unit,
    onLogout: () -> Unit = {},
    viewModel: AdminViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var fechaInicioInput by remember { mutableStateOf("") }

    val adminUsuario = usuario ?: "000000000"
    val adminToken = remember(context) { TokenManager(context).getToken().orEmpty() }

    LaunchedEffect(adminUsuario, adminToken) {
        if (adminToken.isNotBlank()) viewModel.cargarDatosAdmin(adminToken, adminUsuario)
    }

    LaunchedEffect(state.semanaInfo) {
        state.semanaInfo?.fechaInicio?.let {
            if (fechaInicioInput.isBlank()) fechaInicioInput = it
        }
    }

    LaunchedEffect(state.mensajeExito) {
        state.mensajeExito?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.limpiarMensajeExito()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.limpiarError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Panel Administrador", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.cargarDatosAdmin(adminToken, adminUsuario) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Actualizar datos admin")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Cerrar sesión",
                            tint = MaterialTheme.colorScheme.error
                        )
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
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Dashboard", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Filled.Insights, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Cuentas (${state.cuentas.size})", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Filled.Group, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Sugerencias (${state.sugerencias.size})", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Filled.EmojiObjects, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            if (state.cargando && state.cuentas.isEmpty() && state.metricas == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Crossfade(
                    targetState = selectedTab,
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                    label = "adminTab"
                ) { tab ->
                    when (tab) {
                        0 -> DashboardTab(
                            metricas = state.metricas,
                            totalCuentas = state.cuentas.size,
                            totalSugerencias = state.sugerencias.size,
                            sugerenciasPendientes = state.sugerencias.count { it.estado == "pendiente" },
                            semanaInfo = state.semanaInfo,
                            fechaInicioInput = fechaInicioInput,
                            onFechaInicioChange = { fechaInicioInput = it },
                            onGuardarFecha = { viewModel.guardarFechaSemana(fechaInicioInput, adminToken, adminUsuario) }
                        )
                        1 -> CuentasTab(
                            cuentas = state.cuentas,
                            cuentaDetalle = state.cuentaDetalle,
                            detalleCargando = state.cuentaDetalleCargando,
                            actualizandoNotas = state.actualizandoNotas,
                            onBuscar = { usuario -> viewModel.buscarCuentaDetalle(usuario, adminToken, adminUsuario) },
                            onLimpiarDetalle = { viewModel.limpiarCuentaDetalle() },
                            onActualizarNotas = { usuario -> viewModel.actualizarNotasCuenta(usuario, adminToken, adminUsuario) }
                        )
                        else -> SugerenciasAdminTab(
                            sugerencias = state.sugerencias,
                            onCambiarEstado = { id, nuevo -> viewModel.cambiarEstadoSugerencia(id, nuevo, adminToken, adminUsuario) },
                            onGuardarNota = { id, nota -> viewModel.guardarNotaSugerencia(id, nota, adminToken, adminUsuario) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardTab(
    metricas: com.example.upaos.data.model.AdminMetricasResponse?,
    totalCuentas: Int,
    totalSugerencias: Int,
    sugerenciasPendientes: Int,
    semanaInfo: com.example.upaos.data.model.SemanaInfo?,
    fechaInicioInput: String,
    onFechaInicioChange: (String) -> Unit,
    onGuardarFecha: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(title = "Métricas y Estado del Sistema")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                titulo = "Cuentas Registradas",
                valor = "$totalCuentas",
                icono = Icons.Filled.Group,
                colorIcono = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                titulo = "Activos Hoy",
                valor = "${metricas?.cuentasActivasHoy ?: 0}",
                icono = Icons.Filled.Speed,
                colorIcono = UpaoGreen,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                titulo = "Pico Hoy (Simultáneos)",
                valor = "${metricas?.picoHoy?.usuariosSimultaneos ?: 0}",
                subtitulo = metricas?.picoHoy?.fechaHora ?: "Sin actividad hoy",
                icono = Icons.Filled.Insights,
                colorIcono = UpaoAmber,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                titulo = "Pico Histórico",
                valor = "${metricas?.picoHistorico?.usuariosSimultaneos ?: 0}",
                subtitulo = metricas?.picoHistorico?.fechaHora ?: "Sin datos",
                icono = Icons.Filled.AdminPanelSettings,
                colorIcono = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
        }

        AppCard(corner = 14.dp, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sugerencias Pendientes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Total registradas: $totalSugerencias", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusBadge(
                    text = "$sugerenciasPendientes pendientes",
                    color = if (sugerenciasPendientes > 0) UpaoAmber else UpaoGreen
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        SectionHeader(title = "Usuarios Activos por Día (DAU)")
        AppCard(corner = 14.dp, modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Últimos 30 días", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if ((metricas?.dau30Dias?.maxOfOrNull { it.usuarios } ?: 0) > 0) {
                        StatusBadge(
                            text = "pico: ${metricas?.dau30Dias?.maxOfOrNull { it.usuarios }}",
                            color = UpaoGreen
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                DauBarChart(datos = metricas?.dau30Dias.orEmpty())
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Error de sesión (Banner): ${metricas?.erroresSesion24h ?: 0} en 24h · ${metricas?.erroresSesion7d ?: 0} en 7d",
                    style = MaterialTheme.typography.bodySmall,
                    color = if ((metricas?.erroresSesion7d ?: 0) > 0) UpaoRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                titulo = "Errores sesión 24h",
                valor = "${metricas?.erroresSesion24h ?: 0}",
                subtitulo = if ((metricas?.erroresSesion7d ?: 0) > 0) "Revisar credenciales" else "Todo en orden",
                icono = Icons.Filled.Warning,
                colorIcono = if ((metricas?.erroresSesion24h ?: 0) > 0) UpaoRed else UpaoGreen,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                titulo = "Errores sesión 7d",
                valor = "${metricas?.erroresSesion7d ?: 0}",
                icono = Icons.Filled.Error,
                colorIcono = if ((metricas?.erroresSesion7d ?: 0) > 0) UpaoAmber else UpaoGreen,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        SectionHeader(title = "Configuración de Semana Académica")

        AppCard(corner = 14.dp, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("Fecha Inicio de Ciclo Lectivo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (semanaInfo?.configurada == true) "Estado actual: ${semanaInfo.etiqueta}" else "Aún no configurada",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = fechaInicioInput,
                    onValueChange = onFechaInicioChange,
                    label = { Text("Fecha de inicio (YYYY-MM-DD)", fontSize = 12.sp) },
                    placeholder = { Text("Ej. 2026-03-23") },
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                PrimaryButton(
                    text = "Guardar fecha de inicio",
                    onClick = onGuardarFecha,
                    height = 44.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun MetricCard(
    titulo: String,
    valor: String,
    subtitulo: String? = null,
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    colorIcono: Color,
    modifier: Modifier = Modifier
) {
    AppCard(corner = 14.dp, modifier = modifier) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = colorIcono.copy(alpha = 0.15f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = icono, contentDescription = null, tint = colorIcono, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(titulo, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(valor, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            if (!subtitulo.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitulo, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CuentasTab(
    cuentas: List<AdminCuentaItem>,
    cuentaDetalle: com.example.upaos.data.model.AdminCuentaDetalle?,
    detalleCargando: Boolean,
    actualizandoNotas: Boolean,
    onBuscar: (String) -> Unit,
    onLimpiarDetalle: () -> Unit,
    onActualizarNotas: (String) -> Unit
) {
    var busqueda by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        AppCard(corner = 14.dp, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = busqueda,
                    onValueChange = { busqueda = it.filter(Char::isDigit).take(9) },
                    label = { Text("Buscar por número de usuario") },
                    placeholder = { Text("Ej. 000123456") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(onClick = { if (busqueda.isNotBlank()) onBuscar(busqueda) }) {
                    Icon(Icons.Filled.Search, contentDescription = "Buscar cuenta")
                }
            }
        }

        when {
            detalleCargando -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            cuentaDetalle != null -> CuentaDetalleCard(
                detalle = cuentaDetalle,
                actualizandoNotas = actualizandoNotas,
                onActualizarNotas = { onActualizarNotas(cuentaDetalle.usuario) },
                onCerrar = onLimpiarDetalle
            )
            else -> CuentasLista(cuentas = cuentas)
        }
    }
}

@Composable
private fun CuentasLista(cuentas: List<AdminCuentaItem>) {
    if (cuentas.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(icon = Icons.Filled.Group, title = "Sin cuentas registradas", subtitle = "No hay cuentas registradas en el backend.")
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(cuentas, key = { it.usuario }) { cuenta ->
                AppCard(corner = 14.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(cursoColor(cuenta.nombre ?: cuenta.usuario)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (cuenta.nombre ?: cuenta.usuario).trim().firstOrNull()?.uppercase() ?: "?",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cuenta.nombre?.takeIf { it.isNotBlank() } ?: "Usuario UPAO",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Código: ${cuenta.usuario}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (cuenta.fechaPrimerLogin != null) {
                                Text(
                                    text = "Primer login: ${cuenta.fechaPrimerLogin.substringBefore("T")}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        if (cuenta.isAdmin) {
                            StatusBadge(text = "Admin", color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CuentaDetalleCard(
    detalle: com.example.upaos.data.model.AdminCuentaDetalle,
    actualizandoNotas: Boolean,
    onActualizarNotas: () -> Unit,
    onCerrar: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Detalle de la cuenta",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onCerrar) { Text("Volver a la lista") }
        }

        AppCard(corner = 14.dp, modifier = Modifier.fillMaxWidth()) {
            Column {
                DetalleFila("Nombre", detalle.nombre?.takeIf { it.isNotBlank() } ?: "Usuario UPAO")
                DetalleFila("Código", detalle.usuario)
                DetalleFila("Rol", if (detalle.isAdmin) "Administrador" else "Estudiante")
                DetalleFila("Auto-check notas", if (detalle.autoCheckEnabled) "Activado" else "Desactivado")
                DetalleFila("Intervalo de chequeo", "${detalle.intervaloChequeoMinutos} min")
                DetalleFila("Primer login", detalle.fechaPrimerLogin?.substringBefore("T") ?: "Nunca")
                DetalleFila("Última revisión", detalle.ultimaRevision?.substringBefore("T") ?: "Nunca")
                DetalleFila("Token FCM", if (detalle.tieneFcmToken) "Registrado" else "No registrado")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sesión Banner",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.45f)
                    )
                    val (colorSesion, etiquetaSesion) = when (detalle.estadoSesionBanner) {
                        "activa" -> UpaoGreen to "Activa"
                        "nunca_logueado" -> MaterialTheme.colorScheme.primary to "Nunca logueado"
                        else -> UpaoRed to "Expirada"
                    }
                    StatusBadge(text = etiquetaSesion, color = colorSesion)
                }
            }
        }

        PrimaryButton(
            text = if (actualizandoNotas) "Actualizando notas..." else "Actualizar notas ahora",
            onClick = onActualizarNotas,
            loading = actualizandoNotas,
            height = 46.dp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DetalleFila(etiqueta: String, valor: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f)
        )
        Text(
            text = valor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.55f)
        )
    }
}

@Composable
private fun SugerenciasAdminTab(
    sugerencias: List<AdminSugerenciaItem>,
    onCambiarEstado: (Long, String) -> Unit,
    onGuardarNota: (Long, String) -> Unit
) {
    var filtroEstado by remember { mutableStateOf("todas") }
    val opcionesFiltro = listOf(
        "todas" to "Todas",
        "pendiente" to "Pendiente",
        "en_revision" to "Revisando",
        "aprobada" to "Aprobadas",
        "rechazada" to "Rechazadas"
    )
    val filtradas = if (filtroEstado == "todas") sugerencias else sugerencias.filter { it.estado == filtroEstado }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(opcionesFiltro, key = { it.first }) { (clave, etiqueta) ->
                FilterChip(
                    selected = filtroEstado == clave,
                    onClick = { filtroEstado = clave },
                    label = { Text(etiqueta, fontSize = 11.sp) }
                )
            }
        }

        if (filtradas.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.EmojiObjects,
                    title = "Sin sugerencias",
                    subtitle = if (filtroEstado == "todas") "No se ha recibido ninguna sugerencia de los usuarios." else "No hay sugerencias en este estado."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtradas, key = { it.id }) { sug ->
                    val (colorEstado, etiquetaEstado) = when (sug.estado) {
                        "aprobada" -> UpaoGreen to "Aprobada"
                        "en_revision" -> UpaoAmber to "En revisión"
                        "rechazada" -> UpaoRed to "Rechazada"
                        else -> MaterialTheme.colorScheme.primary to "Pendiente"
                    }
                    var notaLocal by remember(sug.id) { mutableStateOf(sug.notaAdmin ?: "") }
                    var notaDirty by remember(sug.id) { mutableStateOf(false) }

                    AppCard(corner = 14.dp, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Usuario ${sug.usuario}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                StatusBadge(text = etiquetaEstado, color = colorEstado)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = sug.texto,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (sug.fechaCreacion != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Enviado: ${sug.fechaCreacion.substringBefore("T")}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = notaLocal,
                                onValueChange = { notaLocal = it; notaDirty = true },
                                label = { Text("Nota interna del admin") },
                                placeholder = { Text("Opcional: observación para el equipo") },
                                shape = RoundedCornerShape(12.dp),
                                minLines = 1,
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FilterChip(
                                    selected = sug.estado == "pendiente",
                                    onClick = { onCambiarEstado(sug.id, "pendiente") },
                                    label = { Text("Pendiente", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = sug.estado == "en_revision",
                                    onClick = { onCambiarEstado(sug.id, "en_revision") },
                                    label = { Text("Revisando", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = sug.estado == "aprobada",
                                    onClick = { onCambiarEstado(sug.id, "aprobada") },
                                    label = { Text("Aprobada", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = sug.estado == "rechazada",
                                    onClick = { onCambiarEstado(sug.id, "rechazada") },
                                    label = { Text("Rechazada", fontSize = 11.sp) }
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                if (notaDirty) {
                                    TextButton(
                                        onClick = {
                                            onGuardarNota(sug.id, notaLocal.trim())
                                            notaDirty = false
                                        }
                                    ) {
                                        Text("Guardar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DauBarChart(
    datos: List<DauPunto>,
    modifier: Modifier = Modifier
) {
    val colorBarra = MaterialTheme.colorScheme.primary
    val colorTrack = MaterialTheme.colorScheme.surfaceVariant
    val maxUsuarios = (datos.maxOfOrNull { it.usuarios } ?: 0).coerceAtLeast(1)

    if (datos.isEmpty()) {
        Text(
            text = "Sin datos de actividad aún",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        return
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        val paso = size.width / datos.size
        val barraAncho = (paso * 0.6f).coerceIn(2f, 10f)
        datos.forEachIndexed { index, punto ->
            val alto = (punto.usuarios.toFloat() / maxUsuarios) * size.height
            val x = index * paso + (paso - barraAncho) / 2f
            drawRoundRect(
                color = colorTrack,
                topLeft = Offset(x, 0f),
                size = Size(barraAncho, size.height),
                cornerRadius = CornerRadius(barraAncho / 2f)
            )
            if (alto > 0f) {
                drawRoundRect(
                    color = colorBarra,
                    topLeft = Offset(x, size.height - alto),
                    size = Size(barraAncho, alto),
                    cornerRadius = CornerRadius(barraAncho / 2f)
                )
            }
        }
    }
}

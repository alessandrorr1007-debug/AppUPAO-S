package com.example.upaos.ui.asistencia

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.upaos.data.api.RetrofitClient
import com.example.upaos.data.api.esErrorSesionExpirada
import com.example.upaos.data.local.ApiCache
import com.example.upaos.data.model.AsistenciaCurso
import com.example.upaos.data.model.AsistenciaResponse
import com.example.upaos.ui.components.AppCard
import com.example.upaos.ui.components.CircularGauge
import com.example.upaos.ui.components.EmptyState
import com.example.upaos.ui.components.ErrorView
import com.example.upaos.ui.components.SectionHeader
import com.example.upaos.ui.components.SkeletonBox
import com.example.upaos.ui.components.StatusBadge
import com.example.upaos.ui.components.cursoColor
import com.example.upaos.ui.components.toTitleCase
import com.example.upaos.ui.theme.UpaoAmber
import com.example.upaos.ui.theme.UpaoGreen
import com.example.upaos.ui.theme.UpaoRed
import com.google.gson.Gson
import kotlinx.coroutines.launch

private val dayNames = listOf("LUN", "MAR", "MIE", "JUE", "VIE", "SAB", "DOM")
private val dayInitials = listOf("L", "M", "M", "J", "V", "S", "D")

private fun sinAcentos(s: String): String =
    s.uppercase()
        .replace("Á", "A").replace("É", "E").replace("Í", "I")
        .replace("Ó", "O").replace("Ú", "U")

private fun diasActivos(horario: String?): Set<Int> {
    val h = horario?.let { sinAcentos(it) } ?: return emptySet()
    return dayNames.mapIndexedNotNull { index, day -> if (h.contains(day)) index else null }.toSet()
}

private fun porcentajeColor(pct: Double): Color = when {
    pct >= 90 -> UpaoGreen
    pct >= 70 -> UpaoAmber
    else -> UpaoRed
}

private fun estadoAsistencia(pct: Double): Pair<String, Color> = when {
    pct >= 90 -> "Óptimo" to UpaoGreen
    pct >= 70 -> "Aceptable" to UpaoAmber
    else -> "En riesgo" to UpaoRed
}

private fun formatPct(pct: Double): String =
    if (pct % 1.0 == 0.0) pct.toInt().toString() else pct.toString()

@Composable
fun AsistenciaContent(
    token: String,
    usuario: String? = null,
    onSesionExpirada: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cache = remember { ApiCache(context) }
    val gson = remember { Gson() }
    val claveCache = "asistencia_${usuario ?: "anonimo"}"

    var registros by remember { mutableStateOf<List<AsistenciaCurso>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var sesionExpirada by remember { mutableStateOf(false) }

    fun aplicarCache() {
        scope.launch {
            try {
                if (registros.isNotEmpty()) return@launch
                val json = cache.cargar(claveCache) ?: return@launch
                val body = gson.fromJson(json, AsistenciaResponse::class.java)
                registros = body.asistencia
                Log.d("UPAO_APP", "[Android UI] Caché aplicada: ${registros.size} registros de asistencia")
            } catch (e: Exception) {
                Log.e("UPAO_APP", "[Android UI] Error leyendo caché de asistencia: ${e.localizedMessage}", e)
            }
        }
    }

    fun load() {
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                Log.d("UPAO_APP", "[Android UI] Consultando asistencia...")
                val res = RetrofitClient.apiService.getAsistencia("Bearer $token")
                isLoading = false
                val errBody = res.errorBody()?.string()
                if (esErrorSesionExpirada(res.code(), errBody)) {
                    sesionExpirada = true
                    return@launch
                }
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    registros = body.asistencia
                    scope.launch { cache.guardar(claveCache, gson.toJson(body)) }
                } else {
                    val err = errBody ?: "Error desconocido"
                    errorMessage = "Error HTTP ${res.code()}: $err"
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                isLoading = false
                errorMessage = "Excepción: ${e.localizedMessage}"
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        aplicarCache()
        load()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Asistencia",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Resumen de inasistencias del periodo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(42.dp)
                ) {
                    IconButton(onClick = { load() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Actualizar Asistencia",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (isLoading && registros.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.8.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when {
            isLoading && registros.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(3) { SkeletonAsistenciaCard() }
                }
            }
            errorMessage != null && registros.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    ErrorView(
                        message = errorMessage!!,
                        onRetry = { load() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            registros.isEmpty() -> {
                EmptyState(
                    icon = Icons.Filled.Schedule,
                    title = "Sin datos de asistencia",
                    subtitle = "No hay datos de asistencia para este periodo.",
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            else -> {
                val conNota = registros.mapNotNull { it.porcentaje }
                val promedio = if (conNota.isNotEmpty()) conNota.average() else 0.0
                val enRiesgo = registros.filter { (it.porcentaje ?: 0.0) < 70.0 }
                val optimos = registros.count { (it.porcentaje ?: 0.0) >= 90.0 }
                val resto = registros.filterNot { it in enRiesgo }

                Column(modifier = Modifier.fillMaxSize()) {
                    ResumenAsistencia(promedio, registros.size, enRiesgo.size, optimos)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (enRiesgo.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Cursos en riesgo",
                                    subtitle = "Asistencia menor al 70%",
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                            items(enRiesgo) { curso -> AsistenciaCard(curso) }
                            if (resto.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        title = "Todos los cursos",
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        if (resto.isNotEmpty()) {
                            items(resto) { curso -> AsistenciaCard(curso) }
                        }
                    }
                }
            }
        }
    }

    if (sesionExpirada) {
        AlertDialog(
            onDismissRequest = { sesionExpirada = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Sesión expirada", fontWeight = FontWeight.Bold) },
            text = { Text("Tu sesión expiró, por favor inicia sesión de nuevo.") },
            confirmButton = {
                TextButton(onClick = {
                    sesionExpirada = false
                    onSesionExpirada()
                }) {
                    Text("Iniciar sesión", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { sesionExpirada = false }) {
                    Text("Ahora no")
                }
            }
        )
    }
}

@Composable
private fun ResumenAsistencia(promedio: Double, totalCursos: Int, enRiesgo: Int, optimos: Int) {
    val color = porcentajeColor(promedio)
    val (estado, _) = estadoAsistencia(promedio)
    AppCard(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        corner = 14.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularGauge(
                progress = (promedio / 100f).toFloat().coerceIn(0f, 1f),
                centerValue = "${formatPct(promedio)}%",
                size = 52.dp,
                strokeWidth = 6.dp,
                gaugeColor = color
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Promedio de asistencia",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                StatusBadge(text = estado, color = color)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatChip(label = "Cursos", value = "$totalCursos", color = MaterialTheme.colorScheme.onSurface)
                StatChip(
                    label = "Riesgo",
                    value = "$enRiesgo",
                    color = if (enRiesgo > 0) UpaoRed else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(2.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AsistenciaCard(item: AsistenciaCurso) {
    val pct = item.porcentaje ?: 0.0
    val color = porcentajeColor(pct)
    val (estado, _) = estadoAsistencia(pct)
    val activos = diasActivos(item.horarioDias)
    val courseColor = cursoColor(item.displayNombre)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                .background(color)
        )
        AppCard(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            corner = 14.dp,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = toTitleCase(item.displayNombre),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        val meta = buildString {
                            append("CRN ${item.crn ?: "-"}")
                            item.seccion?.let { append(" · Sec $it") }
                        }
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    StatusBadge(text = estado, color = color)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { (pct / 100f).toFloat().coerceIn(0f, 1f) },
                        color = color,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Text(
                        text = "${formatPct(pct)}%",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        dayNames.forEachIndexed { index, _ ->
                            val activo = index in activos
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (activo) courseColor else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dayInitials[index],
                                    fontSize = 10.sp,
                                    fontWeight = if (activo) FontWeight.Bold else FontWeight.Medium,
                                    color = if (activo) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Text(
                        text = "${item.faltas ?: 0} faltas",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SkeletonAsistenciaCard() {
    AppCard(corner = 14.dp, contentPadding = PaddingValues(10.dp)) {
        Column {
            SkeletonBox(modifier = Modifier.fillMaxWidth(0.7f).height(14.dp), corner = 7.dp)
            Spacer(modifier = Modifier.height(6.dp))
            SkeletonBox(modifier = Modifier.fillMaxWidth(0.45f).height(10.dp), corner = 5.dp)
            Spacer(modifier = Modifier.height(8.dp))
            SkeletonBox(modifier = Modifier.fillMaxWidth().height(8.dp), corner = 4.dp)
        }
    }
}

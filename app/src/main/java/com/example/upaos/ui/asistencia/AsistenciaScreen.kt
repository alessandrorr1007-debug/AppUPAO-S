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
import com.example.upaos.data.model.AsistenciaCurso
import com.example.upaos.ui.theme.UpaoAmber
import com.example.upaos.ui.theme.UpaoGreen
import com.example.upaos.ui.theme.UpaoOrange
import com.example.upaos.ui.theme.UpaoRed
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

private fun formatPct(pct: Double): String =
    if (pct % 1.0 == 0.0) pct.toInt().toString() else pct.toString()

@Composable
fun AsistenciaContent(token: String, onSesionExpirada: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var registros by remember { mutableStateOf<List<AsistenciaCurso>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var sesionExpirada by remember { mutableStateOf(false) }

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
                    Log.e("UPAO_APP", "[Android UI] Sesión expirada detectada (401 sesion_expirada)")
                    sesionExpirada = true
                    return@launch
                }
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    registros = body.asistencia
                    Log.d("UPAO_APP", "[Android UI] Asistencia recibida: ${registros.size} registros")
                } else {
                    val err = errBody ?: "Error desconocido"
                    Log.e("UPAO_APP", "[Android UI] Error HTTP ${res.code()}: $err")
                    errorMessage = "Error HTTP ${res.code()}: $err"
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                isLoading = false
                Log.e("UPAO_APP", "[Android UI] Excepción al cargar asistencia: ${e.localizedMessage}", e)
                errorMessage = "Excepción: ${e.localizedMessage}"
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Resumen de inasistencias del periodo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { load() }) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Actualizar Asistencia",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            errorMessage != null -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Error de Consulta", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = errorMessage!!, fontSize = 13.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            registros.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Sin datos de asistencia disponibles", color = MaterialTheme.colorScheme.outline)
                }
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(registros) { item ->
                        AsistenciaCard(item)
                    }
                }
            }
        }
    }

    if (sesionExpirada) {
        AlertDialog(
            onDismissRequest = { sesionExpirada = false },
            title = { Text("Sesión expirada") },
            text = { Text("Tu sesión expiró, por favor inicia sesión de nuevo.") },
            confirmButton = {
                TextButton(onClick = {
                    sesionExpirada = false
                    onSesionExpirada()
                }) {
                    Text("Iniciar sesión")
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
fun AsistenciaCard(item: AsistenciaCurso) {
    val pct = item.porcentaje ?: 0.0
    val color = porcentajeColor(pct)
    val activos = diasActivos(item.horarioDias)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = item.displayNombre,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            val meta = buildString {
                append("CRN ${item.crn ?: "-"}")
                item.seccion?.let { append(" · Sección $it") }
                item.hora12h?.let { append(" · $it") }
            }
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Asistencia",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${item.faltas ?: 0} faltas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LinearProgressIndicator(
                    progress = { (pct / 100f).toFloat().coerceIn(0f, 1f) },
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                )
                Text(
                    text = "${formatPct(pct)}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                dayNames.forEachIndexed { index, _ ->
                    val activo = index in activos
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(
                                if (activo) UpaoOrange else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dayInitials[index],
                            fontSize = 12.sp,
                            fontWeight = if (activo) FontWeight.Bold else FontWeight.Medium,
                            color = if (activo) Color(0xFF271700) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.horarioDias ?: "Sin horario registrado",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

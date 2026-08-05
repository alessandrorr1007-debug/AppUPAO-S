package com.example.upaos.ui.horario

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Room
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
import com.example.upaos.data.model.HorarioBloque
import com.example.upaos.data.model.HorarioCurso
import com.example.upaos.data.model.HorarioResponse
import com.example.upaos.ui.components.AppCard
import com.example.upaos.ui.components.EmptyState
import com.example.upaos.ui.components.ErrorView
import com.example.upaos.ui.components.SkeletonBox
import com.example.upaos.ui.components.cursoColor
import com.example.upaos.ui.components.toTitleCase
import com.example.upaos.ui.grades.detectarPeriodoActual
import com.google.gson.Gson
import java.util.Calendar
import kotlinx.coroutines.launch

private fun formatDiaNombre(dia: String?): String {
    if (dia.isNullOrBlank()) return "—"
    val d = dia.uppercase().trim()
    return when {
        d.startsWith("LUN") || d == "L" -> "Lunes"
        d.startsWith("MAR") || d == "M" -> "Martes"
        d.startsWith("MIE") || d.startsWith("MIÉ") || d == "MI" -> "Miércoles"
        d.startsWith("JUE") || d == "J" -> "Jueves"
        d.startsWith("VIE") || d == "V" -> "Viernes"
        d.startsWith("SAB") || d.startsWith("SÁB") || d == "S" -> "Sábado"
        d.startsWith("DOM") || d == "D" -> "Domingo"
        else -> dia.lowercase().replaceFirstChar { it.uppercase() }
    }
}

private fun diaHoy(): Int = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7

private fun minutosAhora(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}

private fun minutosDe(hhmm: String?): Int? {
    if (hhmm.isNullOrBlank()) return null
    val partes = hhmm.split(":")
    if (partes.size < 2) return null
    return (partes[0].toIntOrNull() ?: return null) * 60 + (partes[1].toIntOrNull() ?: return null)
}

private data class ProximaClase(
    val curso: HorarioCurso,
    val bloque: HorarioBloque,
    val diasRestantes: Int,
    val esAhora: Boolean,
    val inicio: Int
)

private fun calcularProximaClase(cursos: List<HorarioCurso>): ProximaClase? {
    val hoy = diaHoy()
    val ahora = minutosAhora()
    var mejor: ProximaClase? = null
    for (curso in cursos) {
        for (bloque in curso.bloques) {
            val dia = bloque.dia ?: continue
            val inicio = minutosDe(bloque.horaInicio) ?: continue
            val fin = minutosDe(bloque.horaFin) ?: inicio
            val restantes = (dia - hoy + 7) % 7
            val esAhora = restantes == 0 && inicio <= ahora && ahora < fin
            if (esAhora) return ProximaClase(curso, bloque, restantes, true, inicio)
            if (restantes == 0 && inicio <= ahora) continue
            val candidato = ProximaClase(curso, bloque, restantes, false, inicio)
            val m = mejor
            if (m == null ||
                restantes < m.diasRestantes ||
                (restantes == m.diasRestantes && inicio < m.inicio)
            ) {
                mejor = candidato
            }
        }
    }
    return mejor
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HorarioContent(
    token: String,
    usuario: String? = null,
    onSesionExpirada: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cache = remember { ApiCache(context) }
    val gson = remember { Gson() }

    var periodos by remember { mutableStateOf(listOf("202610")) }
    var selectedPeriodo by remember { mutableStateOf("202610") }
    var periodosExpanded by remember { mutableStateOf(false) }

    var cursos by remember { mutableStateOf<List<HorarioCurso>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var sesionExpirada by remember { mutableStateOf(false) }

    fun claveCache(): String = "horario_${usuario ?: "anonimo"}_$selectedPeriodo"

    fun aplicarCache() {
        scope.launch {
            try {
                if (cursos.isNotEmpty()) return@launch
                val json = cache.cargar(claveCache()) ?: return@launch
                val body = gson.fromJson(json, HorarioResponse::class.java)
                cursos = body.cursos
                Log.d("UPAO_APP", "[Android UI] Caché aplicada: ${cursos.size} cursos de horario")
            } catch (e: Exception) {
                Log.e("UPAO_APP", "[Android UI] Error leyendo caché de horario: ${e.localizedMessage}", e)
            }
        }
    }

    fun loadHorario() {
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                Log.d("UPAO_APP", "[Android UI] Consultando horario para term=$selectedPeriodo...")
                val res = RetrofitClient.apiService.getHorario("Bearer $token", selectedPeriodo)
                isLoading = false
                val errBody = res.errorBody()?.string()
                if (esErrorSesionExpirada(res.code(), errBody)) {
                    sesionExpirada = true
                    return@launch
                }
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    cursos = body.cursos
                    scope.launch { cache.guardar(claveCache(), gson.toJson(body)) }
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
        try {
            val periodosRes = RetrofitClient.apiService.getPeriodos("Bearer $token")
            if (periodosRes.isSuccessful && periodosRes.body() != null) {
                val body = periodosRes.body()!!
                periodos = body.periodos
                selectedPeriodo = detectarPeriodoActual(body.periodos, body.periodoActual)
            }
            aplicarCache()
            loadHorario()
        } catch (e: Exception) {
            aplicarCache()
            loadHorario()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExposedDropdownMenuBox(
                expanded = periodosExpanded,
                onExpandedChange = { periodosExpanded = !periodosExpanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = selectedPeriodo,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Periodo", fontSize = 12.sp) },
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = periodosExpanded) },
                    modifier = Modifier.menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = periodosExpanded,
                    onDismissRequest = { periodosExpanded = false }
                ) {
                    periodos.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item, fontSize = 13.sp) },
                            onClick = {
                                selectedPeriodo = item
                                periodosExpanded = false
                                loadHorario()
                            }
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(46.dp)
            ) {
                IconButton(onClick = { loadHorario() }) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Actualizar Horario",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (isLoading && cursos.isNotEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.8.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when {
            isLoading && cursos.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(3) { SkeletonHorarioCard() }
                }
            }
            errorMessage != null && cursos.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    ErrorView(
                        message = errorMessage!!,
                        onRetry = { loadHorario() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            cursos.isEmpty() -> {
                EmptyState(
                    icon = Icons.Filled.Schedule,
                    title = "Sin horario publicado",
                    subtitle = "No hay horario para el periodo $selectedPeriodo.",
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            else -> {
                val proxima = calcularProximaClase(cursos)
                Column(modifier = Modifier.fillMaxSize()) {
                    if (proxima != null) {
                        ProximaClaseCard(proxima)
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(cursos) { curso ->
                            HorarioCursoCard(curso)
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
private fun ProximaClaseCard(proxima: ProximaClase) {
    val color = cursoColor(proxima.curso.displayNombre)
    val bloque = proxima.bloque
    val etiqueta = when {
        proxima.esAhora -> "En curso ahora"
        proxima.diasRestantes == 0 -> "Hoy"
        else -> bloque.diaNombre ?: ""
    }
    AppCard(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        corner = 14.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = color,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PRÓXIMA CLASE",
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = etiqueta,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = toTitleCase(proxima.curso.displayNombre),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = listOf(
                        bloque.horaInicio12h ?: bloque.horaInicio,
                        bloque.horaFin12h ?: bloque.horaFin
                    ).filterNotNull().joinToString(" - "),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (!bloque.aula.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Room,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = bloque.aula!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HorarioCursoCard(curso: HorarioCurso) {
    val color = cursoColor(curso.displayNombre)
    val hoy = diaHoy()
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
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(color.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = curso.displayNombre.trim().firstOrNull()?.uppercase() ?: "?",
                            color = color,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = toTitleCase(curso.displayNombre),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        if (curso.displayCodigo.isNotBlank()) {
                            Text(
                                text = curso.displayCodigo,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                curso.bloques.forEach { bloque ->
                    val esHoy = bloque.dia == hoy
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (esHoy) color.copy(alpha = 0.10f) else Color.Transparent)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = formatDiaNombre(bloque.diaNombre),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (esHoy) color else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(75.dp)
                        )
                        Icon(
                            imageVector = Icons.Filled.AccessTime,
                            contentDescription = null,
                            tint = if (esHoy) color else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${bloque.horaInicio12h ?: bloque.horaInicio ?: "—"} — ${bloque.horaFin12h ?: bloque.horaFin ?: "—"}",
                                fontSize = 12.sp,
                                fontWeight = if (esHoy) FontWeight.SemiBold else FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!bloque.aula.isNullOrBlank()) {
                                Text(
                                    text = bloque.aula!!,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonHorarioCard() {
    AppCard(corner = 14.dp, contentPadding = PaddingValues(10.dp)) {
        Column {
            SkeletonBox(modifier = Modifier.fillMaxWidth(0.7f).height(14.dp), corner = 7.dp)
            Spacer(modifier = Modifier.height(6.dp))
            SkeletonBox(modifier = Modifier.fillMaxWidth(0.4f).height(10.dp), corner = 5.dp)
            Spacer(modifier = Modifier.height(10.dp))
            SkeletonBox(modifier = Modifier.fillMaxWidth().height(32.dp), corner = 8.dp)
        }
    }
}

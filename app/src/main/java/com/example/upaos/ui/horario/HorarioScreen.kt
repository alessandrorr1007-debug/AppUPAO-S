package com.example.upaos.ui.horario

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.upaos.data.api.RetrofitClient
import com.example.upaos.data.local.ApiCache
import com.example.upaos.data.model.HorarioBloque
import com.example.upaos.data.model.HorarioCurso
import com.example.upaos.data.model.HorarioResponse
import com.example.upaos.ui.components.AppCard
import com.example.upaos.ui.components.EmptyState
import com.example.upaos.ui.components.ErrorView
import com.example.upaos.ui.components.RefreshableContent
import com.example.upaos.ui.components.SkeletonBox
import com.example.upaos.ui.components.cursoColor
import com.example.upaos.ui.components.toTitleCase
import com.example.upaos.ui.grades.detectarPeriodoActual
import com.example.upaos.widget.ProximoCursoWidget
import com.google.gson.Gson
import java.util.Calendar
import kotlinx.coroutines.launch

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
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var offline by remember { mutableStateOf(false) }
    fun claveCache(): String = "horario_${usuario ?: "anonimo"}_$selectedPeriodo"

    fun aplicarCache() {
        scope.launch {
            try {
                if (cursos.isNotEmpty()) return@launch
                var json = cache.cargar(claveCache())
                if (json == null) {
                    val prefijo = "horario_${usuario ?: "anonimo"}_"
                    val todas = cache.listarPorPrefijo(prefijo)
                    val periodoEnCache = todas.keys
                        .mapNotNull { it.removePrefix(prefijo) }
                        .sorted()
                        .lastOrNull()
                    if (periodoEnCache != null) {
                        selectedPeriodo = periodoEnCache
                        json = todas[prefijo + periodoEnCache]
                    }
                }
                if (json == null) return@launch
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
        offline = false
        scope.launch {
            try {
                Log.d("UPAO_APP", "[Android UI] Consultando horario para term=$selectedPeriodo...")
                val res = RetrofitClient.apiService.getHorario("Bearer $token", selectedPeriodo)
                isLoading = false
                isRefreshing = false
                val errBody = res.errorBody()?.string()
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    cursos = body.cursos
                    scope.launch { cache.guardar(claveCache(), gson.toJson(body)) }
                    // Actualiza el widget de próxima clase
                    try {
                        ProximoCursoWidget.updateAll(context)
                    } catch (e: Exception) {
                        // Widget no instalado
                    }
                } else {
                    val err = errBody ?: "Error desconocido"
                    errorMessage = "Error HTTP ${res.code()}: $err"
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                isLoading = false
                isRefreshing = false
                if (cursos.isNotEmpty()) {
                    offline = true
                } else {
                    errorMessage = "Sin conexión: ${e.localizedMessage}"
                }
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
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedPeriodo,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Periodo", fontSize = 12.sp) },
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = periodosExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
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
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (offline && cursos.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = "Sin conexión · Mostrando horario guardado",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

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
                RefreshableContent(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        isRefreshing = true
                        loadHorario()
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (proxima != null) {
                            ProximaClaseCard(proxima)
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        HorarioSemanalGrid(cursos, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProximaClaseCard(proxima: ProximaClase) {
    val color = cursoColor(proxima.curso.displayNombre)
    val bloque = proxima.bloque
    val etiqueta = when {
        proxima.esAhora -> "• En curso ahora"
        proxima.diasRestantes == 0 -> "• Hoy"
        else -> "• ${bloque.diaNombre ?: ""}"
    }
    val hora = listOf(
        bloque.horaInicio12h ?: bloque.horaInicio,
        bloque.horaFin12h ?: bloque.horaFin
    ).filterNotNull().joinToString(" - ")
    AppCard(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        corner = 12.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = "PRÓXIMA CLASE",
                fontSize = 9.sp,
                letterSpacing = 0.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = toTitleCase(proxima.curso.displayNombre),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = hora,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1
            )
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

private val ColumnaDia = 140.dp

private val NOMBRES_DIAS = listOf("LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB")

private data class BloqueProgramado(
    val curso: HorarioCurso,
    val bloque: HorarioBloque
)

@Composable
private fun HorarioSemanalGrid(cursos: List<HorarioCurso>, modifier: Modifier = Modifier) {
    val hoy = diaHoy() // 0 = Lun, 1 = Mar, ..., 5 = Sáb, 6 = Dom
    val horScroll = rememberScrollState()
    val vertScroll = rememberScrollState()

    val porDia: Map<Int, List<BloqueProgramado>> = cursos.flatMap { curso ->
        curso.bloques
            .filter { (it.dia ?: -1) in 0..5 }
            .map { BloqueProgramado(curso, it) }
    }.groupBy { it.bloque.dia!! }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 24.dp)
            .verticalScroll(vertScroll)
    ) {
        Row(modifier = Modifier.horizontalScroll(horScroll)) {
            NOMBRES_DIAS.forEachIndexed { index, nombre ->
                val esHoy = index == hoy
                Column(
                    modifier = Modifier
                        .width(ColumnaDia)
                        .padding(horizontal = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (esHoy) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = nombre,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (esHoy) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(horScroll)
        ) {
            NOMBRES_DIAS.forEachIndexed { index, _ ->
                val clases = porDia[index] ?: emptyList()
                Column(
                    modifier = Modifier
                        .width(ColumnaDia)
                        .padding(horizontal = 3.dp)
                ) {
                    if (clases.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "—",
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        clases.sortedBy { minutosDe(it.bloque.horaInicio) ?: 0 }.forEach { c ->
                            BloqueColumna(c.curso, c.bloque, esHoy = index == hoy)
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BloqueColumna(curso: HorarioCurso, bloque: HorarioBloque, esHoy: Boolean) {
    val color = cursoColor(curso.displayNombre)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (esHoy) color.copy(alpha = 0.12f) else color.copy(alpha = 0.06f))
            .padding(8.dp)
    ) {
        Text(
            text = toTitleCase(curso.displayNombre),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (esHoy) color else MaterialTheme.colorScheme.onSurface,
            maxLines = 2
        )
        val subtitulo = buildString {
            if (curso.displayCodigo.isNotBlank()) append(curso.displayCodigo)
            if (!curso.crn.isNullOrBlank()) {
                if (isNotEmpty()) append(" · ")
                append("NRC ${curso.crn}")
            }
        }
        if (subtitulo.isNotBlank()) {
            Text(
                text = subtitulo,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.AccessTime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "${bloque.horaInicio12h ?: bloque.horaInicio ?: "—"} - ${bloque.horaFin12h ?: bloque.horaFin ?: "—"}",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!bloque.aula.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Room,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = bloque.aula!!,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

package com.example.upaos.ui.horario

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.upaos.data.api.RetrofitClient
import com.example.upaos.data.api.esErrorSesionExpirada
import com.example.upaos.data.local.ApiCache
import com.example.upaos.data.model.HorarioCurso
import com.example.upaos.data.model.HorarioResponse
import com.example.upaos.ui.grades.detectarPeriodoActual
import com.google.gson.Gson
import kotlinx.coroutines.launch

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
                    Log.e("UPAO_APP", "[Android UI] Sesión expirada detectada (401 sesion_expirada)")
                    sesionExpirada = true
                    return@launch
                }
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    cursos = body.cursos
                    scope.launch { cache.guardar(claveCache(), gson.toJson(body)) }
                    Log.d("UPAO_APP", "[Android UI] Horario recibido: ${cursos.size} cursos, ${body.totalBloques} bloques")
                } else {
                    val err = errBody ?: "Error desconocido"
                    Log.e("UPAO_APP", "[Android UI] Error HTTP ${res.code()}: $err")
                    errorMessage = "Error HTTP ${res.code()}: $err"
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                isLoading = false
                Log.e("UPAO_APP", "[Android UI] Excepción al cargar horario: ${e.localizedMessage}", e)
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
            Log.e("UPAO_APP", "Error consultando periodos para horario: ${e.localizedMessage}")
            aplicarCache()
            loadHorario()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
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
                    label = { Text("Periodo") },
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
                            text = { Text(item) },
                            onClick = {
                                selectedPeriodo = item
                                periodosExpanded = false
                                loadHorario()
                            }
                        )
                    }
                }
            }

            IconButton(onClick = { loadHorario() }) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Actualizar Horario",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            if (isLoading && cursos.isNotEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            isLoading && cursos.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            errorMessage != null && cursos.isEmpty() -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Error de Consulta",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = errorMessage!!, fontSize = 13.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            cursos.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sin horario publicado para el periodo $selectedPeriodo",
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(cursos) { curso ->
                        HorarioCursoCard(curso)
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
private fun HorarioCursoCard(curso: HorarioCurso) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AccessTime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = curso.displayNombre,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (curso.displayCodigo.isNotBlank()) {
                        Text(
                            text = curso.displayCodigo,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            curso.bloques.forEach { bloque ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = bloque.diaNombre ?: "—",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(44.dp)
                    )
                    Text(
                        text = "${bloque.horaInicio12h ?: bloque.horaInicio ?: "—"} — ${bloque.horaFin12h ?: bloque.horaFin ?: "—"}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = bloque.aula ?: "—",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

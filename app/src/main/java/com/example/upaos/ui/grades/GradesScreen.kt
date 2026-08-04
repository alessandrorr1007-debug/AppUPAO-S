package com.example.upaos.ui.grades

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.upaos.data.api.RetrofitClient
import com.example.upaos.data.api.esErrorSesionExpirada
import com.example.upaos.data.local.GradesCache
import com.example.upaos.data.model.ComponenteDetalle
import com.example.upaos.data.model.CourseGrade
import com.example.upaos.data.model.GradesResponse
import com.example.upaos.ui.components.CircularGauge
import com.example.upaos.ui.components.gradeColor
import com.example.upaos.ui.components.isPendiente
import com.example.upaos.ui.components.textoUltimaActualizacion
import com.google.gson.Gson
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Regla compartida con el backend: un periodo es de ciclo regular si su código
 * termina en "10" o "20". Los códigos que terminan en "90" son Centro de
 * Idiomas y NO se consideran ciclo regular.
 */
private fun esPeriodoRegular(code: String): Boolean {
    val s = code.trim()
    return s.endsWith("10") || s.endsWith("20")
}

/**
 * Detecta el periodo actual: de los periodos regulares (10/20, excluyendo 90)
 * selecciona el de código numérico más alto (los códigos son cronológicos:
 * 202610 < 202620 < 202710, el más alto siempre es el más reciente/actual).
 * Se aplica al cargar y cada vez que la lista de periodos se actualiza.
 */
fun detectarPeriodoActual(periodos: List<String>, periodoActual: String? = null): String {
    val regulares = periodos.filter(::esPeriodoRegular)
    regulares.maxOrNull()?.let { return it }
    periodoActual?.takeIf(::esPeriodoRegular)?.let { return it }
    return periodos.firstOrNull() ?: "202610"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesContent(
    token: String,
    usuario: String?,
    onSesionExpirada: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cache = remember { GradesCache(context) }
    val gson = remember { Gson() }

    var periodos by remember { mutableStateOf(listOf("202610")) }
    var selectedPeriodo by remember { mutableStateOf("202610") }
    var periodosExpanded by remember { mutableStateOf(false) }

    var carreras by remember { mutableStateOf(listOf("UG")) }
    var selectedCarrera by remember { mutableStateOf("UG") }
    var carrerasExpanded by remember { mutableStateOf(false) }

    var cursos by remember { mutableStateOf<List<CourseGrade>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var promedioGeneral by remember { mutableStateOf<Any?>(null) }
    var promedioBasadoEn by remember { mutableStateOf<String?>(null) }
    var ultimaActualizacion by remember { mutableStateOf<String?>(null) }
    var sesionExpirada by remember { mutableStateOf(false) }

    val claveCache = "notas_${usuario ?: "anonimo"}"

    fun aplicarCache() {
        scope.launch {
            try {
                if (cursos.isNotEmpty()) return@launch
                val json = cache.cargar(claveCache) ?: return@launch
                val body = gson.fromJson(json, GradesResponse::class.java)
                cursos = body.cursos
                promedioGeneral = body.promedioGeneral
                promedioBasadoEn = body.promedioBasadoEn
                ultimaActualizacion = body.ultimaActualizacion
                body.periodo?.let { selectedPeriodo = it }
                body.carrera?.let { selectedCarrera = it }
                Log.d("UPAO_APP", "[Android UI] Caché aplicada: ${cursos.size} cursos, actualizado=$ultimaActualizacion")
            } catch (e: Exception) {
                Log.e("UPAO_APP", "[Android UI] Error leyendo caché de notas: ${e.localizedMessage}", e)
            }
        }
    }

    fun loadGrades() {
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                Log.d("UPAO_APP", "[Android UI] Enviando consulta -> Periodo: $selectedPeriodo, Nivel/Carrera: $selectedCarrera, Token: ${token.take(10)}...")
                val req = mapOf("periodo" to selectedPeriodo, "carrera" to selectedCarrera)
                val res = RetrofitClient.apiService.buscarNotas("Bearer $token", req)
                isLoading = false
                val errBody = res.errorBody()?.string()
                if (esErrorSesionExpirada(res.code(), errBody)) {
                    Log.e("UPAO_APP", "[Android UI] Sesión expirada detectada (401 sesion_expirada)")
                    sesionExpirada = true
                    return@launch
                }

                Log.d("UPAO_APP", "[Android UI] Respuesta HTTP buscarNotas Code: ${res.code()}")
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    cursos = body.cursos
                    promedioGeneral = body.promedioGeneral
                    promedioBasadoEn = body.promedioBasadoEn
                    ultimaActualizacion = body.ultimaActualizacion
                    // Se guarda el JSON completo: el timestamp real del backend viaja con los datos.
                    scope.launch { cache.guardar(claveCache, gson.toJson(body)) }
                    Log.d("UPAO_APP", "[Android UI] Cursos recibidos por la API: ${cursos.size}")
                    Log.d("UPAO_APP", "[Android UI] Promedio general: $promedioGeneral (basado en: $promedioBasadoEn)")
                } else {
                    val err = errBody ?: "Error desconocido"
                    Log.e("UPAO_APP", "[Android UI] Error HTTP ${res.code()}: $err")
                    // No se borra la caché: se sigue mostrando la última versión conocida.
                    errorMessage = "Error HTTP ${res.code()}: $err"
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                isLoading = false
                Log.e("UPAO_APP", "[Android UI] Excepción al cargar notas: ${e.localizedMessage}", e)
                // No se borra la caché: se sigue mostrando la última versión conocida.
                errorMessage = "Excepción: ${e.localizedMessage}"
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun updateCarrerasForTerm(term: String) {
        scope.launch {
            try {
                val carrerasRes = RetrofitClient.apiService.getCarreras("Bearer $token", term)
                if (carrerasRes.isSuccessful && carrerasRes.body() != null) {
                    val body = carrerasRes.body()!!
                    if (body.carreras.isNotEmpty()) {
                        carreras = body.carreras
                        selectedCarrera = carreras[0]
                    }
                }
            } catch (e: Exception) {
                Log.e("UPAO_APP", "Error actualizando carreras para $term: ${e.localizedMessage}")
            }
            loadGrades()
        }
    }

    LaunchedEffect(Unit) {
        // Mostrar al instante lo último que se conoce de este usuario.
        aplicarCache()
        try {
            val periodosRes = RetrofitClient.apiService.getPeriodos("Bearer $token")
            if (periodosRes.isSuccessful && periodosRes.body() != null) {
                val body = periodosRes.body()!!
                periodos = body.periodos
                // Selección automática: periodo regular (10/20, excluye 90) de mayor código.
                selectedPeriodo = detectarPeriodoActual(body.periodos, body.periodoActual)
            }
            updateCarrerasForTerm(selectedPeriodo)
        } catch (e: Exception) {
            Log.e("UPAO_APP", "Error consultando combos iniciales: ${e.localizedMessage}")
            loadGrades()
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
                                updateCarrerasForTerm(item)
                            }
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = carrerasExpanded,
                onExpandedChange = { carrerasExpanded = !carrerasExpanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = selectedCarrera,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Nivel / Carrera") },
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = carrerasExpanded) },
                    modifier = Modifier.menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = carrerasExpanded,
                    onDismissRequest = { carrerasExpanded = false }
                ) {
                    carreras.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item) },
                            onClick = {
                                selectedCarrera = item
                                carrerasExpanded = false
                                loadGrades()
                            }
                        )
                    }
                }
            }

            IconButton(onClick = { loadGrades() }) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Actualizar Notas",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (ultimaActualizacion != null && errorMessage == null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    text = textoUltimaActualizacion(ultimaActualizacion),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isLoading) {
                    Spacer(modifier = Modifier.width(6.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = errorMessage == null && promedioGeneral != null,
            enter = androidx.compose.animation.fadeIn(tween(300)) + androidx.compose.animation.expandVertically(),
            exit = androidx.compose.animation.fadeOut(tween(200))
        ) {
            Column {
                PromedioCard(promedioGeneral)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

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
                        Text(text = "Error de Consulta", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = errorMessage!!, fontSize = 13.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            cursos.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay cursos registrados para el periodo $selectedPeriodo", color = MaterialTheme.colorScheme.outline)
                }
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(cursos) { course ->
                        CourseGradeCard(token, selectedPeriodo, selectedCarrera, course)
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
fun PromedioCard(promedioGeneral: Any?) {
    val p = promedioGeneral?.toString()?.toDoubleOrNull()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularGauge(
                progress = if (p != null) (p / 20f).toFloat() else 0f,
                centerValue = formatNota(promedioGeneral),
                centerLabel = "/ 20",
                size = 104.dp
            )
            Spacer(modifier = Modifier.width(24.dp))
            Column {
                Text(
                    text = "Promedio general",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Basado en las notas actuales de tus cursos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (p != null) {
                    Text(
                        text = if (p >= 10.5) "Aprobado" else "Requiere mejorar",
                        style = MaterialTheme.typography.labelMedium,
                        color = gradeColor(p)
                    )
                }
            }
        }
    }
}

@Composable
fun CourseGradeCard(
    token: String,
    periodo: String,
    carrera: String,
    course: CourseGrade
) {
    val scope = rememberCoroutineScope()
    var componentesExpanded by remember { mutableStateOf(false) }
    var isLoadingDetails by remember { mutableStateOf(false) }
    var detailMessage by remember { mutableStateOf<String?>(null) }
    var componentes by remember { mutableStateOf<List<ComponenteDetalle>>(emptyList()) }
    var notaProyectada by remember { mutableStateOf<Any?>(null) }
    var pesosPendientes by remember { mutableStateOf<List<String>>(emptyList()) }

    val courseCrn = course.crn ?: course.courseReferenceNumber ?: ""

    fun fetchComponentes() {
        if (componentes.isNotEmpty() || isLoadingDetails) return
        isLoadingDetails = true
        scope.launch {
            try {
                val req = mapOf(
                    "periodo" to periodo,
                    "carrera" to carrera,
                    "crn" to courseCrn
                )
                Log.d("UPAO_APP", "[Android UI] Solicitando componentes CRN=$courseCrn periodo=$periodo")
                val res = RetrofitClient.apiService.getDetalleCurso("Bearer $token", req)
                isLoadingDetails = false
                Log.d("UPAO_APP", "[Android UI] Respuesta getDetalleCurso Code: ${res.code()}")
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    if (body.success) {
                        componentes = body.detalles
                        notaProyectada = body.notaProyectada
                        pesosPendientes = body.pesosPendientes
                        Log.d("UPAO_APP", "[Android UI] Componentes recibidos: ${componentes.size}, notaProyectada=$notaProyectada")
                    } else {
                        detailMessage = "Sin desglose de componentes disponible"
                    }
                } else {
                    detailMessage = "Sin desglose de componentes disponible"
                }
            } catch (e: Exception) {
                isLoadingDetails = false
                Log.e("UPAO_APP", "[Android UI] Excepción al cargar componentes: ${e.localizedMessage}", e)
                detailMessage = "En espera de carga de desgloses"
            }
        }
    }

    val arrowRotation by animateFloatAsState(
        targetValue = if (componentesExpanded) 180f else 0f,
        animationSpec = tween(250),
        label = "arrow"
    )
    val statusColor = gradeColor(course.displayNotaActual)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                .background(statusColor)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier.weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .animateContentSize()
            ) {
                Text(
                    text = course.displayNombre,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (courseCrn.isNotBlank()) "CRN $courseCrn" else "",
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
                        text = "NOTA",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = formatNota(course.displayNotaActual),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        componentesExpanded = !componentesExpanded
                        if (componentesExpanded) fetchComponentes()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(arrowRotation)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (componentesExpanded) "Ocultar Componente" else "Ver Componente")
                }

                AnimatedVisibility(visible = componentesExpanded) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Detalle de Componentes", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))

                        when {
                            isLoadingDetails -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text("Consultando componentes...", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            componentes.isNotEmpty() -> {
                                if (notaProyectada != null) {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                        ),
                                        shape = MaterialTheme.shapes.medium,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = "Nota proyectada (estimada, no oficial): ${displayGrade(notaProyectada)}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            if (pesosPendientes.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "Pendientes: ${pesosPendientes.joinToString(", ")}",
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                                componentes.forEach { componente ->
                                    ComponenteRow(componente)
                                }
                            }
                            detailMessage != null -> {
                                Text(
                                    text = detailMessage!!,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.outline
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
fun ComponenteRow(componente: ComponenteDetalle) {
    var expanded by remember { mutableStateOf(false) }
    val hasSub = componente.hasSubComponents && componente.subcomponentes.isNotEmpty()

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = hasSub) { expanded = !expanded }
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = componente.displayNombre, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                val puntaje = componente.displayPuntaje
                if (puntaje != null) {
                    Text(
                        text = "$puntaje  ·  Peso ${componente.displayPeso}%",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            GradeBadge(label = "NOTA", value = componente.displayNota)
            if (hasSub) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Expandir sub-componentes",
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(if (expanded) 180f else 0f)
                )
            }
        }

        if (hasSub) {
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .padding(start = 16.dp, bottom = 4.dp)
                        .fillMaxWidth()
                ) {
                    componente.subcomponentes.forEach { sub ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = sub.displayNombre, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text(
                                text = displayGrade(sub.displayNota),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

fun displayGrade(value: Any?): String = when {
    value == null || value.toString().isBlank() || value.toString() == "null" -> "Pendiente"
    else -> value.toString()
}

// Muestra la nota con máximo 2 decimales, sin ceros a la derecha (14.0 -> "14", 9.84 -> "9.84").
fun formatNota(value: Any?): String {
    if (isPendiente(value)) return "Pendiente"
    val d = value.toString().trim().toDoubleOrNull() ?: return value.toString()
    val r = (d * 100).roundToInt() / 100.0
    return if (r % 1.0 == 0.0) r.toInt().toString() else r.toString()
}

@Composable
fun GradeBadge(label: String, value: Any?) {
    val color = gradeColor(value)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp
        )
        Text(
            text = displayGrade(value),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

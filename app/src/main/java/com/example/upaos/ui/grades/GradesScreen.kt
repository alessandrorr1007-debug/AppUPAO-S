package com.example.upaos.ui.grades

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.upaos.data.api.RetrofitClient
import com.example.upaos.data.api.esErrorSesionExpirada
import com.example.upaos.data.api.llamarConRenovacion
import com.example.upaos.data.local.GradesCache
import com.example.upaos.data.local.TokenManager
import com.example.upaos.data.model.ComponenteDetalle
import com.example.upaos.data.model.CourseGrade
import com.example.upaos.data.model.GradesResponse
import com.example.upaos.data.model.PromedioPeriodoResponse
import com.example.upaos.ui.components.AppCard
import com.example.upaos.ui.components.CircularGauge
import com.example.upaos.ui.components.EmptyState
import com.example.upaos.ui.components.ErrorView
import com.example.upaos.ui.components.SkeletonCourseCard
import com.example.upaos.ui.components.StatusBadge
import com.example.upaos.ui.components.gradeColor
import com.example.upaos.ui.components.isPendiente
import com.example.upaos.ui.components.textoUltimaActualizacion
import com.example.upaos.ui.components.toTitleCase
import com.google.gson.Gson
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private fun esPeriodoRegular(code: String): Boolean {
    val s = code.trim()
    return s.endsWith("10") || s.endsWith("20")
}

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
    onSesionExpirada: () -> Unit,
    onTokenRenovado: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cache = remember { GradesCache(context) }
    val gson = remember { Gson() }
    val tokenManagerUi = remember { TokenManager(context) }

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
    var promedioPeriodoRes by remember { mutableStateOf<PromedioPeriodoResponse?>(null) }
    var promedioPeriodoCargando by remember { mutableStateOf(false) }
    var promedioPeriodoError by remember { mutableStateOf<String?>(null) }
    var ultimaActualizacion by remember { mutableStateOf<String?>(null) }
    var sesionExpirada by remember { mutableStateOf(false) }

    val claveCache = "notas_${usuario ?: "anonimo"}"

    fun loadPromedioPeriodo(term: String) {
        scope.launch {
            try {
                promedioPeriodoCargando = true
                promedioPeriodoError = null
                Log.d("UPAO_APP", "[Android UI] Solicitando promedio PPS para periodo: $term")
                val res = RetrofitClient.apiService.getPromedioPeriodo(term, "Bearer $token")
                promedioPeriodoCargando = false
                if (res.isSuccessful && res.body() != null) {
                    promedioPeriodoRes = res.body()
                    promedioPeriodoError = null
                    Log.d("UPAO_APP", "[Android UI] PPS recibido -> HTTP ${res.code()} JSON=${gson.toJson(promedioPeriodoRes)}")
                } else {
                    promedioPeriodoError = "HTTP ${res.code()}"
                    Log.w("UPAO_APP", "[Android UI] PPS fallo HTTP ${res.code()} errorBody=${res.errorBody()?.string() ?: "sin cuerpo"}")
                }
            } catch (e: Exception) {
                promedioPeriodoCargando = false
                promedioPeriodoError = e.localizedMessage ?: "error desconocido"
                Log.w("UPAO_APP", "[Android UI] Excepción obteniendo PPS: ${e.localizedMessage}")
            }
        }
    }

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
                Log.d("UPAO_APP", "[Android UI] Caché aplicada: ${cursos.size} cursos")
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
                Log.d("UPAO_APP", "[Android UI] Consultando notas -> Periodo: $selectedPeriodo, Carrera: $selectedCarrera")
                val req = mapOf("periodo" to selectedPeriodo, "carrera" to selectedCarrera)
                val resLlamada = llamarConRenovacion(tokenManagerUi, token) { t ->
                    RetrofitClient.apiService.buscarNotas("Bearer $t", req)
                }
                resLlamada.tokenRenovado?.let(onTokenRenovado)
                val res = resLlamada.response
                isLoading = false
                val errBody = resLlamada.errorBody
                if (esErrorSesionExpirada(res.code(), errBody)) {
                    sesionExpirada = true
                    return@launch
                }

                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    cursos = body.cursos
                    promedioGeneral = body.promedioGeneral
                    promedioBasadoEn = body.promedioBasadoEn
                    ultimaActualizacion = body.ultimaActualizacion
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
        loadPromedioPeriodo(selectedPeriodo)
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
        aplicarCache()
        try {
            val periodosRes = RetrofitClient.apiService.getPeriodos("Bearer $token")
            if (periodosRes.isSuccessful && periodosRes.body() != null) {
                val body = periodosRes.body()!!
                periodos = body.periodos
                selectedPeriodo = detectarPeriodoActual(body.periodos, body.periodoActual)
            }
            updateCarrerasForTerm(selectedPeriodo)
        } catch (e: Exception) {
            loadGrades()
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
                    label = { Text("Nivel / Carrera", fontSize = 12.sp) },
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
                            text = { Text(item, fontSize = 13.sp) },
                            onClick = {
                                selectedCarrera = item
                                carrerasExpanded = false
                                loadGrades()
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
                IconButton(onClick = { loadGrades() }) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Actualizar Notas",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (ultimaActualizacion != null && errorMessage == null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                Text(
                    text = textoUltimaActualizacion(ultimaActualizacion),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isLoading) {
                    Spacer(modifier = Modifier.width(6.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.8.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = errorMessage == null && (promedioGeneral != null || promedioPeriodoRes != null),
            enter = fadeIn(tween(220)) + expandVertically(),
            exit = fadeOut(tween(180))
        ) {
            Column {
                PromedioCard(
                    promedioGeneral = promedioGeneral,
                    promedioPeriodoRes = promedioPeriodoRes,
                    ppsCargando = promedioPeriodoCargando,
                    ppsError = promedioPeriodoError,
                    onReintentarPps = { loadPromedioPeriodo(selectedPeriodo) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        when {
            isLoading && cursos.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(4) { SkeletonCourseCard() }
                }
            }
            errorMessage != null && cursos.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    ErrorView(
                        message = errorMessage!!,
                        onRetry = { loadGrades() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            cursos.isEmpty() -> {
                EmptyState(
                    icon = Icons.Filled.Grade,
                    title = "No hay cursos registrados",
                    subtitle = "No hay cursos para el periodo $selectedPeriodo.",
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
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
fun PromedioCard(
    promedioGeneral: Any?,
    promedioPeriodoRes: PromedioPeriodoResponse? = null,
    ppsCargando: Boolean = false,
    ppsError: String? = null,
    onReintentarPps: (() -> Unit)? = null
) {
    val ppsOficial = promedioPeriodoRes?.ppsOficial?.toDouble()
    val ppsCalculado = promedioPeriodoRes?.ppsCalculado?.toDouble()
    val fuente = promedioPeriodoRes?.fuente

    val ppsFinal = when {
        ppsOficial != null -> ppsOficial
        ppsCalculado != null -> ppsCalculado
        else -> null
    }

    val promedioSimpleVal = promedioGeneral?.toString()?.toDoubleOrNull()

    val badge = when (fuente) {
        "cuadro_merito" -> "Oficial" to MaterialTheme.colorScheme.primary
        "calculado" -> "Estimado" to MaterialTheme.colorScheme.tertiary
        else -> null
    }

    AppCard(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        corner = 16.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Text(
                text = "Resumen de Promedios del Periodo",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Promedio de Ciclo (promedio simple de las notas visibles)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularGauge(
                            progress = if (promedioSimpleVal != null) (promedioSimpleVal / 20.0).toFloat() else 0f,
                            centerValue = if (promedioSimpleVal != null) formatNota(promedioSimpleVal) else "—",
                            centerLabel = "/ 20",
                            size = 40.dp,
                            strokeWidth = 4.dp,
                            gaugeColor = gradeColor(promedioSimpleVal)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Promedio de Ciclo",
                                style = MaterialTheme.typography.titleSmall,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // 2. Ponderado (PPS) -> consume GET /api/promedio/{periodo}
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularGauge(
                            progress = if (ppsFinal != null) (ppsFinal / 20.0).toFloat() else 0f,
                            centerValue = if (ppsFinal != null) formatPps(ppsFinal) else "—",
                            centerLabel = "/ 20",
                            size = 40.dp,
                            strokeWidth = 4.dp,
                            gaugeColor = gradeColor(ppsFinal)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ponderado (PPS)",
                                style = MaterialTheme.typography.titleSmall,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            when {
                                ppsCargando -> {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(10.dp),
                                            strokeWidth = 1.6.dp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Consultando...",
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                ppsFinal != null && badge != null -> {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    StatusBadge(
                                        text = badge.first,
                                        color = badge.second
                                    )
                                }
                                ppsError != null -> {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "No disponible",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = if (onReintentarPps != null) {
                                            Modifier.clickable(onClick = onReintentarPps)
                                        } else {
                                            Modifier
                                        }
                                    )
                                }
                                else -> {
                                    Text(
                                        text = "En proceso",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
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
                val res = RetrofitClient.apiService.getDetalleCurso("Bearer $token", req)
                isLoadingDetails = false
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    if (body.success) {
                        componentes = body.detalles
                        notaProyectada = body.notaProyectada
                        pesosPendientes = body.pesosPendientes
                    } else {
                        detailMessage = "Sin desglose disponible"
                    }
                } else {
                    detailMessage = "Sin desglose disponible"
                }
            } catch (e: Exception) {
                isLoadingDetails = false
                detailMessage = "En espera de desgloses"
            }
        }
    }

    val arrowRotation by animateFloatAsState(
        targetValue = if (componentesExpanded) 180f else 0f,
        animationSpec = tween(220),
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
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                .background(statusColor)
        )
        AppCard(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            corner = 14.dp,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Column(
                modifier = Modifier.animateContentSize(animationSpec = tween(220))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = toTitleCase(course.displayNombre),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2
                        )
                        if (courseCrn.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "CRN $courseCrn",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "NOTA",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = formatNota(course.displayNotaActual),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                FilledTonalButton(
                    onClick = {
                        componentesExpanded = !componentesExpanded
                        if (componentesExpanded) fetchComponentes()
                    },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(arrowRotation)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (componentesExpanded) "Ocultar componentes" else "Ver componentes",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                AnimatedVisibility(visible = componentesExpanded) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Detalle de Componentes", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))

                        when {
                            isLoadingDetails -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(vertical = 6.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.8.dp)
                                    Text("Consultando componentes...", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            componentes.isNotEmpty() -> {
                                if (notaProyectada != null) {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text(
                                                text = "Nota proyectada: ${displayGrade(notaProyectada)}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
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
                                    fontSize = 11.sp,
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
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = componente.displayNombre, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                val puntaje = componente.displayPuntaje
                if (puntaje != null) {
                    Text(
                        text = "$puntaje · Peso ${componente.displayPeso}%",
                        fontSize = 10.sp,
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
                        .size(16.dp)
                        .rotate(if (expanded) 180f else 0f)
                )
            }
        }

        if (hasSub) {
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .padding(start = 12.dp, bottom = 4.dp)
                        .fillMaxWidth()
                ) {
                    componente.subcomponentes.forEach { sub ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = sub.displayNombre, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            Text(
                                text = displayGrade(sub.displayNota),
                                fontSize = 11.sp,
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

fun formatNota(value: Any?): String {
    if (isPendiente(value)) return "Pendiente"
    val d = value.toString().trim().toDoubleOrNull() ?: return value.toString()
    val r = (d * 100).roundToInt() / 100.0
    return if (r % 1.0 == 0.0) r.toInt().toString() else r.toString()
}

fun formatPps(value: Double?): String {
    if (value == null) return "Pendiente"
    return String.format(java.util.Locale.ROOT, "%.2f", value)
}

@Composable
fun GradeBadge(label: String, value: Any?) {
    val color = gradeColor(value)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp
        )
        Text(
            text = displayGrade(value),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

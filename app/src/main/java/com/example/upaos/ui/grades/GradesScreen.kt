package com.example.upaos.ui.grades

import android.content.Context
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.upaos.data.api.RetrofitClient
import com.example.upaos.data.local.GradesCache
import com.example.upaos.data.model.ComponenteDetalle
import com.example.upaos.data.model.CourseGrade
import com.example.upaos.data.model.GradesResponse
import com.example.upaos.data.model.PromedioPeriodoResponse
import com.example.upaos.ui.components.AppCard
import com.example.upaos.ui.components.EmptyState
import com.example.upaos.ui.components.ErrorView
import com.example.upaos.ui.components.RefreshableContent
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
    onSesionExpirada: () -> Unit = {},
    onOpenCourse: (String, String, String, String) -> Unit = { _, _, _, _ -> }
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
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var offline by remember { mutableStateOf(false) }
    var notasProyectadas by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }
    var promedioGeneral by remember { mutableStateOf<Any?>(null) }
    var promedioBasadoEn by remember { mutableStateOf<String?>(null) }
    var promedioPeriodoRes by remember { mutableStateOf<PromedioPeriodoResponse?>(null) }
    var ultimaActualizacion by remember { mutableStateOf<String?>(null) }

    val claveCache = "notas_${usuario ?: "anonimo"}"

    fun loadPromedioPeriodo(term: String) {
        scope.launch {
            try {
                Log.d("UPAO_APP", "[Android UI] Solicitando promedio PPS para periodo: $term")
                val res = RetrofitClient.apiService.getPromedioPeriodo(term, "Bearer $token")
                if (res.isSuccessful && res.body() != null) {
                    promedioPeriodoRes = res.body()
                    Log.d("UPAO_APP", "[Android UI] PPS recibido -> Oficial=${promedioPeriodoRes?.ppsOficial}, Calc=${promedioPeriodoRes?.ppsCalculado}, Fuente=${promedioPeriodoRes?.fuente}")
                } else {
                    Log.w("UPAO_APP", "[Android UI] No se pudo obtener PPS (${res.code()}), usando promedio local de fallback")
                }
            } catch (e: Exception) {
                Log.w("UPAO_APP", "[Android UI] Excepción obteniendo PPS: ${e.localizedMessage}, usando promedio local de fallback")
            }
        }
    }

    fun cargarNotasProyectadas() {
        val pendientes = cursos.filter { isPendiente(it.displayNotaActual) }
        if (pendientes.isEmpty()) return
        pendientes.forEach { course ->
            val crn = course.crn ?: course.courseReferenceNumber ?: return@forEach
            scope.launch {
                try {
                    val cacheJson = cache.cargar("proyectada_${usuario ?: "anonimo"}_$crn")
                    if (cacheJson != null && cacheJson != "null") {
                        notasProyectadas = notasProyectadas + (crn to cacheJson)
                    }
                } catch (e: Exception) {
                    // ignorar
                }
                try {
                    val res = RetrofitClient.apiService.getDetalleCurso(
                        "Bearer $token",
                        mapOf("periodo" to selectedPeriodo, "carrera" to selectedCarrera, "crn" to crn)
                    )
                    if (res.isSuccessful && res.body() != null) {
                        val nota = res.body()!!.notaProyectada
                        if (nota != null) {
                            notasProyectadas = notasProyectadas + (crn to nota)
                            scope.launch { cache.guardar("proyectada_${usuario ?: "anonimo"}_$crn", nota.toString()) }
                        }
                    }
                } catch (e: Exception) {
                    // sin conexión: se mantiene el valor en caché si existe
                }
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
                cargarNotasProyectadas()
            } catch (e: Exception) {
                Log.e("UPAO_APP", "[Android UI] Error leyendo caché de notas: ${e.localizedMessage}", e)
            }
        }
    }

    fun loadGrades() {
        isLoading = true
        errorMessage = null
        offline = false
        scope.launch {
            try {
                Log.d("UPAO_APP", "[Android UI] Consultando notas -> Periodo: $selectedPeriodo, Carrera: $selectedCarrera")
                val req = mapOf("periodo" to selectedPeriodo, "carrera" to selectedCarrera)
                val res = RetrofitClient.apiService.buscarNotas("Bearer $token", req)
                isLoading = false
                isRefreshing = false
                val errBody = res.errorBody()?.string()

                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    cursos = body.cursos
                    promedioGeneral = body.promedioGeneral
                    promedioBasadoEn = body.promedioBasadoEn
                    ultimaActualizacion = body.ultimaActualizacion
                    scope.launch { cache.guardar(claveCache, gson.toJson(body)) }
                    cargarNotasProyectadas()
                    // Actualiza el widget de resumen de notas
                    try {
                        com.example.upaos.widget.ResumenNotasWidget.updateAll(context)
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

        if (offline && cursos.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = "Sin conexión · Mostrando datos guardados",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        if (ultimaActualizacion != null && errorMessage == null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                Text(
                    text = textoUltimaActualizacion(ultimaActualizacion),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = errorMessage == null && (cursos.isNotEmpty() || promedioGeneral != null || promedioPeriodoRes != null),
            enter = fadeIn(tween(220)) + expandVertically(),
            exit = fadeOut(tween(180))
        ) {
            Column {
                PromedioCard(
                    promedioGeneral = promedioGeneral,
                    promedioPeriodoRes = promedioPeriodoRes,
                    cursos = cursos,
                    notasProyectadas = notasProyectadas
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
                RefreshableContent(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        isRefreshing = true
                        loadGrades()
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(cursos) { course ->
                            val crnCard = course.crn ?: course.courseReferenceNumber ?: ""
                            CourseGradeCard(
                                token = token,
                                periodo = selectedPeriodo,
                                carrera = selectedCarrera,
                                course = course,
                                notaProyectada = notasProyectadas[crnCard],
                                onClick = {
                                    onOpenCourse(
                                        selectedPeriodo,
                                        selectedCarrera,
                                        crnCard,
                                        course.displayNombre
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PromedioCard(
    promedioGeneral: Any?,
    promedioPeriodoRes: PromedioPeriodoResponse? = null,
    cursos: List<CourseGrade> = emptyList(),
    notasProyectadas: Map<String, Any?> = emptyMap()
) {
    val ppsOficial = promedioPeriodoRes?.ppsOficial?.toDouble()
    val ppsCalculado = promedioPeriodoRes?.ppsCalculado?.toDouble()

    val promedioCursosLocal: Double? = remember(cursos, notasProyectadas) {
        val notasValidas = cursos.mapNotNull { c ->
            val crn = c.crn ?: c.courseReferenceNumber ?: ""
            val nota = if (isPendiente(c.displayNotaActual) && notasProyectadas[crn] != null) {
                notasProyectadas[crn]
            } else {
                c.displayNotaActual
            }
            nota?.toString()?.trim()?.toDoubleOrNull()
        }
        if (notasValidas.isNotEmpty()) {
            notasValidas.average()
        } else null
    }

    val pFinal: Double? = when {
        ppsOficial != null -> ppsOficial
        ppsCalculado != null -> ppsCalculado
        promedioGeneral != null && !isPendiente(promedioGeneral) && promedioGeneral.toString().trim().toDoubleOrNull() != null -> {
            promedioGeneral.toString().trim().toDouble()
        }
        else -> promedioCursosLocal
    }

    val fuenteTexto = when {
        ppsOficial != null -> "Oficial (Cuadro de Mérito)"
        ppsCalculado != null -> "Estimado (Notas × Créditos)"
        promedioCursosLocal != null -> "Promedio del ciclo"
        else -> "Sin notas registradas"
    }

    val gaugeColor = gradeColor(pFinal)
    val notaTexto = formatPromedio(pFinal)

    AppCard(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        corner = 14.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ponderado",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = fuenteTexto,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = notaTexto,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (notaTexto != "--") gaugeColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CourseGradeCard(
    token: String,
    periodo: String,
    carrera: String,
    course: CourseGrade,
    notaProyectada: Any? = null,
    onClick: () -> Unit = {}
) {
    val notaMostrar =
        if (isPendiente(course.displayNotaActual) && notaProyectada != null) notaProyectada else course.displayNotaActual
    val statusColor = gradeColor(notaMostrar)

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
            modifier = Modifier.weight(1f),
            onClick = onClick
        ) {
            val courseCrn = course.crn ?: course.courseReferenceNumber ?: ""
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
                            text = "NRC $courseCrn",
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
                        text = formatNota(notaMostrar),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        maxLines = 1
                    )
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
    value == null || value.toString().isBlank() || value.toString() == "null" -> "--"
    else -> value.toString()
}

fun formatNota(value: Any?): String {
    if (isPendiente(value)) return "--"
    val d = value.toString().trim().toDoubleOrNull() ?: return value.toString()
    val r = (d * 100).roundToInt() / 100.0
    return if (r % 1.0 == 0.0) r.toInt().toString() else r.toString()
}

fun formatPromedio(value: Any?): String {
    if (isPendiente(value)) return "--"
    val d = value?.toString()?.trim()?.toDoubleOrNull() ?: return "--"
    val r = (d * 100).roundToInt() / 100.0
    return if (r % 1.0 == 0.0) r.toInt().toString() else r.toString()
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

package com.example.upaos.ui.grades

import com.example.upaos.ui.home.DashboardViewModel
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.upaos.data.local.TokenManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.upaos.ui.components.courseAccent
import com.example.upaos.ui.components.gradeAccent
import com.example.upaos.ui.components.isPendiente
import com.example.upaos.ui.components.toTitleCase
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private fun esPeriodoRegular(code: String) = com.example.upaos.domain.academic.AcademicRules.isRegular(code)

fun detectarPeriodoActual(periodos: List<String>, periodoActual: String? = null): String =
    com.example.upaos.domain.academic.AcademicRules.latestAvailable(periodos, periodoActual)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesContent(
    token: String,
    dashboard: DashboardViewModel,
    usuario: String?,
    onSesionExpirada: () -> Unit = {},
    onOpenCourse: (String, String, String, String) -> Unit = { _, _, _, _ -> }
) {
    val context = LocalContext.current
    val shared by dashboard.state.collectAsState()
    val selectedPeriodo = shared.gradePeriod
    val selectedCarrera = shared.career
    val periodos = shared.periods.ifEmpty { listOf(selectedPeriodo) }
    val carreras = shared.careers
    var periodosExpanded by remember { mutableStateOf(false) }
    var carrerasExpanded by remember { mutableStateOf(false) }
    val cursos = shared.grades?.cursos ?: emptyList()
    val isLoading = shared.busy
    val isRefreshing = shared.busy
    val claveCache = dashboard.gradeCacheKey
    val errorMessage = shared.errors[claveCache]
    val offline = claveCache !in shared.fresh
    val notasProyectadas = shared.projected
    val promedioGeneral = shared.grades?.promedioGeneral
    val promedioBasadoEn = shared.grades?.promedioBasadoEn
    val promedioPeriodoRes = shared.average
    val nombreEstudiante = remember(usuario) {
        usuario?.let { u -> TokenManager(context).getCuentas().firstOrNull { it.usuario == u }?.nombre }
    }
    var mostrarDialogCompartir by remember { mutableStateOf(false) }
    fun loadGrades() = dashboard.refresh()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                AppCard(
                    color = MaterialTheme.colorScheme.surface,
                    corner = 14.dp,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    onClick = { periodosExpanded = true }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PERIODO",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = selectedPeriodo.ifBlank { "Periodo" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                DropdownMenu(
                    expanded = periodosExpanded,
                    onDismissRequest = { periodosExpanded = false }
                ) {
                    periodos.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item, fontSize = 13.sp) },
                            onClick = {
                                periodosExpanded = false
                                dashboard.selectGrades(item)
                            }
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                AppCard(
                    color = MaterialTheme.colorScheme.surface,
                    corner = 14.dp,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    onClick = { carrerasExpanded = true }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "NIVEL / CARRERA",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = selectedCarrera.ifBlank { "Carrera" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                DropdownMenu(
                    expanded = carrerasExpanded,
                    onDismissRequest = { carrerasExpanded = false }
                ) {
                    carreras.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item, fontSize = 13.sp) },
                            onClick = {
                                carrerasExpanded = false
                                dashboard.selectGrades(selectedPeriodo, item)
                            }
                        )
                    }
                }
            }

            AppCard(
                color = MaterialTheme.colorScheme.surface,
                corner = 14.dp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 11.dp),
                modifier = Modifier.fillMaxHeight(),
                onClick = {
                    if (cursos.isEmpty()) {
                        Toast.makeText(context, "No hay notas para compartir todavía", Toast.LENGTH_SHORT).show()
                    } else {
                        mostrarDialogCompartir = true
                    }
                }
            ) {
                Box(
                    modifier = Modifier.fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Compartir resumen de notas",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (offline || isLoading || isRefreshing) {
            Column(Modifier.padding(vertical = 4.dp)) {
                com.example.upaos.ui.home.Freshness(shared.times[claveCache], !offline, isLoading || isRefreshing)
            }
        }

        if (offline && cursos.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = "Datos guardados · pendiente de actualizar",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
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
                    notasProyectadas = notasProyectadas,
                    periodo = selectedPeriodo
                )
                Spacer(modifier = Modifier.height(4.dp))
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
                        loadGrades()
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        contentPadding = PaddingValues(bottom = 12.dp),
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

    if (mostrarDialogCompartir) {
        com.example.upaos.ui.share.CompartirNotasDialog(
            cursos = cursos,
            periodo = selectedPeriodo,
            carrera = selectedCarrera,
            promedioGeneral = promedioGeneral,
            promedioPeriodoRes = promedioPeriodoRes,
            notasProyectadas = notasProyectadas,
            nombreEstudiante = nombreEstudiante,
            onDismiss = { mostrarDialogCompartir = false }
        )
    }
}

/**
 * Calcula la nota acumulada ponderada de un curso (ej: EP1 de 14 * 20% = 2.8).
 * A medida que se cargan el Parcial, EP2 y Final, el acumulado va aumentando.
 */
fun calcularAcumuladoCurso(course: CourseGrade, notaProyectada: Any? = null, periodo: String? = null): Double? {
    com.example.upaos.data.upao.Calculators.progresoCurso(course, periodo)?.let { return it.acumulado }
    // With no breakdown, never infer an evaluation's weight from the size of its grade.
    if (course.componentes.isNotEmpty()) return null
    return com.example.upaos.data.upao.Calculators.toDouble(notaProyectada ?: course.displayNotaActual)
        ?.takeIf { it.isFinite() && it in 0.0..20.0 }
}

fun resolverCreditosCurso(course: CourseGrade, promedioPeriodoRes: PromedioPeriodoResponse? = null): Int {
    com.example.upaos.domain.academic.AcademicRules.credits(course.rawBanner)?.let { return it }
    val crn = course.crn ?: course.courseReferenceNumber ?: ""
    val crPromedio = promedioPeriodoRes?.cursos?.firstOrNull { it.crn == crn }?.creditos?.takeIf { it > 0 }
    if (crPromedio != null) return crPromedio

    // Unknown credits must not be invented or contribute a default weight to the PPS.
    return com.example.upaos.domain.academic.AcademicRules.credits(course.rawBanner) ?: 0
}

fun referenciaAcumuladaCurso(course: CourseGrade, periodo: String? = null): Double =
    com.example.upaos.data.upao.Calculators.progresoCurso(course, periodo)?.minimoAcumulado ?: 10.5

fun referenciaPonderada(cursos: List<CourseGrade>, periodo: String?, promedioPeriodoRes: PromedioPeriodoResponse?): Double? {
    val evaluados = cursos.filter { calcularAcumuladoCurso(it, periodo = periodo) != null }
    val creditos = evaluados.sumOf { resolverCreditosCurso(it, promedioPeriodoRes) }
    return if (creditos > 0) evaluados.sumOf {
        referenciaAcumuladaCurso(it, periodo) * resolverCreditosCurso(it, promedioPeriodoRes)
    } / creditos else null
}

@Composable
fun AcumuladoColor(puntos: Double?, referencia: Double?): Color = gradeAccent(
    if (puntos == null || referencia == null) null else if (puntos + 1e-9 >= referencia) 10.5 else 0.0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromedioCard(
    promedioGeneral: Any?,
    promedioPeriodoRes: PromedioPeriodoResponse? = null,
    cursos: List<CourseGrade> = emptyList(),
    notasProyectadas: Map<String, Any?> = emptyMap(),
    periodo: String? = null
) {
    var showSheet by remember { mutableStateOf(false) }

    val ppsOficial = promedioPeriodoRes?.ppsOficial?.toDouble()
    val ppsCalculado = promedioPeriodoRes?.ppsCalculado?.toDouble()

    // Cálculo ponderado usando el acumulado y los créditos reales de cada curso
    val ponderadoLocal: Double? = remember(cursos, notasProyectadas, promedioPeriodoRes, periodo) {
        var puntos = 0.0
        var creditos = 0
        cursos.forEach { c ->
            val crn = c.crn ?: c.courseReferenceNumber ?: ""
            val acumulado = calcularAcumuladoCurso(c, notasProyectadas[crn], periodo)
            val cr = resolverCreditosCurso(c, promedioPeriodoRes)
            if (acumulado != null && !acumulado.isNaN() && !acumulado.isInfinite()) {
                puntos += (acumulado * cr)
                creditos += cr
            }
        }
        if (creditos > 0) (puntos / creditos).takeUnless { it.isNaN() || it.isInfinite() } else null
    }

    val pFinal: Double? = when {
        // En ciclos en curso, el ponderado debe reflejar el acumulado
        ponderadoLocal != null -> ponderadoLocal
        ppsCalculado != null -> ppsCalculado
        ppsOficial != null -> ppsOficial
        promedioGeneral != null && !isPendiente(promedioGeneral) && promedioGeneral.toString().trim().toDoubleOrNull() != null -> {
            promedioGeneral.toString().trim().toDouble().takeUnless { it.isNaN() || it.isInfinite() }
        }
        else -> null
    }

    val fuenteTexto = when {
        ponderadoLocal != null -> "Acumulado del ciclo (Puntos ÷ Créditos)"
        ppsOficial != null -> "Oficial (Cuadro de Mérito)"
        ppsCalculado != null -> "Estimado (Notas × Créditos)"
        else -> "Sin notas registradas"
    }

    val gaugeColor = if (ponderadoLocal != null) AcumuladoColor(pFinal, referenciaPonderada(cursos, periodo, promedioPeriodoRes)) else gradeAccent(pFinal)
    val notaTexto = formatPromedio(pFinal)

    AppCard(
        color = MaterialTheme.colorScheme.surface,
        corner = 14.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
        modifier = Modifier.fillMaxWidth(),
        onClick = { showSheet = true }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Ponderado",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "Ver desglose del ponderado",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = "$fuenteTexto · Toca para ver cálculo",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "PONDERADO",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = notaTexto,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            DetallePonderadoSheet(
                promedioGeneral = pFinal,
                fuenteTexto = fuenteTexto,
                promedioPeriodoRes = promedioPeriodoRes,
                cursos = cursos,
                notasProyectadas = notasProyectadas,
                periodo = periodo,
                onClose = { showSheet = false }
            )
        }
    }
}

data class DetalleCursoPonderado(
    val nombre: String,
    val crn: String,
    val nota: Double?,
    val creditos: Int,
    val puntos: Double?,
    val referencia: Double = 10.5
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetallePonderadoSheet(
    promedioGeneral: Double?,
    fuenteTexto: String,
    promedioPeriodoRes: PromedioPeriodoResponse?,
    cursos: List<CourseGrade>,
    notasProyectadas: Map<String, Any?> = emptyMap(),
    periodo: String? = null,
    onClose: () -> Unit
) {
    val itemsPonderados = remember(cursos, promedioPeriodoRes, notasProyectadas, periodo) {
        cursos.map { c ->
            val crn = c.crn ?: c.courseReferenceNumber ?: ""
            val cr = resolverCreditosCurso(c, promedioPeriodoRes)
            // Usamos estrictamente el acumulado del curso (ej: EP1 de 14 * 20% = 2.8)
            val acumulado = calcularAcumuladoCurso(c, notasProyectadas[crn], periodo)
            val puntos = if (acumulado != null && !acumulado.isNaN() && !acumulado.isInfinite()) acumulado * cr else null
            DetalleCursoPonderado(
                nombre = toTitleCase(c.displayNombre),
                crn = crn,
                nota = acumulado,
                creditos = cr,
                puntos = puntos,
                referencia = referenciaAcumuladaCurso(c, periodo)
            )
        }
    }

    val totalPuntos = itemsPonderados.mapNotNull { it.puntos }.sum()
    val totalCreditosEvaluados = itemsPonderados.filter { it.puntos != null }.sumOf { it.creditos }
    val pCalculado = if (totalCreditosEvaluados > 0) (totalPuntos / totalCreditosEvaluados).takeUnless { it.isNaN() || it.isInfinite() } else null
    val notaMostrar = pCalculado ?: promedioGeneral

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cálculo del Ponderado",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = fuenteTexto,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = formatPromedio(notaMostrar),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = AcumuladoColor(notaMostrar, referenciaPonderada(cursos, periodo, promedioPeriodoRes)),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        item {
            // Tarjeta explicativa de la fórmula UPAO con acumulado
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "FÓRMULA PONDERADA UPAO",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Promedio = Σ(Acumulado × Créditos) ÷ Total Créditos",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "El ponderado se calcula multiplicando el puntaje acumulado de cada asignatura por sus créditos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Text(
                text = "1. MULTIPLICACIÓN POR CURSO (ACUMULADO × CRÉDITOS)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline,
                letterSpacing = 0.5.sp
            )
        }

        items(itemsPonderados) { item ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = item.nombre,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (item.nota != null && item.puntos != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "Acumulado ${formatNota(item.nota)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = AcumuladoColor(item.nota, item.referencia),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text("  ×  ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${item.creditos} créd",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text("  =  ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${formatNota(item.puntos)} pts",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "Pendiente × ${item.creditos} créditos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "-- pts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "2. CÓMO SE SUMA Y CALCULA EL RESULTADO",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline,
                letterSpacing = 0.5.sp
            )
        }

        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "A. Suma total de puntos acumulados:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${formatNota(totalPuntos)} pts",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "B. Suma de créditos evaluados:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "$totalCreditosEvaluados créd",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "C. División final (Puntos ÷ Créditos):",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (totalCreditosEvaluados > 0) {
                                    "${formatNota(totalPuntos)} ÷ $totalCreditosEvaluados = ${formatPromedio(pCalculado)}"
                                } else {
                                    "Sin evaluaciones para dividir"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = formatPromedio(pCalculado),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = AcumuladoColor(pCalculado, referenciaPonderada(cursos, periodo, promedioPeriodoRes)),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Entendido")
            }
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
    // Puntos acumulados ponderados (ej: EP1 de 14 * 20% = 2.8)
    val progreso = com.example.upaos.data.upao.Calculators.progresoCurso(course, periodo)
    val puntajeAcumulado = calcularAcumuladoCurso(course, notaProyectada, periodo)
    val promedioParcial: Double? = progreso?.promedio
        ?: com.example.upaos.data.upao.Calculators.toDouble(course.displayNotaActual)

    val barColor = when {
        promedioParcial == null -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        promedioParcial >= 10.5 -> gradeAccent(10.5)
        else -> gradeAccent(0.0)
    }
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
                .background(barColor)
        )
        AppCard(
            color = MaterialTheme.colorScheme.surface,
            corner = 14.dp,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
            modifier = Modifier.weight(1f),
            onClick = onClick
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = toTitleCase(course.displayNombre),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "ACUMULADO",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = formatNota(puntajeAcumulado),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun ComponenteRow(componente: ComponenteDetalle, periodo: String? = null) {
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
                val pesoTxt = formatPesoTexto(com.example.upaos.data.upao.Calculators.pesoComponente(componente, periodo), componente.displayNombre)
                val puntaje = componente.displayPuntaje
                val detalleTxt = listOfNotNull(puntaje, pesoTxt?.let { if (it.startsWith("Peso", ignoreCase = true)) it else "Peso $it" }).joinToString(" · ")
                if (detalleTxt.isNotBlank()) {
                    Text(
                        text = detalleTxt,
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
                        val subPesoTxt = formatPesoTexto(sub.displayPeso, sub.displayNombre)
                        val subPuntaje = if (sub.puntajeObtenido != null && sub.puntajeSobre != null) {
                            "${sub.puntajeObtenido}/${sub.puntajeSobre}"
                        } else null

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sub.displayNombre,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (subPuntaje != null || subPesoTxt != null) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (subPuntaje != null) {
                                            Text(
                                                text = subPuntaje,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                        if (subPesoTxt != null) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = if (subPesoTxt.startsWith("Peso", ignoreCase = true)) subPesoTxt else "Peso $subPesoTxt",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = displayGrade(sub.displayNota),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = gradeAccent(sub.displayNota),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatPesoTexto(pesoRaw: Any?, fallbackText: String? = null): String? {
    val s = pesoRaw?.toString()?.trim()
    if (!s.isNullOrBlank() && s != "null") {
        if (s.endsWith("%")) return s
        val d = s.toDoubleOrNull()
        if (d != null) {
            val pct = if (d > 0.0 && d <= 1.0) d * 100.0 else d
            val r = (pct * 10.0).roundToInt() / 10.0
            return if (r % 1.0 == 0.0) "${r.toInt()}%" else "$r%"
        }
        return "$s%"
    }
    if (!fallbackText.isNullOrBlank()) {
        val match = Regex("""(\d+(?:\.\d+)?)\s*%""").find(fallbackText)
        if (match != null) return match.value.trim()
    }
    return null
}

fun displayGrade(value: Any?): String = when {
    value == null || value.toString().isBlank() || value.toString() == "null" -> "--"
    else -> value.toString()
}

fun formatNota(value: Any?): String {
    if (isPendiente(value)) return "--"
    val d = value.toString().trim().toDoubleOrNull() ?: return value.toString()
    if (d.isNaN() || d.isInfinite()) return "--"
    val r = (d * 100).roundToInt() / 100.0
    return if (r % 1.0 == 0.0) r.toInt().toString() else r.toString()
}

fun formatPromedio(value: Any?): String {
    if (isPendiente(value)) return "--"
    val d = value?.toString()?.trim()?.toDoubleOrNull() ?: return "--"
    if (d.isNaN() || d.isInfinite()) return "--"
    val r = (d * 100).roundToInt() / 100.0
    return if (r % 1.0 == 0.0) r.toInt().toString() else r.toString()
}

@Composable
fun GradeBadge(label: String, value: Any?) {
    val color = gradeAccent(value)
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

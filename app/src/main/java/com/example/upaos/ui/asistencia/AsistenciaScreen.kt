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
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Science
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
import com.example.upaos.data.local.ApiCache
import com.example.upaos.data.model.AsistenciaComponente
import com.example.upaos.data.model.AsistenciaCurso
import com.example.upaos.data.model.AsistenciaResponse
import com.example.upaos.data.model.HorarioCurso
import com.example.upaos.data.model.HorarioResponse
import com.example.upaos.ui.grades.detectarPeriodoActual
import com.example.upaos.ui.components.AppCard
import com.example.upaos.ui.components.CircularGauge
import com.example.upaos.ui.components.EmptyState
import com.example.upaos.ui.components.ErrorView
import com.example.upaos.ui.components.RefreshableContent
import com.example.upaos.ui.components.SectionHeader
import com.example.upaos.ui.components.SkeletonBox
import com.example.upaos.ui.components.StatusBadge
import com.example.upaos.ui.components.cursoColor
import com.example.upaos.ui.components.toTitleCase
import com.example.upaos.ui.theme.UpaoAmber
import com.example.upaos.ui.theme.UpaoBlue
import com.example.upaos.ui.theme.UpaoGreen
import com.example.upaos.ui.theme.UpaoOrange
import com.example.upaos.ui.theme.UpaoRed
import com.google.gson.Gson
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.example.upaos.data.model.clasificarTipo
import com.example.upaos.data.model.contarDiasHorario

private val dayNames = listOf("LUN", "MAR", "MIE", "JUE", "VIE", "SAB")
private val dayInitials = listOf("L", "M", "M", "J", "V", "S")

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

private fun normalizarNombre(nombre: String): String =
    nombre.uppercase()
        .replace("Á", "A").replace("É", "E").replace("Í", "I")
        .replace("Ó", "O").replace("Ú", "U")
        .replace(Regex("[^A-Z0-9\\s]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun cursosCoinciden(a: AsistenciaCurso, h: HorarioCurso): Boolean {
    val crnA = a.crn?.trim()
    val crnH = h.crn?.trim()
    if (!crnA.isNullOrBlank() && !crnH.isNullOrBlank() && crnA == crnH) return true
    val codigoA = a.codigoMateria?.let { normalizarNombre(it) } ?: ""
    val codigoH = h.codigoMateria?.let { normalizarNombre(it) } ?: ""
    if (codigoA.isNotBlank() && codigoH.isNotBlank() && codigoA == codigoH) return true
    val nombreA = normalizarNombre(a.displayNombre)
    val nombreH = normalizarNombre(h.displayNombre)
    if (nombreA == nombreH) return true
    if (nombreA.contains(nombreH) || nombreH.contains(nombreA)) return true
    return false
}

private fun diasDelHorario(h: HorarioCurso): String {
    val dias = h.bloques.mapNotNull { it.diaNombre }.distinct()
    return if (dias.isNotEmpty()) dias.joinToString(", ") else ""
}

private fun procesarCursosAsistencia(
    registros: List<AsistenciaCurso>,
    horario: List<HorarioCurso>,
    semanaActual: Int? = null
): List<AsistenciaCurso> {
    if (registros.isEmpty() && horario.isEmpty()) return emptyList()

    // 1. Estimar semanas de clase transcurridas analizando registros con faltas > 0
    val semanasDetectadas = mutableListOf<Int>()
    for (r in registros) {
        val f = r.faltas ?: 0
        val p = r.porcentaje ?: 0.0
        if (f > 0 && p > 0.0 && p < 100.0) {
            val a = kotlin.math.round((p * f) / (100.0 - p)).toInt()
            val total = a + f
            val dias = maxOf(1, contarDiasHorario(r.horarioDias))
            val sem = (total.toDouble() / dias).roundToInt()
            if (sem in 1..18) semanasDetectadas.add(sem)
        }
    }

    val semanasValidas = if (semanasDetectadas.isNotEmpty()) {
        semanasDetectadas.sorted()[semanasDetectadas.size / 2]
    } else {
        semanaActual?.takeIf { it in 1..18 } ?: 4
    }

    // 2. Agrupar registros de asistencia por curso (nombre o código).
    // Cuando la API bota 2 registros del mismo curso, uno es Teoría y el otro es Laboratorio.
    val gruposRegistros = mutableMapOf<String, MutableList<AsistenciaCurso>>()
    for (r in registros) {
        val clave = r.codigoMateria?.takeIf { it.isNotBlank() }?.let { normalizarNombre(it) }
            ?: normalizarNombre(r.displayNombre)
        gruposRegistros.getOrPut(clave) { mutableListOf() }.add(r)
    }

    val horarioRestante = horario.toMutableList()
    val resultado = mutableListOf<AsistenciaCurso>()

    for ((clave, listaRegs) in gruposRegistros) {
        val hCoincidente = horarioRestante.firstOrNull { h ->
            val codH = h.codigoMateria?.let { normalizarNombre(it) } ?: ""
            val nomH = normalizarNombre(h.displayNombre)
            clave == codH || clave == nomH || nomH.contains(clave) || clave.contains(nomH)
        }
        if (hCoincidente != null) {
            horarioRestante.remove(hCoincidente)
        }

        val nombreFinal = hCoincidente?.displayNombre ?: listaRegs.first().displayNombre
        val codMateriaFinal = hCoincidente?.codigoMateria ?: listaRegs.first().codigoMateria

        val componentes = listaRegs.mapIndexed { index, reg ->
            val dias = reg.horarioDias?.takeIf { it.isNotBlank() }
                ?: hCoincidente?.let { diasDelHorario(it) }?.takeIf { it.isNotBlank() }
            val diasCount = maxOf(1, contarDiasHorario(dias))
            val clasesEstimadas = semanasValidas * diasCount

            val tipoClasificado = clasificarTipo(
                tipoApi = reg.tipo ?: reg.tipoComponente,
                seccion = reg.seccion,
                nombreCurso = nombreFinal,
                index = index,
                totalComponentes = listaRegs.size
            )

            val asistenciasCalculadas = reg.calcularVecesAsistidas(clasesEstimadas)
                ?: reg.asistencias
                ?: reg.vecesAsistio
                ?: run {
                    val p = reg.porcentaje ?: 100.0
                    val f = reg.faltas ?: 0
                    if (p <= 0.0) 0
                    else if (f > 0 && p < 100.0) kotlin.math.round((p * f) / (100.0 - p)).toInt().coerceAtLeast(0)
                    else (clasesEstimadas - f).coerceAtLeast(1)
                }

            val totalClasesComp = reg.totalClases
                ?: (asistenciasCalculadas + (reg.faltas ?: 0)).takeIf { it > 0 }
                ?: clasesEstimadas

            AsistenciaComponente(
                crn = reg.crn,
                seccion = reg.seccion,
                tipo = tipoClasificado,
                tipoComponente = tipoClasificado,
                porcentaje = reg.porcentaje,
                faltas = reg.faltas ?: 0,
                asistencias = asistenciasCalculadas,
                vecesAsistio = asistenciasCalculadas,
                totalClases = totalClasesComp,
                horarioDias = dias,
                hora = reg.hora,
                hora12h = reg.hora12h,
                aula = reg.aula
            )
        }

        val totalFaltasCurso = componentes.sumOf { it.faltas ?: 0 }
        val totalAsistenciasCurso = componentes.sumOf { it.asistencias ?: it.vecesAsistio ?: 0 }
        val totalClasesCurso = componentes.sumOf { it.totalClases ?: 0 }

        val porcentajeGlobal = if (totalClasesCurso > 0) {
            ((totalAsistenciasCurso.toDouble() / totalClasesCurso.toDouble()) * 100.0)
        } else {
            val pcts = componentes.mapNotNull { it.porcentaje }
            if (pcts.isNotEmpty()) pcts.average() else 100.0
        }

        val todosLosDias = componentes.mapNotNull { it.horarioDias }
            .flatMap { it.split(",", "·").map { d -> d.trim() } }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")

        val crnConsolidado = componentes.mapNotNull { it.crn }.filter { it.isNotBlank() }.distinct().joinToString(" / ")
        val seccionConsolidada = componentes.mapNotNull { it.seccion }.filter { it.isNotBlank() }.distinct().joinToString(" / ")

        resultado.add(
            AsistenciaCurso(
                crn = crnConsolidado.ifBlank { hCoincidente?.crn ?: listaRegs.first().crn },
                materia = nombreFinal,
                codigoMateria = codMateriaFinal,
                nombreCurso = nombreFinal,
                seccion = seccionConsolidada.ifBlank { listaRegs.first().seccion },
                periodo = listaRegs.firstOrNull { !it.periodo.isNullOrBlank() }?.periodo,
                faltas = totalFaltasCurso,
                asistencias = totalAsistenciasCurso,
                vecesAsistio = totalAsistenciasCurso,
                totalClases = totalClasesCurso,
                porcentaje = porcentajeGlobal,
                horarioDias = todosLosDias.ifBlank { hCoincidente?.let { diasDelHorario(it) } ?: listaRegs.first().horarioDias },
                hora = listaRegs.firstOrNull { !it.hora.isNullOrBlank() }?.hora,
                hora12h = listaRegs.firstOrNull { !it.hora12h.isNullOrBlank() }?.hora12h,
                aula = listaRegs.firstOrNull { !it.aula.isNullOrBlank() }?.aula,
                componentes = componentes,
                totalSecciones = componentes.size
            )
        )
    }

    // Cursos del horario que aún no registran asistencia
    for (h in horarioRestante) {
        val diasTxt = diasDelHorario(h)
        resultado.add(
            AsistenciaCurso(
                crn = h.crn,
                materia = h.displayNombre,
                codigoMateria = h.codigoMateria,
                nombreCurso = h.displayNombre,
                seccion = null,
                periodo = null,
                faltas = null,
                asistencias = null,
                vecesAsistio = null,
                totalClases = null,
                porcentaje = null,
                horarioDias = diasTxt,
                hora = null,
                hora12h = null,
                aula = null,
                componentes = emptyList(),
                totalSecciones = null
            )
        )
    }

    return resultado
}


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
    var horarioCursos by remember { mutableStateOf<List<HorarioCurso>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var offline by remember { mutableStateOf(false) }
    var cursoSeleccionado by remember { mutableStateOf<AsistenciaCurso?>(null) }
    var semanaActual by remember { mutableStateOf<Int?>(null) }

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
        offline = false
        scope.launch {
            try {
                Log.d("UPAO_APP", "[Android UI] Consultando asistencia...")
                val res = RetrofitClient.apiService.getAsistencia("Bearer $token")
                isLoading = false
                isRefreshing = false
                val errBody = res.errorBody()?.string()
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
                isRefreshing = false
                if (registros.isNotEmpty() || horarioCursos.isNotEmpty()) {
                    offline = true
                } else {
                    errorMessage = "Sin conexión: ${e.localizedMessage}"
                }
            }
        }
    }

    fun cargarHorario() {
        scope.launch {
            try {
                val periodosRes = RetrofitClient.apiService.getPeriodos("Bearer $token")
                val periodo = if (periodosRes.isSuccessful && periodosRes.body() != null) {
                    val p = periodosRes.body()!!
                    detectarPeriodoActual(p.periodos, p.periodoActual)
                } else {
                    "202610"
                }
                if (horarioCursos.isNotEmpty()) {
                    val cacheJson = cache.cargar("horario_${usuario ?: "anonimo"}_$periodo")
                    if (cacheJson != null) {
                        val cuerpo = gson.fromJson(cacheJson, HorarioResponse::class.java)
                        horarioCursos = cuerpo.cursos
                        return@launch
                    }
                }
                val res = RetrofitClient.apiService.getHorario("Bearer $token", periodo)
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    horarioCursos = body.cursos
                    scope.launch { cache.guardar("horario_${usuario ?: "anonimo"}_$periodo", gson.toJson(body)) }
                }
            } catch (e: Exception) {
                Log.e("UPAO_APP", "[Android UI] Error cargando horario para asistencia: ${e.localizedMessage}", e)
                try {
                    val prefijo = "horario_${usuario ?: "anonimo"}_"
                    val cacheJson = cache.listarPorPrefijo(prefijo).values.lastOrNull()
                    if (cacheJson != null) {
                        val cuerpo = gson.fromJson(cacheJson, HorarioResponse::class.java)
                        if (cuerpo.cursos.isNotEmpty()) {
                            horarioCursos = cuerpo.cursos
                            offline = true
                        }
                    }
                } catch (e2: Exception) {
                    Log.e("UPAO_APP", "[Android UI] Error leyendo caché de horario: ${e2.localizedMessage}", e2)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        aplicarCache()
        cargarHorario()
        scope.launch {
            try {
                val sRes = RetrofitClient.apiService.getSemana()
                if (sRes.isSuccessful && sRes.body() != null) {
                    semanaActual = sRes.body()?.semana
                }
            } catch (_: Exception) {}
        }
        load()
    }

    val cursosVisibles = remember(registros, horarioCursos, semanaActual) {
        procesarCursosAsistencia(registros, horarioCursos, semanaActual)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
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

        Spacer(modifier = Modifier.height(8.dp))

        if (offline && (registros.isNotEmpty() || horarioCursos.isNotEmpty())) {
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

        when {
            isLoading && cursosVisibles.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(3) { SkeletonAsistenciaCard() }
                }
            }
            errorMessage != null && cursosVisibles.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    ErrorView(
                        message = errorMessage!!,
                        onRetry = { load() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            cursosVisibles.isNotEmpty() -> {
                val conDatos = cursosVisibles.filter { it.porcentaje != null }
                val sinDatos = cursosVisibles.filter { it.porcentaje == null }
                val conNota = conDatos.mapNotNull { it.porcentaje }
                val promedio = if (conNota.isNotEmpty()) conNota.average() else 0.0
                val enRiesgo = conDatos.filter { (it.porcentaje ?: 0.0) < 70.0 }
                val optimos = conDatos.count { (it.porcentaje ?: 0.0) >= 90.0 }
                val totalAsistencias = conDatos.sumOf { it.vecesAsistidas ?: 0 }
                val totalFaltas = conDatos.sumOf { it.totalFaltasCalculadas }

                Column(modifier = Modifier.fillMaxSize()) {
                    ResumenAsistencia(promedio, conDatos.size, enRiesgo.size, optimos, totalAsistencias, totalFaltas)
                    Spacer(modifier = Modifier.height(8.dp))
                    RefreshableContent(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            isRefreshing = true
                            load()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (conDatos.isNotEmpty()) {
                                val resto = conDatos.filterNot { it in enRiesgo }
                                if (enRiesgo.isNotEmpty()) {
                                    item {
                                        SectionHeader(
                                            title = "Cursos en riesgo",
                                            subtitle = "Asistencia menor al 70%",
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                    items(enRiesgo) { curso ->
                                        AsistenciaCard(item = curso, onClick = { cursoSeleccionado = curso })
                                    }
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
                                    items(resto) { curso ->
                                        AsistenciaCard(item = curso, onClick = { cursoSeleccionado = curso })
                                    }
                                }
                            }
                            if (sinDatos.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        title = "Sin registros de asistencia",
                                        subtitle = "Estos cursos de tu horario aún no registran asistencias",
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                                items(sinDatos) { curso ->
                                    AsistenciaCard(item = curso, sinDatos = true, onClick = { cursoSeleccionado = curso })
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                EmptyState(
                    icon = Icons.Filled.Schedule,
                    title = "Sin datos de asistencia",
                    subtitle = "No hay datos de asistencia para este periodo.",
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }

    cursoSeleccionado?.let { curso ->
        AsistenciaDetalleModal(
            curso = curso,
            onDismiss = { cursoSeleccionado = null }
        )
    }
}

@Composable
private fun ResumenAsistencia(
    promedio: Double,
    totalCursos: Int,
    enRiesgo: Int,
    optimos: Int,
    totalAsistencias: Int = 0,
    totalFaltas: Int = 0
) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                StatChip(label = "Asistí", value = "$totalAsistencias", color = UpaoGreen)
                StatChip(
                    label = "Falté",
                    value = "$totalFaltas",
                    color = if (totalFaltas > 0) UpaoRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatChip(
                    label = "Riesgo",
                    value = "$enRiesgo",
                    color = if (enRiesgo > 0) UpaoRed else MaterialTheme.colorScheme.onSurfaceVariant
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
fun AsistenciaCard(
    item: AsistenciaCurso,
    sinDatos: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val pct = item.porcentaje ?: 0.0
    val color = if (sinDatos) MaterialTheme.colorScheme.outline else porcentajeColor(pct)
    val (estado, _) = estadoAsistencia(pct)
    val activos = diasActivos(item.horarioDias)
    val courseColor = cursoColor(item.displayNombre)
    val diasNombres = if (activos.isNotEmpty()) {
        activos.sorted().joinToString(" · ") { dayNames[it].lowercase().replaceFirstChar { it.uppercase() } }
    } else {
        null
    }
    val asistencias = item.vecesAsistidas
    val faltas = item.totalFaltasCalculadas
    val totalClases = item.totalClasesCalculadas
    val tieneComponentesMultiples = item.componentes.size > 1

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
            onClick = onClick,
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
                        val meta = if (tieneComponentesMultiples) {
                            item.componentes.joinToString(" · ") { comp ->
                                val t = comp.tipo ?: "Comp"
                                val nrc = comp.crn?.let { "NRC $it" } ?: ""
                                val sec = comp.seccion?.let { "Sec $it" } ?: ""
                                listOf(t, nrc, sec).filter { it.isNotBlank() }.joinToString(" ")
                            }
                        } else {
                            buildString {
                                append("NRC ${item.crn ?: "-"}")
                                item.seccion?.let { append(" · Sec $it") }
                            }
                        }
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (diasNombres != null) {
                            Text(
                                text = diasNombres,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = courseColor
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    if (sinDatos) {
                        StatusBadge(text = "Sin registros", color = MaterialTheme.colorScheme.outline)
                    } else {
                        StatusBadge(text = estado, color = color)
                    }
                    if (onClick != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = "Ver desglose",
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (sinDatos) {
                    Text(
                        text = "Aún no hay registros de asistencia para este curso.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
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

                    Spacer(modifier = Modifier.height(8.dp))

                    // Si tiene múltiples componentes (Teoría y Laboratorio), mostramos desglose
                    if (tieneComponentesMultiples) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            item.componentes.forEach { comp ->
                                val tipoComp = comp.tipo ?: "Componente"
                                val isTeoria = tipoComp.contains("Teor", ignoreCase = true)
                                val isLab = tipoComp.contains("Lab", ignoreCase = true)
                                val compColor = if (isTeoria) UpaoBlue else if (isLab) Color(0xFF7C3AED) else UpaoOrange
                                val compIcon = if (isTeoria) Icons.Filled.MenuBook else if (isLab) Icons.Filled.Science else Icons.Filled.Assignment
                                val compPct = comp.porcentaje ?: 100.0
                                val cAsist = comp.vecesAsistidas ?: 0
                                val cFalt = comp.faltas ?: 0

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = compIcon,
                                            contentDescription = null,
                                            tint = compColor,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = tipoComp.uppercase(),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = compColor
                                        )
                                        if (!comp.seccion.isNullOrBlank()) {
                                            Text(
                                                text = " (${comp.seccion})",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "$cAsist Asistí",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = UpaoGreen
                                        )
                                        Text(
                                            text = "·",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                        Text(
                                            text = "$cFalt Falté",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (cFalt > 0) UpaoRed else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "(${formatPct(compPct)}%)",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = porcentajeColor(compPct)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Fila destacada: Veces que asistí y veces que falté en total
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Badge veces que asistió
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = UpaoGreen.copy(alpha = 0.12f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = UpaoGreen,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (asistencias != null) "$asistencias Asistí${if (tieneComponentesMultiples) " Total" else ""}" else "Asistí",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = UpaoGreen
                                )
                            }
                        }

                        // Badge veces que faltó
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (faltas > 0) UpaoRed.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Cancel,
                                    contentDescription = null,
                                    tint = if (faltas > 0) UpaoRed else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$faltas Falté${if (tieneComponentesMultiples) " Total" else ""}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (faltas > 0) UpaoRed else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Total de clases si está disponible
                        if (totalClases != null && totalClases > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ) {
                                Text(
                                    text = "$totalClases clases",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
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
                    if (diasNombres != null) {
                        Text(
                            text = diasNombres,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AsistenciaDetalleModal(
    curso: AsistenciaCurso,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val componentesTipificados = remember(curso) { curso.componentesTipificados() }
    val (estadoGlobal, colorGlobal) = remember(curso) { estadoAsistencia(curso.porcentaje ?: 0.0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header del curso
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = toTitleCase(curso.displayNombre),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val meta = buildString {
                        append("NRC ${curso.crn ?: "-"}")
                        curso.seccion?.let { append(" · Sección $it") }
                    }
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colorGlobal.copy(alpha = 0.12f),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${formatPct(curso.porcentaje ?: 0.0)}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = colorGlobal
                        )
                        Text(
                            text = estadoGlobal,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorGlobal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Resumen consolidado del curso
            AppCard(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                corner = 12.dp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Total del Curso",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val totalClasesTxt = curso.totalClasesCalculadas?.let { "$it clases registradas" } ?: ""
                        if (totalClasesTxt.isNotBlank()) {
                            Text(
                                text = totalClasesTxt,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatChip(
                            label = "Asistí",
                            value = "${curso.vecesAsistidas ?: 0}",
                            color = UpaoGreen
                        )
                        StatChip(
                            label = "Falté",
                            value = "${curso.totalFaltasCalculadas}",
                            color = if (curso.totalFaltasCalculadas > 0) UpaoRed else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Desglose por Componente",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (componentesTipificados.isNotEmpty()) {
                componentesTipificados.forEach { (tipo, comp) ->
                    ComponenteCard(tipo = tipo, componente = comp)
                    Spacer(modifier = Modifier.height(10.dp))
                }
            } else {
                ComponenteGeneralCard(curso = curso)
            }
        }
    }
}

@Composable
private fun ComponenteCard(
    tipo: String,
    componente: AsistenciaComponente
) {
    val (tipoColor, tipoIcono) = when (tipo) {
        "Teoría" -> UpaoBlue to Icons.Filled.MenuBook
        "Práctica" -> UpaoOrange to Icons.Filled.Assignment
        "Laboratorio" -> Color(0xFF7C3AED) to Icons.Filled.Science
        else -> MaterialTheme.colorScheme.primary to Icons.Filled.CheckCircle
    }

    val pct = componente.porcentaje ?: 100.0
    val (_, colorEstado) = estadoAsistencia(pct)
    val asistidas = componente.vecesAsistidas
    val faltas = componente.faltas ?: 0
    val total = componente.totalClasesCalculadas

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = tipoColor.copy(alpha = 0.15f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = tipoIcono,
                                contentDescription = null,
                                tint = tipoColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = tipo.uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = tipoColor
                            )
                        }
                    }
                    if (!componente.seccion.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "Sec. ${componente.seccion}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "${formatPct(pct)}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = colorEstado
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { (pct / 100f).toFloat().coerceIn(0f, 1f) },
                color = colorEstado,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = UpaoGreen.copy(alpha = 0.12f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = UpaoGreen,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$asistidas Asistí",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = UpaoGreen
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = (if (faltas > 0) UpaoRed else MaterialTheme.colorScheme.outline).copy(alpha = 0.12f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Cancel,
                            contentDescription = null,
                            tint = if (faltas > 0) UpaoRed else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$faltas Falté",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (faltas > 0) UpaoRed else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (total != null && total > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "$total clases",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            val horarioInfo = listOfNotNull(
                componente.horarioDias?.takeIf { it.isNotBlank() },
                componente.hora12h?.takeIf { it.isNotBlank() } ?: componente.hora?.takeIf { it.isNotBlank() },
                componente.aula?.takeIf { it.isNotBlank() }
            ).joinToString(" · ")

            if (horarioInfo.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = horarioInfo,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun ComponenteGeneralCard(curso: AsistenciaCurso) {
    val pct = curso.porcentaje ?: 100.0
    val (_, colorEstado) = estadoAsistencia(pct)
    val asistidas = curso.vecesAsistidas ?: 0
    val faltas = curso.totalFaltasCalculadas
    val total = curso.totalClasesCalculadas

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = UpaoBlue.copy(alpha = 0.15f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MenuBook,
                            contentDescription = null,
                            tint = UpaoBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "TEORÍA / GENERAL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = UpaoBlue
                        )
                    }
                }
                Text(
                    text = "${formatPct(pct)}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = colorEstado
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { (pct / 100f).toFloat().coerceIn(0f, 1f) },
                color = colorEstado,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = UpaoGreen.copy(alpha = 0.12f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = UpaoGreen,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$asistidas Asistí",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = UpaoGreen
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = (if (faltas > 0) UpaoRed else MaterialTheme.colorScheme.outline).copy(alpha = 0.12f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Cancel,
                            contentDescription = null,
                            tint = if (faltas > 0) UpaoRed else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$faltas Falté",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (faltas > 0) UpaoRed else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (total != null && total > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "$total clases",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            if (!curso.horarioDias.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = curso.horarioDias,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

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
import androidx.compose.material.icons.filled.Person
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

private fun porcentajeColor(pct: Double?, tieneRegistro: Boolean = true): Color {
    if (!tieneRegistro || pct == null) return Color(0xFF64748B)
    return when {
        pct >= 90 -> UpaoGreen
        pct >= 70 -> UpaoAmber
        else -> UpaoRed
    }
}

private fun estadoAsistencia(
    pct: Double?,
    faltas: Int = 0,
    tieneRegistro: Boolean = true
): Pair<String, Color> {
    if (!tieneRegistro || pct == null) {
        return "Pendiente de lista" to Color(0xFF64748B)
    }
    if (faltas == 0) {
        return "0 faltas · 100%" to UpaoGreen
    }
    return when {
        pct >= 90 -> "Óptimo" to UpaoGreen
        pct >= 70 -> "Aceptable" to UpaoAmber
        else -> "En riesgo" to UpaoRed
    }
}

private fun diaHoy(): Int = (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7

private fun calcularClasesHastaHoy(
    diasHorario: String?,
    semanaActual: Int,
    periodoSeleccionado: String
): Int {
    if (periodoSeleccionado != "202620") {
        val count = maxOf(1, contarDiasHorario(diasHorario))
        return 16 * count
    }
    val activos = diasActivos(diasHorario)
    val diasCount = maxOf(1, if (activos.isNotEmpty()) activos.size else contarDiasHorario(diasHorario))
    val semanasPrevias = (semanaActual - 1).coerceAtLeast(0)
    val hoyDia = diaHoy() // 0 = LUN, 1 = MAR, 2 = MIE, 3 = JUE, 4 = VIE, 5 = SAB
    val diasPasadosEstaSemana = if (activos.isNotEmpty()) {
        activos.count { it <= hoyDia }
    } else {
        1
    }
    val clases = (semanasPrevias * diasCount) + diasPasadosEstaSemana
    return maxOf(1, clases)
}

private fun obtenerSemanaCiclo(periodo: String, semanaApi: Int?): Int {
    if (semanaApi != null && semanaApi in 1..16) return semanaApi
    if (periodo != "202620") return 16
    val inicio = java.util.Calendar.getInstance().apply {
        set(2026, java.util.Calendar.AUGUST, 31, 0, 0, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val hoy = java.util.Calendar.getInstance()
    val diff = hoy.timeInMillis - inicio.timeInMillis
    if (diff < 0) return 1
    val sem = (diff / (1000L * 60 * 60 * 24 * 7)).toInt() + 1
    return sem.coerceIn(1, 16)
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
    todosRegistros: List<AsistenciaCurso>,
    horario: List<HorarioCurso>,
    semanaActual: Int? = null,
    periodoSeleccionado: String
): List<AsistenciaCurso> {
    if (todosRegistros.isEmpty() && horario.isEmpty()) return emptyList()

    // 1. Filtrar registros por el periodo seleccionado
    val registrosPeriodo = todosRegistros.filter { r ->
        val p = r.periodo?.trim()
        if (!p.isNullOrBlank()) {
            p == periodoSeleccionado
        } else {
            true
        }
    }

    val semanaActualCalculada = obtenerSemanaCiclo(periodoSeleccionado, semanaActual)

    // 3. Agrupar registros ÚNICAMENTE por nombre del curso (nunca por codigoMateria de carrera)
    val gruposRegistros = mutableMapOf<String, MutableList<AsistenciaCurso>>()
    for (r in registrosPeriodo) {
        val clave = normalizarNombre(r.displayNombre)
        gruposRegistros.getOrPut(clave) { mutableListOf() }.add(r)
    }

    val horarioRestante = horario.toMutableList()
    val resultado = mutableListOf<AsistenciaCurso>()

    for ((clave, listaRegs) in gruposRegistros) {
        val hCoincidente = horarioRestante.firstOrNull { h ->
            val crnH = h.crn?.trim()
            val crnMatch = !crnH.isNullOrBlank() && listaRegs.any { reg ->
                reg.crn?.trim() == crnH || reg.componentes.any { it.crn?.trim() == crnH }
            }
            if (crnMatch) return@firstOrNull true
            val nomH = normalizarNombre(h.displayNombre)
            clave == nomH || nomH.contains(clave) || clave.contains(nomH)
        }
        if (hCoincidente != null) {
            horarioRestante.remove(hCoincidente)
        }

        val nombreFinal = hCoincidente?.displayNombre ?: listaRegs.first().displayNombre
        val codMateriaFinal = hCoincidente?.codigoMateria ?: listaRegs.first().codigoMateria

        // Extraer todos los componentes de los registros del curso
        val rawComponentes = mutableListOf<AsistenciaComponente>()
        for (reg in listaRegs) {
            if (reg.componentes.isNotEmpty()) {
                rawComponentes.addAll(reg.componentes)
            } else {
                rawComponentes.add(
                    AsistenciaComponente(
                        crn = reg.crn,
                        seccion = reg.seccion,
                        tipo = reg.tipo ?: reg.tipoComponente,
                        tipoComponente = reg.tipoComponente ?: reg.tipo,
                        porcentaje = reg.porcentaje,
                        faltas = reg.faltas,
                        asistencias = reg.asistencias,
                        vecesAsistio = reg.vecesAsistio,
                        totalClases = reg.totalClases,
                        horarioDias = reg.horarioDias,
                        hora = reg.hora,
                        hora12h = reg.hora12h,
                        aula = reg.aula,
                        docente = reg.docente,
                        profesor = reg.profesor,
                        instructor = reg.instructor
                    )
                )
            }
        }

        // Tipificar componentes:
        // Si hay exactamente 2 componentes (o 2 registros para el mismo curso), uno es Teoría y el otro es Laboratorio
        val componentesTipificados = if (rawComponentes.size == 2) {
            val c1 = rawComponentes[0]
            val c2 = rawComponentes[1]
            val sec1 = c1.seccion?.uppercase() ?: ""
            val sec2 = c2.seccion?.uppercase() ?: ""
            val tip1 = (c1.tipo ?: c1.tipoComponente)?.uppercase() ?: ""
            val tip2 = (c2.tipo ?: c2.tipoComponente)?.uppercase() ?: ""

            val esLab1 = sec1.contains("LAB") || sec1.endsWith("L") || tip1.contains("LAB")
            val esLab2 = sec2.contains("LAB") || sec2.endsWith("L") || tip2.contains("LAB")
            val esTeor1 = sec1.contains("TEOR") || sec1.endsWith("T") || tip1.contains("TEOR")
            val esTeor2 = sec2.contains("TEOR") || sec2.endsWith("T") || tip2.contains("TEOR")

            val (teoriaComp, labComp) = when {
                esLab2 || esTeor1 -> c1 to c2
                esLab1 || esTeor2 -> c2 to c1
                else -> {
                    if (sec1 <= sec2) c1 to c2 else c2 to c1
                }
            }
            listOf(
                teoriaComp.copy(tipo = "Teoría", tipoComponente = "Teoría"),
                labComp.copy(tipo = "Laboratorio", tipoComponente = "Laboratorio")
            )
        } else {
            rawComponentes.mapIndexed { index, comp ->
                val tipoFinal = comp.tipo?.takeIf { it.isNotBlank() } ?: clasificarTipo(
                    tipoApi = comp.tipo ?: comp.tipoComponente,
                    seccion = comp.seccion,
                    nombreCurso = nombreFinal,
                    index = index,
                    totalComponentes = rawComponentes.size
                )
                comp.copy(tipo = tipoFinal, tipoComponente = tipoFinal)
            }
        }

        // Calcular datos detallados para cada componente con lógica de semanas real
        val componentesProcesados = componentesTipificados.mapIndexed { idx, comp ->
            // Priorizar siempre los días reales del Horario del alumno:
            val dias = if (hCoincidente != null) {
                if (hCoincidente.bloques.size == componentesTipificados.size && idx < hCoincidente.bloques.size) {
                    hCoincidente.bloques[idx].diaNombre ?: diasDelHorario(hCoincidente)
                } else {
                    diasDelHorario(hCoincidente)
                }
            } else {
                comp.horarioDias?.takeIf { it.isNotBlank() }
            }
            val clasesEstimadas = calcularClasesHastaHoy(dias, semanaActualCalculada, periodoSeleccionado)

            val tieneRegistro = comp.tieneRegistroAsistencia
            val faltasComp = comp.faltas ?: 0

            val asistenciasCalculadas = if (tieneRegistro) {
                comp.asistencias
                    ?: comp.vecesAsistio
                    ?: comp.calcularVecesAsistidas(clasesEstimadas)
                    ?: run {
                        val p = comp.porcentaje ?: 100.0
                        if (p <= 0.0) 0
                        else if (faltasComp > 0 && p < 100.0) kotlin.math.round((p * faltasComp) / (100.0 - p)).toInt().coerceAtLeast(0)
                        else (clasesEstimadas - faltasComp).coerceAtLeast(1)
                    }
            } else {
                null
            }

            val totalClasesComp = if (tieneRegistro) {
                comp.totalClases
                    ?: asistenciasCalculadas?.let { it + faltasComp }?.takeIf { it > 0 }
                    ?: clasesEstimadas
            } else {
                null
            }

            comp.copy(
                faltas = faltasComp,
                asistencias = asistenciasCalculadas,
                vecesAsistio = asistenciasCalculadas,
                totalClases = totalClasesComp,
                horarioDias = dias,
                porcentaje = if (tieneRegistro) comp.porcentaje else null
            )
        }

        val componentesConRegistro = componentesProcesados.filter { it.tieneRegistroAsistencia }
        val totalFaltasCurso = componentesProcesados.sumOf { it.faltas ?: 0 }
        val totalAsistenciasCurso = if (componentesConRegistro.isNotEmpty()) {
            componentesConRegistro.sumOf { it.asistencias ?: it.vecesAsistio ?: it.vecesAsistidas ?: 0 }
        } else {
            listaRegs.firstOrNull()?.asistencias
                ?: listaRegs.firstOrNull()?.vecesAsistio
                ?: componentesProcesados.firstOrNull()?.asistencias
        }
        val totalClasesCurso = if (componentesConRegistro.isNotEmpty()) {
            componentesConRegistro.sumOf { it.totalClases ?: it.totalClasesCalculadas ?: 0 }
        } else null

        val porcentajeGlobal = if (componentesConRegistro.isNotEmpty()) {
            val totalClasesVal = totalClasesCurso ?: 0
            if (totalClasesVal > 0 && totalAsistenciasCurso != null) {
                ((totalAsistenciasCurso.toDouble() / totalClasesVal.toDouble()) * 100.0)
            } else {
                val pcts = componentesConRegistro.mapNotNull { it.porcentaje }
                if (pcts.isNotEmpty()) pcts.average() else 100.0
            }
        } else {
            null
        }

        val todosLosDias = componentesProcesados.mapNotNull { it.horarioDias }
            .flatMap { it.split(",", "·").map { d -> d.trim() } }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")

        val crnConsolidado = componentesProcesados.mapNotNull { it.crn }.filter { it.isNotBlank() }.distinct().joinToString(" / ")
        val seccionConsolidada = componentesProcesados.mapNotNull { it.seccion }.filter { it.isNotBlank() }.distinct().joinToString(" / ")

        resultado.add(
            AsistenciaCurso(
                crn = crnConsolidado.ifBlank { hCoincidente?.crn ?: listaRegs.first().crn },
                materia = nombreFinal,
                codigoMateria = codMateriaFinal,
                nombreCurso = nombreFinal,
                seccion = seccionConsolidada.ifBlank { listaRegs.first().seccion },
                periodo = periodoSeleccionado,
                faltas = totalFaltasCurso,
                asistencias = totalAsistenciasCurso,
                vecesAsistio = totalAsistenciasCurso,
                totalClases = totalClasesCurso,
                porcentaje = porcentajeGlobal,
                horarioDias = hCoincidente?.let { diasDelHorario(it) }?.takeIf { it.isNotBlank() } ?: todosLosDias.ifBlank { listaRegs.first().horarioDias },
                hora = listaRegs.firstOrNull { !it.hora.isNullOrBlank() }?.hora,
                hora12h = listaRegs.firstOrNull { !it.hora12h.isNullOrBlank() }?.hora12h,
                aula = listaRegs.firstOrNull { !it.aula.isNullOrBlank() }?.aula,
                componentes = componentesProcesados,
                totalSecciones = componentesProcesados.size
            )
        )
    }

    // Cursos del horario del periodo seleccionado que aún no registran asistencia
    for (h in horarioRestante) {
        val diasTxt = diasDelHorario(h)
        resultado.add(
            AsistenciaCurso(
                crn = h.crn,
                materia = h.displayNombre,
                codigoMateria = h.codigoMateria,
                nombreCurso = h.displayNombre,
                seccion = null,
                periodo = periodoSeleccionado,
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


@OptIn(ExperimentalMaterial3Api::class)
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

    var periodos by remember { mutableStateOf(listOf("202620", "202610")) }
    var selectedPeriodo by remember { mutableStateOf("202620") }
    var periodosExpanded by remember { mutableStateOf(false) }

    var registros by remember { mutableStateOf<List<AsistenciaCurso>>(emptyList()) }
    var horarioCursos by remember { mutableStateOf<List<HorarioCurso>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var offline by remember { mutableStateOf(false) }
    var cursoSeleccionado by remember { mutableStateOf<AsistenciaCurso?>(null) }
    var semanaActual by remember { mutableStateOf<Int?>(null) }

    fun actualizarPeriodos(
        listaAsistencia: List<AsistenciaCurso>,
        periodosApi: List<String> = emptyList(),
        periodoActualApi: String? = null
    ) {
        val periodosDeAsistencia = listaAsistencia.mapNotNull { it.periodo?.trim() }.filter { it.isNotBlank() }
        val todos = (periodosApi + periodosDeAsistencia).distinct().filter { it.isNotBlank() }.sortedDescending()
        if (todos.isNotEmpty()) {
            periodos = todos
            if (!todos.contains(selectedPeriodo)) {
                selectedPeriodo = if (todos.contains("202620")) "202620" else detectarPeriodoActual(todos, periodoActualApi)
            }
        }
    }

    fun aplicarCache() {
        scope.launch {
            try {
                if (registros.isNotEmpty()) return@launch
                val json = cache.cargar(claveCache) ?: return@launch
                val body = gson.fromJson(json, AsistenciaResponse::class.java)
                registros = body.asistencia
                actualizarPeriodos(body.asistencia)
                Log.d("UPAO_APP", "[Android UI] Caché aplicada: ${registros.size} registros de asistencia")
            } catch (e: Exception) {
                Log.e("UPAO_APP", "[Android UI] Error leyendo caché de asistencia: ${e.localizedMessage}", e)
            }
        }
    }

    fun cargarHorario(term: String) {
        scope.launch {
            try {
                val cacheJson = cache.cargar("horario_${usuario ?: "anonimo"}_$term")
                if (cacheJson != null) {
                    val cuerpo = gson.fromJson(cacheJson, HorarioResponse::class.java)
                    horarioCursos = cuerpo.cursos
                }
                val res = RetrofitClient.apiService.getHorario("Bearer $token", term)
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    horarioCursos = body.cursos
                    scope.launch { cache.guardar("horario_${usuario ?: "anonimo"}_$term", gson.toJson(body)) }
                }
            } catch (e: Exception) {
                Log.e("UPAO_APP", "[Android UI] Error cargando horario para asistencia: ${e.localizedMessage}", e)
                try {
                    val prefijo = "horario_${usuario ?: "anonimo"}_"
                    val cacheMap = cache.listarPorPrefijo(prefijo)
                    val cacheJson = cacheMap[prefijo + term] ?: cacheMap.values.lastOrNull()
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

    fun load() {
        isLoading = true
        errorMessage = null
        offline = false
        scope.launch {
            try {
                try {
                    val sRes = RetrofitClient.apiService.getSemana()
                    if (sRes.isSuccessful && sRes.body() != null) {
                        semanaActual = sRes.body()?.semana
                    }
                } catch (_: Exception) {}

                if (!usuario.isNullOrBlank()) {
                    try {
                        RetrofitClient.apiService.actualizarAhora(usuario)
                    } catch (_: Exception) {}
                }

                Log.d("UPAO_APP", "[Android UI] Consultando asistencia...")
                val res = RetrofitClient.apiService.getAsistencia("Bearer $token")
                isLoading = false
                isRefreshing = false
                val errBody = res.errorBody()?.string()
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    registros = body.asistencia
                    actualizarPeriodos(body.asistencia)
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

    LaunchedEffect(Unit) {
        aplicarCache()
        try {
            val periodosRes = RetrofitClient.apiService.getPeriodos("Bearer $token")
            if (periodosRes.isSuccessful && periodosRes.body() != null) {
                val p = periodosRes.body()!!
                actualizarPeriodos(registros, p.periodos, p.periodoActual)
            }
        } catch (e: Exception) {
            Log.e("UPAO_APP", "Error cargando periodos: ${e.localizedMessage}")
        }
        cargarHorario(selectedPeriodo)
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

    val cursosVisibles = remember(registros, horarioCursos, semanaActual, selectedPeriodo) {
        procesarCursosAsistencia(registros, horarioCursos, semanaActual, selectedPeriodo)
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
                text = "Resumen de inasistencias del periodo $selectedPeriodo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Selector de Periodo (igual que en Notas y Horario)
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
                                cargarHorario(item)
                            }
                        )
                    }
                }
            }
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
                val enRiesgo = conDatos.filter { (it.porcentaje ?: 0.0) < 70.0 }
                val semanaCiclo = obtenerSemanaCiclo(selectedPeriodo, semanaActual)

                Column(modifier = Modifier.fillMaxSize()) {
                    ResumenAsistencia(
                        semanaCiclo = semanaCiclo,
                        periodo = selectedPeriodo
                    )
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
    semanaCiclo: Int = 2,
    periodo: String = "202620"
) {
    AppCard(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        corner = 14.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Semana $semanaCiclo de 16",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (periodo == "202620") "Periodo $periodo · En curso" else "Periodo $periodo · Culminado",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Text(
                    text = "Semana $semanaCiclo/16",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
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
    val tieneRegistro = item.tieneRegistroAsistencia && !sinDatos
    val pct = item.porcentaje
    val color = porcentajeColor(pct, tieneRegistro)
    val faltas = item.totalFaltasCalculadas
    val (estado, _) = estadoAsistencia(pct, faltas, tieneRegistro)
    val activos = diasActivos(item.horarioDias)
    val courseColor = cursoColor(item.displayNombre)
    val diasNombres = if (activos.isNotEmpty()) {
        activos.sorted().joinToString(" · ") { dayNames[it].lowercase().replaceFirstChar { it.uppercase() } }
    } else {
        null
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
                            maxLines = 2
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
                    StatusBadge(text = estado, color = color)
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

                if (!tieneRegistro) {
                    Text(
                        text = "Aún no pasaron lista o no hay registro de asistencia (0 faltas).",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    val pctVal = pct ?: 100.0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { (pctVal / 100f).toFloat().coerceIn(0f, 1f) },
                            color = color,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .height(7.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Text(
                            text = "${formatPct(pctVal)}%",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Fila destacada: Faltas
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (faltas > 0) UpaoRed.copy(alpha = 0.12f) else UpaoGreen.copy(alpha = 0.10f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (faltas > 0) Icons.Filled.Cancel else Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = if (faltas > 0) UpaoRed else UpaoGreen,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (faltas == 1) "1 Falta" else "$faltas Faltas",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (faltas > 0) UpaoRed else UpaoGreen
                            )
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

                    val tieneRegistroCurso = curso.tieneRegistroAsistencia
                    if (!tieneRegistroCurso) {
                        StatusBadge(text = "Pendiente de lista", color = MaterialTheme.colorScheme.outline)
                    } else {
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
                                    text = "${formatPct(curso.porcentaje ?: 100.0)}%",
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
                    Text(
                        text = "Total del Curso",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    StatChip(
                        label = "Faltas",
                        value = "${curso.totalFaltasCalculadas}",
                        color = if (curso.totalFaltasCalculadas > 0) UpaoRed else UpaoGreen
                    )
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

    val tieneRegistro = componente.tieneRegistroAsistencia
    val pct = componente.porcentaje
    val colorEstado = porcentajeColor(pct, tieneRegistro)
    val faltas = componente.faltas ?: 0

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

                if (!tieneRegistro) {
                    StatusBadge(text = "Pendiente de lista", color = MaterialTheme.colorScheme.outline)
                } else {
                    Text(
                        text = "${formatPct(pct ?: 100.0)}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = colorEstado
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!tieneRegistro) {
                Text(
                    text = "Aún no se ha registrado asistencia para esta sección.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                val pctVal = pct ?: 100.0
                LinearProgressIndicator(
                    progress = { (pctVal / 100f).toFloat().coerceIn(0f, 1f) },
                    color = colorEstado,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = (if (faltas > 0) UpaoRed else UpaoGreen).copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (faltas > 0) Icons.Filled.Cancel else Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = if (faltas > 0) UpaoRed else UpaoGreen,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (faltas == 1) "1 Falta" else "$faltas Faltas",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (faltas > 0) UpaoRed else UpaoGreen
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

            val doc = componente.displayDocente
            if (!doc.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "Docente",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Docente: ${toTitleCase(doc)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun ComponenteGeneralCard(curso: AsistenciaCurso) {
    val tieneRegistro = curso.tieneRegistroAsistencia
    val pct = curso.porcentaje
    val colorEstado = porcentajeColor(pct, tieneRegistro)
    val faltas = curso.totalFaltasCalculadas

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
                if (!tieneRegistro) {
                    StatusBadge(text = "Pendiente de lista", color = MaterialTheme.colorScheme.outline)
                } else {
                    Text(
                        text = "${formatPct(pct ?: 100.0)}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = colorEstado
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!tieneRegistro) {
                Text(
                    text = "Aún no se ha registrado asistencia.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                val pctVal = pct ?: 100.0
                LinearProgressIndicator(
                    progress = { (pctVal / 100f).toFloat().coerceIn(0f, 1f) },
                    color = colorEstado,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = (if (faltas > 0) UpaoRed else UpaoGreen).copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (faltas > 0) Icons.Filled.Cancel else Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = if (faltas > 0) UpaoRed else UpaoGreen,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (faltas == 1) "1 Falta" else "$faltas Faltas",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (faltas > 0) UpaoRed else UpaoGreen
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

            val doc = curso.displayDocente
            if (!doc.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "Docente",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Docente: ${toTitleCase(doc)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

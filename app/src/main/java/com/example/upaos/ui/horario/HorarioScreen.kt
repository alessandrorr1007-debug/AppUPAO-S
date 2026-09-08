package com.example.upaos.ui.horario

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Room
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.upaos.data.api.RetrofitClient
import com.example.upaos.data.local.ApiCache
import com.example.upaos.data.model.AsistenciaComponente
import com.example.upaos.data.model.AsistenciaCurso
import com.example.upaos.data.model.AsistenciaResponse
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
import com.example.upaos.ui.theme.UpaoBlue
import com.example.upaos.ui.theme.UpaoOrange
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

private fun normalizarNombre(nombre: String): String =
    nombre.uppercase()
        .replace("Á", "A").replace("É", "E").replace("Í", "I")
        .replace("Ó", "O").replace("Ú", "U")
        .replace(Regex("[^A-Z0-9\\s]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

private data class InfoBloqueComponente(
    val tipo: String, // "Teoría", "Laboratorio", "Práctica" o "Clase"
    val nrc: String?,
    val seccion: String?,
    val docente: String?
)

private fun formatDocente(docente: String?): String {
    if (docente.isNullOrBlank()) return "Docente no asignado en el sistema"
    return if (docente.contains(" / ")) {
        docente.split(" / ").map { toTitleCase(it.trim()) }.filter { it.isNotBlank() }.joinToString(" / ")
    } else {
        toTitleCase(docente)
    }
}

/**
 * Deduce el componente correspondiente a un bloque específico de un curso,
 * priorizando los datos directos del horario (docente, tipo, aula, NRC) extraídos por el backend,
 * y complementando con los registros de asistencia.
 */
private fun deducirInfoComponente(
    curso: HorarioCurso,
    bloque: HorarioBloque,
    asistenciaCursos: List<AsistenciaCurso>
): InfoBloqueComponente {
    val nomNormalizado = normalizarNombre(curso.displayNombre)
    val codMateria = curso.codigoMateria?.trim()?.uppercase() ?: ""

    // 1. Datos directos del bloque o del curso en horario (extraídos por el backend)
    val docBloque = bloque.displayDocente?.takeIf { it.isNotBlank() }
    val docCurso = curso.displayDocente?.takeIf { it.isNotBlank() }
    val tipoDirecto = bloque.displayTipo?.takeIf { it.isNotBlank() }
    val nrcDirecto = (bloque.nrc ?: bloque.crn)?.takeIf { it.isNotBlank() }
    val secDirecta = (bloque.seccion ?: curso.seccion)?.takeIf { it.isNotBlank() }

    // Buscar el curso en la lista de asistencia
    val asisMatch = asistenciaCursos.firstOrNull { a ->
        val crnA = a.crn?.trim()
        val crnH = curso.crn?.trim()
        if (!crnA.isNullOrBlank() && !crnH.isNullOrBlank() && (crnA == crnH || a.componentes.any { it.crn?.trim() == crnH })) return@firstOrNull true
        val codA = a.codigoMateria?.trim()?.uppercase() ?: ""
        val nomA = normalizarNombre(a.displayNombre)
        if (codMateria.isNotBlank() && codA.isNotBlank() && codMateria == codA) {
            nomA == nomNormalizado || nomA.contains(nomNormalizado) || nomNormalizado.contains(nomA)
        } else {
            nomA == nomNormalizado || nomA.contains(nomNormalizado) || nomNormalizado.contains(nomA)
        }
    }

    val componentes = asisMatch?.componentes ?: emptyList()
    val totalBloques = curso.bloques.size
    val indiceBloque = curso.bloques.indexOf(bloque).let { if (it >= 0) it else 0 }

    if (componentes.size == 2) {
        val c1 = componentes[0]
        val c2 = componentes[1]
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
            else -> if (sec1 <= sec2) c1 to c2 else c2 to c1
        }

        val compElegido = if (indiceBloque == 0) teoriaComp else labComp
        val tipoNombre = tipoDirecto ?: if (indiceBloque == 0) "Teoría" else "Laboratorio"
        val docenteFinal = docBloque
            ?: compElegido.displayDocente
            ?: docCurso
            ?: asisMatch?.displayDocente

        return InfoBloqueComponente(
            tipo = tipoNombre,
            nrc = nrcDirecto ?: compElegido.crn?.takeIf { it.isNotBlank() } ?: curso.crn,
            seccion = secDirecta ?: compElegido.seccion?.takeIf { it.isNotBlank() } ?: asisMatch?.seccion,
            docente = docenteFinal
        )
    } else if (componentes.isNotEmpty()) {
        val comp = if (indiceBloque < componentes.size) componentes[indiceBloque] else componentes.first()
        val tipoNombre = tipoDirecto
            ?: comp.tipo?.takeIf { it.isNotBlank() }
            ?: comp.tipoComponente?.takeIf { it.isNotBlank() }
            ?: if (indiceBloque == 0) "Teoría" else "Laboratorio"
        val docenteFinal = docBloque
            ?: comp.displayDocente
            ?: docCurso
            ?: asisMatch?.displayDocente

        return InfoBloqueComponente(
            tipo = tipoNombre,
            nrc = nrcDirecto ?: comp.crn?.takeIf { it.isNotBlank() } ?: curso.crn,
            seccion = secDirecta ?: comp.seccion?.takeIf { it.isNotBlank() } ?: asisMatch?.seccion,
            docente = docenteFinal
        )
    }

    // Si no hay desglose en asistencia, deducir por la información directa del horario o por la posición del bloque
    val tipoDefecto = tipoDirecto ?: if (totalBloques >= 2) {
        if (indiceBloque == 0) "Teoría" else "Laboratorio"
    } else {
        "Teoría"
    }
    val docenteFinal = docBloque
        ?: docCurso
        ?: asisMatch?.displayDocente

    return InfoBloqueComponente(
        tipo = tipoDefecto,
        nrc = nrcDirecto ?: curso.crn,
        seccion = secDirecta ?: asisMatch?.seccion,
        docente = docenteFinal
    )
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
    var asistenciaCursos by remember { mutableStateOf<List<AsistenciaCurso>>(emptyList()) }
    var bloqueSeleccionado by remember { mutableStateOf<Pair<HorarioCurso, HorarioBloque>?>(null) }

    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var offline by remember { mutableStateOf(false) }
    fun claveCache(): String = "horario_${usuario ?: "anonimo"}_$selectedPeriodo"

    fun aplicarCache() {
        scope.launch {
            try {
                // Cargar asistencia de caché para asociar docentes y NRCs específicos de componentes
                val asisJson = cache.cargar("asistencia_${usuario ?: "anonimo"}")
                if (asisJson != null) {
                    val asisBody = gson.fromJson(asisJson, AsistenciaResponse::class.java)
                    asistenciaCursos = asisBody.asistencia
                }

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
                cursos = body.listaCursos
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
                    cursos = body.listaCursos
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

    fun cargarAsistencia() {
        scope.launch {
            try {
                val res = RetrofitClient.apiService.getAsistencia("Bearer $token")
                if (res.isSuccessful && res.body() != null) {
                    asistenciaCursos = res.body()!!.asistencia
                    cache.guardar("asistencia_${usuario ?: "anonimo"}", gson.toJson(res.body()!!))
                }
            } catch (_: Exception) {
                // Si falla la red, ya se cargó de la caché en aplicarCache()
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
            cargarAsistencia()
        } catch (e: Exception) {
            aplicarCache()
            loadHorario()
            cargarAsistencia()
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
                        cargarAsistencia()
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (proxima != null) {
                            ProximaClaseCard(
                                proxima = proxima,
                                onClick = {
                                    bloqueSeleccionado = proxima.curso to proxima.bloque
                                }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        HorarioSemanalGrid(
                            cursos = cursos,
                            asistenciaCursos = asistenciaCursos,
                            onBloqueClick = { curso, bloque ->
                                bloqueSeleccionado = curso to bloque
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    bloqueSeleccionado?.let { (curso, bloque) ->
        val infoComp = deducirInfoComponente(curso, bloque, asistenciaCursos)
        HorarioDetalleModal(
            curso = curso,
            bloque = bloque,
            infoComponente = infoComp,
            onDismiss = { bloqueSeleccionado = null }
        )
    }
}

@Composable
private fun ProximaClaseCard(
    proxima: ProximaClase,
    onClick: () -> Unit
) {
    val color = cursoColor(proxima.curso.displayNombre)
    val bloque = proxima.bloque
    val hora = listOf(
        bloque.horaInicio12h ?: bloque.horaInicio,
        bloque.horaFin12h ?: bloque.horaFin
    ).filterNotNull().joinToString(" - ")
    AppCard(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        corner = 12.dp,
        onClick = onClick,
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
private fun HorarioSemanalGrid(
    cursos: List<HorarioCurso>,
    asistenciaCursos: List<AsistenciaCurso>,
    onBloqueClick: (HorarioCurso, HorarioBloque) -> Unit,
    modifier: Modifier = Modifier
) {
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
                            val infoComp = deducirInfoComponente(c.curso, c.bloque, asistenciaCursos)
                            BloqueColumna(
                                curso = c.curso,
                                bloque = c.bloque,
                                infoComponente = infoComp,
                                esHoy = index == hoy,
                                onClick = { onBloqueClick(c.curso, c.bloque) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BloqueColumna(
    curso: HorarioCurso,
    bloque: HorarioBloque,
    infoComponente: InfoBloqueComponente,
    esHoy: Boolean,
    onClick: () -> Unit
) {
    val color = cursoColor(curso.displayNombre)
    val esLab = infoComponente.tipo.contains("LAB", ignoreCase = true)
    val badgeColor = if (esLab) Color(0xFF7C3AED) else UpaoBlue

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (esHoy) color.copy(alpha = 0.12f) else color.copy(alpha = 0.06f))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = badgeColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = infoComponente.tipo.uppercase(),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            if (!infoComponente.nrc.isNullOrBlank()) {
                Text(
                    text = "NRC ${infoComponente.nrc}",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = toTitleCase(curso.displayNombre),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (esHoy) color else MaterialTheme.colorScheme.onSurface,
            maxLines = 2
        )

        if (curso.displayCodigo.isNotBlank()) {
            Text(
                text = curso.displayCodigo,
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
        val docenteBloque = infoComponente.docente?.takeIf { it.isNotBlank() }
        if (docenteBloque != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = formatDocente(docenteBloque),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Modal BottomSheet interactivo que se despliega al pulsar un bloque del horario.
 * Muestra el desglose detallado con distinción de Teoría / Laboratorio,
 * NRC específico del componente, aula, horario y nombre del docente.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HorarioDetalleModal(
    curso: HorarioCurso,
    bloque: HorarioBloque,
    infoComponente: InfoBloqueComponente,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val esLab = infoComponente.tipo.contains("LAB", ignoreCase = true)
    val compColor = if (esLab) Color(0xFF7C3AED) else UpaoBlue
    val compIcon = if (esLab) Icons.Filled.Science else Icons.Filled.MenuBook

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header del Curso
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
                    Text(
                        text = curso.displayCodigo.ifBlank { "Curso Universitario" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = compColor.copy(alpha = 0.15f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = compIcon,
                            contentDescription = null,
                            tint = compColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = infoComponente.tipo.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = compColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tarjeta de Información Detallada del Componente
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Detalles de la Clase",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // NRC y Sección
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Text(
                                    text = "NRC COMPONENTE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = infoComponente.nrc ?: curso.crn ?: "—",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (!infoComponente.seccion.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    Text(
                                        text = "SECCIÓN",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Sec. ${infoComponente.seccion}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Horario y Día
                    val horaTxt = listOfNotNull(
                        bloque.horaInicio12h ?: bloque.horaInicio,
                        bloque.horaFin12h ?: bloque.horaFin
                    ).joinToString(" - ")

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccessTime,
                            contentDescription = "Horario",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Día y Horario",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "${bloque.diaNombre ?: "Día programado"} · $horaTxt",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Aula
                    if (!bloque.aula.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Room,
                                contentDescription = "Aula",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Ubicación / Aula",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = bloque.aula!!,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Nombre del Profesor / Docente
                    Spacer(modifier = Modifier.height(8.dp))
                    val docNombre = infoComponente.docente?.takeIf { it.isNotBlank() }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (docNombre != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (docNombre != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = "Docente",
                                    tint = if (docNombre != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "DOCENTE / PROFESOR",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatDocente(docNombre),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (docNombre != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}


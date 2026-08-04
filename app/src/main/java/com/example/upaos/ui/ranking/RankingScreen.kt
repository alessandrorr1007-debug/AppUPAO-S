package com.example.upaos.ui.ranking

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.upaos.data.api.RetrofitClient
import com.example.upaos.data.local.GradesCache
import com.example.upaos.data.model.CourseGrade
import com.example.upaos.data.model.GradesResponse
import com.example.upaos.data.model.RankingResponse
import com.example.upaos.data.model.normalizarCourseId
import com.google.gson.Gson

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(
    usuario: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val cache = remember { GradesCache(context) }
    val gson = remember { Gson() }

    var cargando by remember { mutableStateOf(true) }
    var cursos by remember { mutableStateOf<List<CourseGrade>>(emptyList()) }
    var periodo by remember { mutableStateOf<String?>(null) }
    var rankings by remember { mutableStateOf<Map<String, RankingResponse>>(emptyMap()) }
    var optinActivo by remember { mutableStateOf(true) }

    LaunchedEffect(usuario) {
        if (usuario == null) {
            cargando = false
            return@LaunchedEffect
        }
        try {
            val json = cache.cargar("notas_$usuario")
            val body: GradesResponse? = json?.let { runCatching { gson.fromJson(it, GradesResponse::class.java) }.getOrNull() }
            cursos = body?.cursos.orEmpty().filter { curso ->
                val (materia, numero) = codigoMateriaNumero(curso)
                normalizarCourseId(materia, numero).isNotBlank()
            }
            periodo = body?.periodo

            // Consulta secuencial del ranking de cada curso (pocos cursos, evita saturar).
            val mapa = mutableMapOf<String, RankingResponse>()
            for (curso in cursos) {
                val (materia, numero) = codigoMateriaNumero(curso)
                val courseId = normalizarCourseId(materia, numero)
                val ciclo = periodo ?: "202610"
                try {
                    val res = RetrofitClient.apiService.getRanking(usuario, courseId, ciclo)
                    if (res.isSuccessful && res.body() != null) {
                        val rank = res.body()!!
                        if (rank.motivo == "optin_inactivo") optinActivo = false
                        mapa[courseId] = rank
                    }
                } catch (e: Exception) {
                    // Curso sin ranking disponible: se muestra el estado por defecto.
                }
            }
            rankings = mapa
        } catch (e: Exception) {
            // Caché vacía o corrupta: se muestra el estado vacío.
        }
        cargando = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Ranking de cursos", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        when {
            cargando -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            cursos.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Leaderboard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Aún no tienes cursos disponibles",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Abre primero la pestaña de Notas para cargar tus cursos del periodo.",
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!optinActivo) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "La participación en el ranking está desactivada. Actívala desde Ajustes para ver tu posición.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            text = if (periodo != null) "Periodo $periodo" else "Periodo actual",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(cursos, key = { it.crn ?: it.displayNombre }) { curso ->
                        RankingCursoCard(
                            curso = curso,
                            ranking = rankings[normalizarCourseId(
                                codigoMateriaNumero(curso).first,
                                codigoMateriaNumero(curso).second
                            )]
                        )
                    }
                }
            }
        }
    }
}

private fun codigoMateriaNumero(curso: CourseGrade): Pair<Any?, Any?> {
    val raw = curso.rawBanner
    return Pair(raw?.get("subjectCode"), raw?.get("courseNumber"))
}

@Composable
fun RankingCursoCard(curso: CourseGrade, ranking: RankingResponse?) {
    val (texto, color) = when (ranking?.motivo) {
        "optin_inactivo" -> "Activa la participación desde Ajustes" to null
        "insuficientes" -> "Aún no hay suficientes participantes (mín. ${ranking.minUsuarios})" to null
        "sin_datos" -> "Consulta tus notas para entrar al ranking" to null
        null -> "Posición no disponible todavía" to null
        else -> if (ranking.disponible) {
            "Tu posición: ${ranking.position} de ${ranking.total} · top ${ranking.percentil}%" to MaterialTheme.colorScheme.primary
        } else {
            "Posición no disponible todavía" to null
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = curso.displayNombre,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = texto,
                    style = MaterialTheme.typography.bodySmall,
                    color = color ?: MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (ranking?.disponible == true && ranking.position != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "#${ranking.position}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

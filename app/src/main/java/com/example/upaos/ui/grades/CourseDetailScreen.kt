package com.example.upaos.ui.grades
import com.example.upaos.ui.components.CampusTopBar
import com.example.upaos.ui.components.CampusCard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.upaos.data.model.ComponenteDetalle
import com.example.upaos.ui.components.AppCard
import com.example.upaos.ui.components.EmptyState
import com.example.upaos.ui.components.gradeAccent
import com.example.upaos.ui.components.isPendiente
import com.example.upaos.ui.components.toTitleCase
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    token: String,
    periodo: String,
    carrera: String,
    crn: String,
    courseName: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var componentes by remember { mutableStateOf<List<ComponenteDetalle>>(emptyList()) }
    var notaProyectada by remember { mutableStateOf<Any?>(null) }
    var pesosPendientes by remember { mutableStateOf<List<String>>(emptyList()) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val upaoRepository = remember { com.example.upaos.data.upao.UpaoRepository(context) }

    fun load() {
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                val body = upaoRepository.getDetalleCurso(periodo, carrera, crn)
                isLoading = false
                if (body.success && body.detalles.isNotEmpty()) {
                    componentes = body.detalles
                    notaProyectada = body.notaProyectada
                    pesosPendientes = body.pesosPendientes
                } else if (body.success) {
                    componentes = body.detalles
                    notaProyectada = body.notaProyectada
                    pesosPendientes = body.pesosPendientes
                } else {
                    errorMessage = "Sin desglose disponible"
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) {
                isLoading = false
                errorMessage = "En espera de desgloses"
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CampusTopBar(
                title = { Text(toTitleCase(courseName).ifBlank { "Detalle del curso" }, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {

            when {
                isLoading -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.8.dp)
                            Text("Consultando componentes...", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
                errorMessage != null && componentes.isEmpty() -> {
                    EmptyState(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        title = "Sin componentes disponibles",
                        subtitle = errorMessage,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                componentes.isEmpty() -> {
                    EmptyState(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        title = "Sin componentes registrados",
                        subtitle = "No hay componentes de evaluación para este curso.",
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                else -> {
                    val progreso = com.example.upaos.data.upao.Calculators.progreso(componentes, periodo)
                    val puntosAcumulados = progreso?.acumulado ?: 0.0
                    val promedioParcial = progreso?.promedio
                    if (promedioParcial != null || notaProyectada != null) {
                        CampusCard(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "PROMEDIO PARCIAL",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            text = formatNota(promedioParcial ?: puntosAcumulados),
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = gradeAccent(promedioParcial ?: puntosAcumulados)
                                        )
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            Text(
                                                text = "ACUMULADO",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                letterSpacing = 0.5.sp
                                            )
                                            Text(
                                                text = "${formatNota(puntosAcumulados)} / 20 pts",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AcumuladoColor(progreso?.acumulado, progreso?.minimoAcumulado)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Text("Componentes", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    AppCard(
                        color = MaterialTheme.colorScheme.surface,
                        corner = 20.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            componentes.forEachIndexed { index, componente ->
                                if (index > 0) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                                ComponenteRow(componente, periodo)
                            }
                        }
                    }
                }
            }
        }
    }
}

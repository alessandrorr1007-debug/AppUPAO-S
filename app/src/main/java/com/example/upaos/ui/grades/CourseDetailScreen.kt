package com.example.upaos.ui.grades

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
import com.example.upaos.data.api.RetrofitClient
import com.example.upaos.data.model.ComponenteDetalle
import com.example.upaos.ui.components.AppCard
import com.example.upaos.ui.components.EmptyState
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

    fun load() {
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                val req = mapOf(
                    "periodo" to periodo,
                    "carrera" to carrera,
                    "crn" to crn
                )
                val res = RetrofitClient.apiService.getDetalleCurso("Bearer $token", req)
                isLoading = false
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    if (body.success) {
                        componentes = body.detalles
                        notaProyectada = body.notaProyectada
                        pesosPendientes = body.pesosPendientes
                    } else {
                        errorMessage = "Sin desglose disponible"
                    }
                } else {
                    errorMessage = "Sin desglose disponible"
                }
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
            TopAppBar(
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
            if (crn.isNotBlank()) {
                Text(
                    text = "NRC $crn",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

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
                    if (notaProyectada != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Nota proyectada: ${displayGrade(notaProyectada)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                if (pesosPendientes.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Por evaluar: ${pesosPendientes.joinToString(", ")}",
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    Text("Componentes", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    AppCard(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        corner = 14.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            componentes.forEachIndexed { index, componente ->
                                if (index > 0) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                                ComponenteRow(componente)
                            }
                        }
                    }
                }
            }
        }
    }
}

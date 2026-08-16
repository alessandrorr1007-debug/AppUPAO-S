package com.example.upaos.ui.grades

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.upaos.data.api.RetrofitClient
import com.example.upaos.data.model.ComponenteDetalle
import com.example.upaos.ui.components.AppCard
import com.example.upaos.ui.components.EmptyState
import com.example.upaos.ui.components.ErrorView
import com.example.upaos.ui.components.SkeletonBox
import com.example.upaos.ui.components.gradeColor
import com.example.upaos.ui.components.gradeValue
import com.example.upaos.ui.components.toTitleCase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleComponentesScreen(
    token: String,
    periodo: String,
    carrera: String,
    crn: String,
    nombre: String,
    onBack: () -> Unit
) {
    var cargando by remember { mutableStateOf(true) }
    var componentes by remember { mutableStateOf<List<ComponenteDetalle>>(emptyList()) }
    var notaProyectada by remember { mutableStateOf<Any?>(null) }
    var pesosPendientes by remember { mutableStateOf<List<String>>(emptyList()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val req = mapOf("periodo" to periodo, "carrera" to carrera, "crn" to crn)
            val res = RetrofitClient.apiService.getDetalleCurso("Bearer $token", req)
            cargando = false
            if (res.isSuccessful && res.body() != null) {
                val body = res.body()!!
                if (body.success) {
                    componentes = body.detalles
                    notaProyectada = body.notaProyectada
                    pesosPendientes = body.pesosPendientes
                } else {
                    errorMsg = "Sin desglose disponible"
                }
            } else {
                errorMsg = "Sin desglose disponible"
            }
        } catch (e: Exception) {
            cargando = false
            errorMsg = "En espera de desgloses"
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = toTitleCase(nombre),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (crn.isNotBlank()) {
                            Text(
                                text = "CRN $crn",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                cargando -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(4) {
                        AppCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(14.dp),
                            corner = 16.dp
                        ) {
                            SkeletonBox(modifier = Modifier.fillMaxWidth(0.7f).height(14.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            SkeletonBox(modifier = Modifier.fillMaxWidth().height(8.dp))
                        }
                    }
                }
                errorMsg != null -> ErrorView(
                    message = errorMsg!!,
                    modifier = Modifier.align(Alignment.Center)
                )
                componentes.isEmpty() -> EmptyState(
                    icon = Icons.Filled.Grade,
                    title = "Sin desglose de notas",
                    subtitle = "Aún no hay componentes publicados para este curso.",
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { NotaProyectadaCard(notaProyectada, pesosPendientes) }
                    items(componentes) { componente ->
                        DetalleComponenteItem(componente)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotaProyectadaCard(notaProyectada: Any?, pesosPendientes: List<String>) {
    if (notaProyectada == null) return
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Nota proyectada",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (pesosPendientes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Pendientes: ${pesosPendientes.joinToString(", ")}",
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = displayGrade(notaProyectada),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = gradeColor(notaProyectada)
            )
        }
    }
}

@Composable
private fun DetalleComponenteItem(componente: ComponenteDetalle) {
    var expanded by remember { mutableStateOf(false) }
    val hasSub = componente.hasSubComponents && componente.subcomponentes.isNotEmpty()
    val nota = componente.displayNota
    val notaVal = gradeValue(nota)
    val color = gradeColor(nota)
    val peso = componente.displayPeso?.toString()

    AppCard(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        corner = 16.dp,
        contentPadding = PaddingValues(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = componente.displayNombre,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    val detalle = buildList {
                        peso?.let { add("Peso $it%") }
                        componente.displayPuntaje?.let { add("$it pts") }
                    }
                    if (detalle.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = detalle.joinToString(" · "),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "NOTA",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = displayGrade(nota),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            }

            if (notaVal != null) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (notaVal / 20f).toFloat().coerceIn(0f, 1f) },
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                )
            }

            if (hasSub) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sub-componentes (${componente.subcomponentes.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(if (expanded) 180f else 0f)
                    )
                }
                if (expanded) {
                    Spacer(modifier = Modifier.height(4.dp))
                    componente.subcomponentes.forEach { sub ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = sub.displayNombre,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = displayGrade(sub.displayNota),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = gradeColor(sub.displayNota)
                            )
                        }
                    }
                }
            }
        }
    }
}

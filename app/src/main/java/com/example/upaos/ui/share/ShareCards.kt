package com.example.upaos.ui.share

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.upaos.data.model.CourseGrade
import com.example.upaos.data.model.HorarioCurso
import com.example.upaos.data.model.PromedioPeriodoResponse
import com.example.upaos.data.upao.ScheduleCalculator
import com.example.upaos.ui.components.gradeColor
import com.example.upaos.ui.components.isPendiente
import com.example.upaos.ui.components.toTitleCase
import com.example.upaos.ui.grades.calcularAcumuladoCurso
import com.example.upaos.ui.grades.formatPromedio
import com.example.upaos.ui.grades.resolverCreditosCurso
import com.example.upaos.ui.theme.*
import kotlinx.coroutines.launch

// Paleta fija institucional para tarjetas compartidas (invariante ante el tema del usuario)
private val CardBackground = UpaoBackgroundDark // 0xFF0D1117
private val CardSurface = UpaoSurfaceDark // 0xFF161B26
private val CardSurfaceHigh = UpaoSurfaceHighDark // 0xFF1F2532
private val CardBorder = UpaoBlue.copy(alpha = 0.35f)
private val TextWhite = Color(0xFFF1F5F9)
private val TextMuted = Color(0xFF94A3B8)
private val AccentBlue = UpaoBlueLight

/**
 * Composable de la Tarjeta de Horario Semanal con diseño institucional para compartir.
 */
@Composable
fun TarjetaHorarioCard(
    cursos: List<HorarioCurso>,
    periodo: String,
    modifier: Modifier = Modifier
) {
    val bloquesPorDia = remember(cursos) {
        val dias = (0..5).associateWith { mutableListOf<Pair<HorarioCurso, com.example.upaos.data.model.HorarioBloque>>() }
        for (curso in cursos) {
            val fusionados = ScheduleCalculator.fusionarBloques(curso.bloques)
            for (b in fusionados) {
                val dia = b.dia ?: continue
                if (dia in 0..5) {
                    dias[dia]?.add(curso to b)
                }
            }
        }
        dias.mapValues { (_, lista) -> lista.sortedBy { it.second.horaInicio ?: "" } }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header institucional
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = UpaoBlue,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("U", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "UPAO S",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = TextWhite,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "HORARIO DE CLASES · PERIODO $periodo",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = AccentBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Días de clases
            val diasNombres = listOf("LUNES", "MARTES", "MIÉRCOLES", "JUEVES", "VIERNES", "SÁBADO")
            var hayClases = false

            for (diaIndex in 0..5) {
                val clasesDia = bloquesPorDia[diaIndex].orEmpty()
                if (clasesDia.isEmpty()) continue
                hayClases = true

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = diasNombres[diaIndex],
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = UpaoOrangeBright,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    clasesDia.forEach { (curso, bloque) ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = CardSurface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Horario badge
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = CardSurfaceHigh,
                                    modifier = Modifier.width(88.dp)
                                ) {
                                    Text(
                                        text = "${bloque.horaInicio ?: "--"} - ${bloque.horaFin ?: "--"}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextWhite,
                                        modifier = Modifier.padding(vertical = 3.dp, horizontal = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = curso.displayNombre,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = TextWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val aula = bloque.aula?.trim().orEmpty().ifBlank { "UPAO" }
                                    val tipo = bloque.displayTipo ?: bloque.tipo ?: ""
                                    Text(
                                        text = if (tipo.isNotBlank()) "$aula · $tipo" else aula,
                                        fontSize = 10.sp,
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!hayClases) {
                Text(
                    text = "No hay clases programadas para este periodo.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = CardBorder.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))

            // Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Generado con UPAO S",
                    fontSize = 10.sp,
                    color = TextMuted
                )
                Text(
                    text = "upaos.app",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentBlue
                )
            }
        }
    }
}

/**
 * Composable de la Tarjeta de Resumen de Calificaciones y PPS.
 * Privacidad estricta: por defecto no muestra el nombre del estudiante; NUNCA incluye código ni contraseñas.
 */
@Composable
fun TarjetaNotasCard(
    cursos: List<CourseGrade>,
    periodo: String,
    carrera: String,
    promedioGeneral: Any?,
    promedioPeriodoRes: PromedioPeriodoResponse?,
    notasProyectadas: Map<String, Any?>,
    nombreEstudiante: String?,
    ocultarNombre: Boolean,
    modifier: Modifier = Modifier
) {
    val ppsOficial = promedioPeriodoRes?.ppsOficial?.toDouble()
    val ppsCalculado = promedioPeriodoRes?.ppsCalculado?.toDouble()

    val ponderadoLocal: Double? = remember(cursos, notasProyectadas, promedioPeriodoRes) {
        var puntos = 0.0
        var creditos = 0
        cursos.forEach { c ->
            val crn = c.crn ?: c.courseReferenceNumber ?: ""
            val acumulado = calcularAcumuladoCurso(c, notasProyectadas[crn])
            val cr = resolverCreditosCurso(c, promedioPeriodoRes)
            if (acumulado != null && !acumulado.isNaN() && !acumulado.isInfinite()) {
                puntos += (acumulado * cr)
                creditos += cr
            }
        }
        if (creditos > 0) (puntos / creditos).takeUnless { it.isNaN() || it.isInfinite() } else null
    }

    val pFinal: Double? = when {
        ponderadoLocal != null -> ponderadoLocal
        ppsCalculado != null -> ppsCalculado
        ppsOficial != null -> ppsOficial
        promedioGeneral != null && !isPendiente(promedioGeneral) && promedioGeneral.toString().trim().toDoubleOrNull() != null -> {
            promedioGeneral.toString().trim().toDouble().takeUnless { it.isNaN() || it.isInfinite() }
        }
        else -> null
    }

    val ppsTexto = formatPromedio(pFinal)
    val gaugeColor = gradeColor(pFinal)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header institucional
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = UpaoBlue,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("U", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "UPAO S",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = TextWhite,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "RESUMEN DE NOTAS · PERIODO $periodo",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = AccentBlue
                    )
                }

                // Identidad del alumno (según toggle de privacidad; NUNCA código)
                Column(horizontalAlignment = Alignment.End) {
                    val displayName = if (ocultarNombre || nombreEstudiante.isNullOrBlank()) {
                        "Estudiante UPAO"
                    } else {
                        toTitleCase(nombreEstudiante)
                    }
                    Text(
                        text = displayName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = TextWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = carrera.ifBlank { "Pregrado" },
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Cuadro del Ponderado / PPS
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = CardSurface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "PROMEDIO PONDERADO (PPS)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                        Text(
                            text = if (ponderadoLocal != null) "Acumulado actual de evaluaciones" else "Semestral",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = gaugeColor.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, gaugeColor)
                    ) {
                        Text(
                            text = ppsTexto,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = gaugeColor,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Listado de cursos
            Text(
                text = "CURSOS MATRICULADOS (${cursos.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = UpaoOrangeBright,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            cursos.forEach { curso ->
                val crn = curso.crn ?: curso.courseReferenceNumber ?: ""
                val notaAcumulada = calcularAcumuladoCurso(curso, notasProyectadas[crn])
                val notaActual = curso.displayNotaActual?.toString()?.trim()
                val notaStr = when {
                    !notaActual.isNullOrBlank() && !isPendiente(notaActual) -> notaActual
                    notaAcumulada != null -> String.format(java.util.Locale.US, "%.1f", notaAcumulada)
                    else -> "--"
                }
                val notaDouble = notaStr.toDoubleOrNull()
                val colorNota = gradeColor(notaDouble)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = CardSurface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = curso.displayNombre,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val cred = resolverCreditosCurso(curso, promedioPeriodoRes)
                            Text(
                                text = "$cred créditos",
                                fontSize = 9.sp,
                                color = TextMuted
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colorNota.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = notaStr,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = colorNota,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = CardBorder.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))

            // Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Generado con UPAO S",
                    fontSize = 10.sp,
                    color = TextMuted
                )
                Text(
                    text = "upaos.app",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentBlue
                )
            }
        }
    }
}

/**
 * Diálogo modal para previsualizar y compartir la tarjeta de horario como imagen (PNG).
 */
@Composable
fun CompartirHorarioDialog(
    cursos: List<HorarioCurso>,
    periodo: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()
    var compartiendo by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Compartir Horario",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Área de previsualización que se captura en el GraphicsLayer
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .drawWithContent {
                            graphicsLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            drawLayer(graphicsLayer)
                        }
                ) {
                    TarjetaHorarioCard(
                        cursos = cursos,
                        periodo = periodo
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (compartiendo) return@Button
                        compartiendo = true
                        scope.launch {
                            ShareBitmapHelper.capturarYCompartir(
                                context = context,
                                graphicsLayer = graphicsLayer,
                                nombreArchivo = "horario_upao_$periodo",
                                titulo = "Horario UPAO - Periodo $periodo"
                            )
                            compartiendo = false
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !compartiendo
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (compartiendo) "Generando imagen..." else "Compartir en WhatsApp")
                }
            }
        }
    }
}

/**
 * Diálogo modal para previsualizar y compartir la tarjeta de notas con toggle de privacidad de nombre.
 */
@Composable
fun CompartirNotasDialog(
    cursos: List<CourseGrade>,
    periodo: String,
    carrera: String,
    promedioGeneral: Any?,
    promedioPeriodoRes: PromedioPeriodoResponse?,
    notasProyectadas: Map<String, Any?>,
    nombreEstudiante: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()
    var ocultarNombre by remember { mutableStateOf(true) } // Privacidad por defecto: ocultar nombre
    var compartiendo by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Compartir Resumen de Notas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }

                // Control de privacidad (toggle de nombre)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (ocultarNombre) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Ocultar mi nombre",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Switch(
                            checked = ocultarNombre,
                            onCheckedChange = { ocultarNombre = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Área de previsualización que se captura en el GraphicsLayer
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .drawWithContent {
                            graphicsLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            drawLayer(graphicsLayer)
                        }
                ) {
                    TarjetaNotasCard(
                        cursos = cursos,
                        periodo = periodo,
                        carrera = carrera,
                        promedioGeneral = promedioGeneral,
                        promedioPeriodoRes = promedioPeriodoRes,
                        notasProyectadas = notasProyectadas,
                        nombreEstudiante = nombreEstudiante,
                        ocultarNombre = ocultarNombre
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (compartiendo) return@Button
                        compartiendo = true
                        scope.launch {
                            ShareBitmapHelper.capturarYCompartir(
                                context = context,
                                graphicsLayer = graphicsLayer,
                                nombreArchivo = "notas_upao_$periodo",
                                titulo = "Resumen de Notas UPAO - $periodo"
                            )
                            compartiendo = false
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !compartiendo
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (compartiendo) "Generando imagen..." else "Compartir en WhatsApp")
                }
            }
        }
    }
}

package com.example.upaos.ui.calculadora

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.upaos.ui.theme.UpaoBlue
import com.example.upaos.ui.theme.UpaoGreen
import com.example.upaos.ui.theme.UpaoRed
import kotlin.math.roundToInt

// Nota mínima aprobatoria oficial UPAO
private const val NOTA_MINIMA = 10.5

private data class ComponentePeso(val id: String, val nombre: String, val peso: Int)

private val componentes = listOf(
    ComponentePeso("ep1", "EP1", 20),
    ComponentePeso("parcial", "Parcial", 30),
    ComponentePeso("ep2", "EP2", 20),
    ComponentePeso("final", "Final", 30)
)

private fun formatNota(v: Double): String {
    val r = (v * 100).roundToInt() / 100.0
    return if (r % 1.0 == 0.0) r.toInt().toString() else String.format(java.util.Locale.US, "%.2f", r)
}

private enum class EstadoCalculo { NEUTRO, PARCIAL, APROBADO, DESAPROBADO, ALCANZABLE, IMPOSIBLE }

private data class ResultadoCalculo(
    val titulo: String,
    val valorDestacado: String,
    val mensaje: String,
    val estado: EstadoCalculo
)

private fun calcular(notas: List<Double?>): ResultadoCalculo {
    val llenas = notas.count { it != null }
    val sumaPonderada = componentes.indices.sumOf { i ->
        notas[i]?.let { it * componentes[i].peso / 100.0 } ?: 0.0
    }

    if (llenas == 4) {
        val promedio = sumaPonderada
        val redondeo = promedio.roundToInt()
        return if (promedio >= NOTA_MINIMA) {
            ResultadoCalculo(
                titulo = "Promedio Final",
                valorDestacado = formatNota(promedio),
                mensaje = "¡Felicidades! Curso aprobado · Nota en acta oficial UPAO: $redondeo",
                estado = EstadoCalculo.APROBADO
            )
        } else {
            ResultadoCalculo(
                titulo = "Promedio Final",
                valorDestacado = formatNota(promedio),
                mensaje = "Desaprobado (por debajo de ${formatNota(NOTA_MINIMA)}) · Nota en acta: $redondeo",
                estado = EstadoCalculo.DESAPROBADO
            )
        }
    }

    if (llenas == 3) {
        val missingIndex = componentes.indices.first { notas[it] == null }
        val missingComp = componentes[missingIndex]
        val pesoFaltante = missingComp.peso / 100.0
        val necesaria = (NOTA_MINIMA - sumaPonderada) / pesoFaltante

        return when {
            necesaria <= 0.0 -> ResultadoCalculo(
                titulo = "Curso Aprobado",
                valorDestacado = formatNota(sumaPonderada),
                mensaje = "Ya acumulaste ${formatNota(sumaPonderada)} pts. Tienes el curso aprobado sin importar tu nota en ${missingComp.nombre}.",
                estado = EstadoCalculo.APROBADO
            )
            necesaria > 20.0 -> ResultadoCalculo(
                titulo = "Fuera de alcance",
                valorDestacado = formatNota(necesaria),
                mensaje = "Se requiere más de 20 pts en ${missingComp.nombre} para alcanzar 10.5.",
                estado = EstadoCalculo.IMPOSIBLE
            )
            else -> ResultadoCalculo(
                titulo = "Necesitas en ${missingComp.nombre}",
                valorDestacado = formatNota(necesaria),
                mensaje = "Sacando ${formatNota(necesaria)} o más en ${missingComp.nombre} apruebas el curso con ${formatNota(NOTA_MINIMA)}.",
                estado = EstadoCalculo.ALCANZABLE
            )
        }
    }

    if (llenas in 1..2) {
        val sumaPesos = componentes.indices.sumOf { if (notas[it] != null) componentes[it].peso else 0 }
        val parcial = sumaPonderada / (sumaPesos / 100.0)
        val faltanParaAprobar = (NOTA_MINIMA - sumaPonderada).coerceAtLeast(0.0)
        return ResultadoCalculo(
            titulo = "Puntos asegurados",
            valorDestacado = formatNota(sumaPonderada),
            mensaje = if (faltanParaAprobar > 0) {
                "Llevas ${formatNota(sumaPonderada)} pts asegurados (promedio parcial ${formatNota(parcial)}). Faltan ${formatNota(faltanParaAprobar)} pts para aprobar."
            } else {
                "¡Ya tienes ${formatNota(sumaPonderada)} pts asegurados! Ya alcanzaste la nota aprobatoria."
            },
            estado = if (faltanParaAprobar <= 0) EstadoCalculo.APROBADO else EstadoCalculo.PARCIAL
        )
    }

    return ResultadoCalculo(
        titulo = "Calculadora UPAO",
        valorDestacado = "0.0",
        mensaje = "Ingresa tus notas para ver tu promedio acumulado y saber cuánto necesitas para aprobar.",
        estado = EstadoCalculo.NEUTRO
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculadoraScreen(onBack: () -> Unit) {
    var valores by remember { mutableStateOf(listOf("", "", "", "")) }

    val notas = componentes.indices.map { i ->
        valores[i].toDoubleOrNull()?.takeIf { it in 0.0..20.0 }
    }
    val resultado = calcular(notas)
    val llenas = notas.count { it != null }
    val sumaPonderada = componentes.indices.sumOf { i ->
        notas[i]?.let { it * componentes[i].peso / 100.0 } ?: 0.0
    }
    val hayNotasIngresadas = valores.any { it.isNotBlank() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Calculadora de Notas",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    AnimatedVisibility(visible = hayNotasIngresadas, enter = fadeIn(), exit = fadeOut()) {
                        IconButton(onClick = { valores = listOf("", "", "", "") }) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Limpiar notas",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Panel de Resultado Limpio (Sin cajas raras ni íconos encerrados)
            ResultadoBanner(
                resultado = resultado,
                puntosAcumulados = sumaPonderada,
                llenas = llenas
            )

            // Subtítulo con pesos oficiales
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Evaluaciones",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "EP1 20% · Parcial 30% · EP2 20% · Final 30%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Grid 2x2 elegante
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NotaCard(
                    componente = componentes[0],
                    value = valores[0],
                    onValueChange = { nuevo ->
                        valores = valores.toMutableList().also { it[0] = nuevo }
                    },
                    modifier = Modifier.weight(1f)
                )
                NotaCard(
                    componente = componentes[1],
                    value = valores[1],
                    onValueChange = { nuevo ->
                        valores = valores.toMutableList().also { it[1] = nuevo }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NotaCard(
                    componente = componentes[2],
                    value = valores[2],
                    onValueChange = { nuevo ->
                        valores = valores.toMutableList().also { it[2] = nuevo }
                    },
                    modifier = Modifier.weight(1f)
                )
                NotaCard(
                    componente = componentes[3],
                    value = valores[3],
                    onValueChange = { nuevo ->
                        valores = valores.toMutableList().also { it[3] = nuevo }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Pie de página sutil
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "La nota mínima aprobatoria UPAO es 10.5 (se redondea a 11 en el acta final).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ResultadoBanner(
    resultado: ResultadoCalculo,
    puntosAcumulados: Double,
    llenas: Int
) {
    val color = when (resultado.estado) {
        EstadoCalculo.APROBADO -> UpaoGreen
        EstadoCalculo.DESAPROBADO, EstadoCalculo.IMPOSIBLE -> UpaoRed
        EstadoCalculo.ALCANZABLE -> UpaoBlue
        EstadoCalculo.PARCIAL -> UpaoBlue
        EstadoCalculo.NEUTRO -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            // Fila superior: Título + Badge de estado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = resultado.titulo.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (resultado.estado != EstadoCalculo.NEUTRO) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = color.copy(alpha = 0.12f),
                        contentColor = color
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Text(
                                text = when (resultado.estado) {
                                    EstadoCalculo.APROBADO -> "Aprobado"
                                    EstadoCalculo.DESAPROBADO -> "Desaprobado"
                                    EstadoCalculo.ALCANZABLE -> "Meta alcanzable"
                                    EstadoCalculo.IMPOSIBLE -> "Crítico"
                                    EstadoCalculo.PARCIAL -> "$llenas de 4 notas"
                                    else -> ""
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Número / Valor destacado
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = resultado.valorDestacado,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = if (resultado.estado == EstadoCalculo.NEUTRO) MaterialTheme.colorScheme.outline else color
                )
                if (resultado.estado != EstadoCalculo.NEUTRO) {
                    Text(
                        text = if (resultado.estado == EstadoCalculo.ALCANZABLE) "requerida" else "/ 20 pts",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }

            // Barra de progreso delgada (solo si hay notas)
            if (llenas > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                val fraction = (puntosAcumulados / 20.0).toFloat().coerceIn(0f, 1f)
                val animatedFraction by animateFloatAsState(
                    targetValue = fraction,
                    animationSpec = tween(400),
                    label = "barProgreso"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedFraction)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mensaje descriptivo
            Text(
                text = resultado.mensaje,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun NotaCard(
    componente: ComponentePeso,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val d = value.toDoubleOrNull()
    val tieneNota = d != null
    val esInvalido = value.isNotEmpty() && (d == null || d < 0.0 || d > 20.0)

    // Aporte real en puntos
    val aporte = if (tieneNota && !esInvalido) (d!! * componente.peso / 100.0) else null

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Encabezado con nombre y peso
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = componente.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "${componente.peso}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Campo de entrada estilizado y centrado
            OutlinedTextField(
                value = value,
                onValueChange = { input ->
                    if (input.length <= 5) {
                        onValueChange(input.filter { it.isDigit() || it == '.' })
                    }
                },
                placeholder = {
                    Text(
                        text = "0 - 20",
                        style = TextStyle(
                            textAlign = TextAlign.Center,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                trailingIcon = {
                    if (value.isNotEmpty()) {
                        IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Borrar",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                },
                isError = esInvalido,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = if (tieneNota && !esInvalido) {
                        if (d!! >= NOTA_MINIMA) UpaoGreen else UpaoRed
                    } else MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            )

            // Línea inferior: muestra cuánto aporta al promedio o error
            Spacer(modifier = Modifier.height(6.dp))
            if (esInvalido) {
                Text(
                    text = "Debe ser de 0 a 20",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            } else if (aporte != null) {
                Text(
                    text = "+${formatNota(aporte)} pts al promedio",
                    color = UpaoGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    text = "Sin ingresar",
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

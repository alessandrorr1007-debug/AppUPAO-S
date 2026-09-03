package com.example.upaos.ui.calculadora

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.upaos.ui.theme.UpaoGreen
import com.example.upaos.ui.theme.UpaoRed
import kotlin.math.roundToInt

// Nota mínima aprobatoria UPAO.
private const val NOTA_MINIMA = 10.5

private data class ComponentePeso(val nombre: String, val peso: Int)

private val componentes = listOf(
    ComponentePeso("EP1", 20),
    ComponentePeso("Parcial", 30),
    ComponentePeso("EP2", 20),
    ComponentePeso("Final", 30)
)

private fun formatNota(v: Double): String {
    val r = (v * 100).roundToInt() / 100.0
    return if (r % 1.0 == 0.0) r.toInt().toString() else r.toString()
}

private enum class EstadoResultado { NEUTRO, PARCIAL, BIEN, MAL }

private data class Resultado(val label: String, val texto: String, val estado: EstadoResultado)

private fun calcularResultado(notas: List<Double?>): Resultado {
    val llenas = notas.count { it != null }
    val sumaPonderada = componentes.indices.sumOf { i ->
        notas[i]?.let { it * componentes[i].peso / 100.0 } ?: 0.0
    }

    if (llenas == 4) {
        val promedio = sumaPonderada
        return if (promedio >= NOTA_MINIMA) {
            Resultado(
                label = "Promedio final",
                texto = "${formatNota(promedio)} / 20 — aprobado",
                estado = EstadoResultado.BIEN
            )
        } else {
            Resultado(
                label = "Promedio final",
                texto = "${formatNota(promedio)} / 20 — por debajo de ${formatNota(NOTA_MINIMA)}",
                estado = EstadoResultado.MAL
            )
        }
    }

    if (llenas == 3) {
        val missingIndex = componentes.indices.first { notas[it] == null }
        val pesoFaltante = componentes[missingIndex].peso / 100.0
        val necesaria = (NOTA_MINIMA - sumaPonderada) / pesoFaltante
        return when {
            necesaria <= 0 -> Resultado(
                label = "Nota necesaria",
                texto = "Ya tienes aprobado el curso, no necesitas nada en ${componentes[missingIndex].nombre}",
                estado = EstadoResultado.BIEN
            )
            necesaria > 20 -> Resultado(
                label = "Nota necesaria",
                texto = "No es posible aprobar solo con ${componentes[missingIndex].nombre} (máximo 20)",
                estado = EstadoResultado.MAL
            )
            else -> Resultado(
                label = "Nota necesaria",
                texto = "Necesitas ${formatNota(necesaria)} en ${componentes[missingIndex].nombre} para llegar a ${formatNota(NOTA_MINIMA)}",
                estado = EstadoResultado.BIEN
            )
        }
    }

    if (llenas in 1..2) {
        val sumaPesos = componentes.indices.sumOf { i -> if (notas[i] != null) componentes[i].peso else 0 }
        val parcial = sumaPonderada / (sumaPesos / 100.0)
        return Resultado(
            label = "Promedio parcial",
            texto = "${formatNota(parcial)} / 20 — faltan ${4 - llenas} nota(s)",
            estado = EstadoResultado.PARCIAL
        )
    }

    return Resultado(
        label = "Calculadora",
        texto = "Ingresa tus notas para calcular",
        estado = EstadoResultado.NEUTRO
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculadoraScreen(onBack: () -> Unit) {
    var valores by remember { mutableStateOf(listOf("", "", "", "")) }

    val notas = componentes.indices.map { i ->
        valores[i].toDoubleOrNull()?.takeIf { it in 0.0..20.0 }
    }
    val resultado = calcularResultado(notas)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Calculadora de Notas", fontWeight = FontWeight.Bold) },
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
                .padding(16.dp)
        ) {
            ResultadoCard(resultado)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Tus notas",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Pesos oficiales: EP1 20% · Parcial 30% · EP2 20% · Final 30%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NotaCard(
                    componente = componentes[0],
                    value = valores[0],
                    onValueChange = { nuevo -> valores = valores.toMutableList().also { it[0] = nuevo } },
                    modifier = Modifier.weight(1f)
                )
                NotaCard(
                    componente = componentes[1],
                    value = valores[1],
                    onValueChange = { nuevo -> valores = valores.toMutableList().also { it[1] = nuevo } },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NotaCard(
                    componente = componentes[2],
                    value = valores[2],
                    onValueChange = { nuevo -> valores = valores.toMutableList().also { it[2] = nuevo } },
                    modifier = Modifier.weight(1f)
                )
                NotaCard(
                    componente = componentes[3],
                    value = valores[3],
                    onValueChange = { nuevo -> valores = valores.toMutableList().also { it[3] = nuevo } },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "La nota mínima aprobatoria es ${formatNota(NOTA_MINIMA)}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun ResultadoCard(resultado: Resultado) {
    val color = when (resultado.estado) {
        EstadoResultado.BIEN -> UpaoGreen
        EstadoResultado.MAL -> UpaoRed
        else -> MaterialTheme.colorScheme.primary
    }
    val icon = when (resultado.estado) {
        EstadoResultado.BIEN -> Icons.Filled.CheckCircle
        EstadoResultado.MAL -> Icons.Filled.Warning
        else -> Icons.Filled.Info
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.14f),
            contentColor = color
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(40.dp)
            )
            Column {
                Text(
                    text = resultado.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = color.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = resultado.texto,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
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
    val invalid = value.isNotEmpty() && (d == null || d < 0.0 || d > 20.0)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = componente.nombre,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${componente.peso}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = value,
                onValueChange = { nuevo ->
                    if (nuevo.length <= 5) onValueChange(nuevo.filter { it.isDigit() || it == '.' })
                },
                placeholder = { Text("0-20", textAlign = TextAlign.Center) },
                isError = invalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
            )
            if (invalid) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "0-20",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
            }
        }
    }
}

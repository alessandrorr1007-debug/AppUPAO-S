package com.example.upaos.ui.settings

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.upaos.ui.components.AppCard

@Composable
fun HelpSupportContent() {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(-1) }
    var reportOpen by rememberSaveable { mutableStateOf(false) }
    var report by rememberSaveable { mutableStateOf("") }
    val questions = listOf(
        "¿Cómo actualizo mis datos?" to "Desliza hacia abajo en Cursos, Horario o Asistencia. Necesitas conexión para consultar la información más reciente. Si la consulta falla, la app conserva lo último guardado.",
        "¿Por qué no recibo avisos?" to "Revisa que los avisos estén activados en Ajustes > Activar notificaciones y que Android permita las notificaciones de UPAO S. La revisión en segundo plano necesita una sesión y contraseña guardadas, conexión y permiso para trabajar con la pantalla apagada.",
        "¿La calculadora cambia mis notas?" to "No. Las notas que ingresas o completas son una simulación dentro de la calculadora. Tus calificaciones en la universidad se conservan.",
        "¿Qué significa una asistencia pendiente?" to "Indica que todavía no hay un registro disponible. Toca Ver desglose en el curso para consultar sus componentes.",
        "¿Cómo elimino mis datos del celular?" to "En Ajustes > Privacidad y datos puedes borrar la caché o cerrar sesión y borrar los datos locales de una cuenta. Esto no elimina tu cuenta universitaria."
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Preguntas frecuentes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        questions.forEachIndexed { index, (question, answer) ->
            AppCard(modifier = Modifier.fillMaxWidth().animateContentSize(), corner = 16.dp,
                contentPadding = PaddingValues(12.dp), onClick = { expanded = if (expanded == index) -1 else index }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(question, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Icon(if (expanded == index) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded == index) "Contraer respuesta" else "Ver respuesta", modifier = Modifier.size(20.dp))
                }
                AnimatedVisibility(expanded == index) {
                    Text(answer, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = { reportOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Reportar un problema") }
    }
    if (reportOpen) AlertDialog(
        onDismissRequest = { reportOpen = false },
        title = { Text("Reportar un problema") },
        text = {
            OutlinedTextField(value = report, onValueChange = { if (it.length <= 2000) report = it },
                label = { Text("¿Qué ocurrió?") }, placeholder = { Text("En qué pantalla estabas y qué hiciste antes del problema") },
                minLines = 4, maxLines = 7, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(enabled = report.isNotBlank(), onClick = {
                val version = com.example.upaos.util.AppUpdater.getCurrentVersionName(context)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Problema en UPAO S")
                    putExtra(Intent.EXTRA_TEXT, "UPAO S $version · Android ${Build.VERSION.RELEASE}\n\n${report.trim()}")
                }
                try {
                    context.startActivity(Intent.createChooser(send, "Compartir reporte"))
                    reportOpen = false
                } catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(context, "No hay una aplicación disponible para compartir el reporte", Toast.LENGTH_LONG).show()
                }
            }) { Text("Compartir reporte") }
        },
        dismissButton = { TextButton(onClick = { reportOpen = false }) { Text("Cancelar") } }
    )
}

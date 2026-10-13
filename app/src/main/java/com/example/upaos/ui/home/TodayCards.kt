package com.example.upaos.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.upaos.data.agenda.AgendaItem
import com.example.upaos.data.model.NotificacionItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun campusFormat(time: Long, pattern: String) = SimpleDateFormat(pattern, Locale.forLanguageTag("es-PE"))
    .apply { timeZone = campusTimeZone }.format(Date(time))

@Composable
fun TodayHero(now: Long, classes: Int?, tasks: Int?, news: Int?) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF123B91), Color(0xFF245DCA))))
        .padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(campusFormat(now, "EEEE, d 'de' MMMM").replaceFirstChar { it.titlecase() },
                style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .85f))
            Surface(color = Color.White.copy(alpha = .15f), shape = RoundedCornerShape(50)) {
                Text("HOY", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Tu día, a la vista", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Clases, pendientes y novedades de hoy", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .85f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroMetric(classes, "Clases", Icons.Filled.Schedule, Modifier.weight(1f))
            HeroMetric(tasks, "Pendientes", Icons.AutoMirrored.Filled.Assignment, Modifier.weight(1f))
            HeroMetric(news, "Novedades", Icons.Filled.Notifications, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroMetric(count: Int?, label: String, icon: ImageVector, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = .12f)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = .85f), modifier = Modifier.size(14.dp))
            Text(count?.toString() ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .85f))
    }
}

@Composable
fun TodaySectionTitle(title: String, count: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
            Text(count.toString(), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
fun SourceStamp(updated: Long?, current: Boolean) {
    Text(if (updated == null) "Sin información guardada" else
        "${if (current) "Actualizado" else "Guardado · pendiente de actualizar"} · ${campusFormat(updated, "dd/MM HH:mm")}",
        modifier = Modifier.padding(top = 5.dp, bottom = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun TodayEmptyState(title: String, description: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f), modifier = Modifier.size(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun TodaySchedule(classes: List<NextClass>, available: Boolean, now: Long, updated: Long?, current: Boolean, onSchedule: () -> Unit) {
    val next = classes.firstOrNull { it.end > now }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Filled.Schedule, null, Modifier.padding(12.dp).size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text("Tus clases de hoy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(when { !available -> "Horario por actualizar"; classes.isEmpty() -> "Hoy no tienes clases programadas"; next == null -> "Clases de hoy finalizadas"; next.start <= now -> "Estás en horario de clase"; else -> "Tu siguiente clase de hoy" },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (next != null) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (next.start <= now) "EN CURSO" else "A LAS ${campusFormat(next.start, "HH:mm")}",
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(next.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(if (next.room == "Virtual") Icons.Filled.Videocam else Icons.Filled.LocationOn, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(next.room, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("${campusFormat(next.start, "HH:mm")} – ${campusFormat(next.end, "HH:mm")}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            classes.filter { it != next }.forEach { lesson ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(campusFormat(lesson.start, "HH:mm"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(lesson.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("${lesson.room} · ${if (lesson.end <= now) "Finalizada" else "Hasta ${campusFormat(lesson.end, "HH:mm")}"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { SourceStamp(updated, current) }
                TextButton(onClick = onSchedule) { Text("Ver horario") }
            }
        }
    }
}

@Composable
fun TodayAssignments(items: List<AgendaItem>, now: Long, updated: Long?, current: Boolean, onCanvas: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.AutoMirrored.Filled.Assignment, null, Modifier.padding(12.dp).size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text("Por entregar hoy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Tus tareas y entregas del día", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(items.size.toString(), Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            if (items.isEmpty()) {
                TodayEmptyState(if (updated == null) "Conecta Canvas para ver tus entregas" else "Sin entregas pendientes para hoy",
                    if (updated == null) "Conecta tu cuenta desde la pestaña Canvas." else if (current) "No tienes entregas pendientes que venzan hoy." else "No hay entregas de hoy en los datos guardados.")
            } else {
                items.forEach { assignment -> androidx.compose.runtime.key(assignment.key) { TodayAssignment(assignment, now, true) } }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { SourceStamp(updated, current) }
                TextButton(onClick = onCanvas) { Text("Ver Canvas") }
            }
        }
    }
}

@Composable
fun TodayAssignment(item: AgendaItem, now: Long, todayOnly: Boolean) {
    val context = LocalContext.current
    val deliveryUrl = item.url?.takeIf { Uri.parse(it).scheme == "https" && Uri.parse(it).host == "upao.instructure.com" }
    fun openDelivery() { deliveryUrl?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }
    val late = !item.submitted && !item.graded && (item.dueMillis ?: Long.MAX_VALUE) < now
    val accent = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Card(Modifier.fillMaxWidth().clickable(enabled = deliveryUrl != null, onClick = { openDelivery() }), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(Modifier.weight(1f).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.course, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.Filled.Schedule, null, Modifier.size(17.dp), tint = accent)
                    Text(item.dueMillis?.let { if (todayOnly) "Hoy · ${campusFormat(it, "HH:mm")}" else "Vence ${campusFormat(it, "dd/MM HH:mm")}" } ?: "Sin fecha de entrega", style = MaterialTheme.typography.bodySmall, color = accent)
                    Spacer(Modifier.weight(1f))
                    Surface(color = accent.copy(alpha = .12f), shape = RoundedCornerShape(8.dp)) {
                        Text(if (todayOnly && late) "Vencida hoy" else item.status(now), Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = accent)
                    }
                }
                if (deliveryUrl != null) {
                    TextButton(onClick = { openDelivery() }, contentPadding = PaddingValues(0.dp)) {
                        Text("Ver entrega")
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.padding(start = 6.dp).size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun TodayNotice(item: NotificacionItem, onNotifications: () -> Unit) {
    Card(onClick = onNotifications, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = if (!item.leida) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .35f) else MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Notifications, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (!item.leida) "NUEVA NOTA" else "ACTUALIZACIÓN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    notificationMillis(item.fechaCreacion)?.let { Text(campusFormat(it, "HH:mm"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Text(item.mensaje, style = MaterialTheme.typography.bodyMedium)
                item.curso?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}


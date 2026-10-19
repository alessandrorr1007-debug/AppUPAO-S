package com.example.upaos.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
fun TodayHero(
    now: Long,
    classes: Int?,
    tasks: Int?,
    news: Int?,
    onSchedule: () -> Unit = {},
    onCanvas: () -> Unit = {},
    onNotifications: () -> Unit = {}
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF123B91), Color(0xFF245DCA))))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                campusFormat(now, "EEEE, d 'de' MMMM").replaceFirstChar { it.titlecase() },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = .85f)
            )
            Surface(color = Color.White.copy(alpha = .15f), shape = RoundedCornerShape(50)) {
                Text("HOY", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Tu día, a la vista", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Clases, pendientes y novedades de hoy", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .85f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroMetric(classes, "Clases", Icons.Filled.Schedule, Modifier.weight(1f), onClick = onSchedule)
            HeroMetric(tasks, "Pendientes", Icons.AutoMirrored.Filled.Assignment, Modifier.weight(1f), onClick = onCanvas)
            HeroMetric(news, "Novedades", Icons.Filled.Notifications, Modifier.weight(1f), onClick = onNotifications)
        }
    }
}

@Composable
private fun HeroMetric(
    count: Int?,
    label: String,
    icon: ImageVector,
    modifier: Modifier,
    onClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = .12f))
            .clickable(onClick = onClick)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
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

data class TodayCardTheme(
    val cardBg: Color,
    val cardBorder: Color,
    val cardIconBg: Color,
    val cardAccent: Color,
    val cardTextColor: Color,
    val cardSubtextColor: Color,
    val innerCardBg: Color,
    val innerCardBorder: Color
)

@Composable
fun rememberTodayCardTheme(): TodayCardTheme {
    val isDark = isSystemInDarkTheme()
    return if (isDark) {
        TodayCardTheme(
            cardBg = MaterialTheme.colorScheme.surfaceContainerHigh,
            cardBorder = MaterialTheme.colorScheme.outlineVariant,
            cardIconBg = MaterialTheme.colorScheme.surfaceContainerHighest,
            cardAccent = MaterialTheme.colorScheme.primary,
            cardTextColor = MaterialTheme.colorScheme.onSurface,
            cardSubtextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            innerCardBg = MaterialTheme.colorScheme.surfaceContainerHighest,
            innerCardBorder = MaterialTheme.colorScheme.outlineVariant
        )
    } else {
        TodayCardTheme(
            cardBg = Color(0xFFF0F5FF),
            cardBorder = Color(0xFFBED5FD),
            cardIconBg = Color(0xFFDBEAFE),
            cardAccent = Color(0xFF1D4ED8),
            cardTextColor = Color(0xFF0F2856),
            cardSubtextColor = Color(0xFF2563EB),
            innerCardBg = Color.White,
            innerCardBorder = Color(0xFFDBEAFE)
        )
    }
}

@Composable
fun SourceStamp(updated: Long?, current: Boolean, color: Color? = null) {
    if (!current && updated != null) {
        Text(
            text = "Última actualización · ${campusFormat(updated, "dd/MM HH:mm")}",
            modifier = Modifier.padding(top = 5.dp, bottom = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color ?: MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TodayEmptyState(title: String, description: String) {
    val theme = rememberTodayCardTheme()

    Surface(
        color = theme.innerCardBg,
        border = BorderStroke(1.dp, theme.innerCardBorder),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = theme.cardAccent, modifier = Modifier.size(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = theme.cardTextColor)
                Text(description, style = MaterialTheme.typography.bodySmall, color = theme.cardSubtextColor)
            }
        }
    }
}

@Composable
fun TodaySchedule(classes: List<NextClass>, available: Boolean, now: Long, updated: Long?, current: Boolean, onSchedule: () -> Unit) {
    if (classes.isEmpty()) return
    val next = classes.firstOrNull { it.end > now }
    val theme = rememberTodayCardTheme()

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = theme.cardBg, contentColor = theme.cardTextColor),
        border = BorderStroke(1.dp, theme.cardBorder)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = theme.cardIconBg) {
                    Icon(Icons.Filled.Schedule, null, Modifier.padding(12.dp).size(22.dp), tint = theme.cardAccent)
                }
                Column(Modifier.weight(1f)) {
                    Text("Tus clases de hoy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = theme.cardTextColor)
                    Text(when { !available -> "Horario por actualizar"; classes.isEmpty() -> "Hoy no tienes clases programadas"; next == null -> "Clases de hoy finalizadas"; next.start <= now -> "Estás en horario de clase"; else -> "Tu siguiente clase de hoy" },
                        style = MaterialTheme.typography.bodySmall, color = theme.cardSubtextColor)
                }
            }
            if (next != null) {
                Surface(
                    color = theme.innerCardBg,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, theme.innerCardBorder)
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (next.start <= now) "EN CURSO" else "A LAS ${campusFormat(next.start, "HH:mm")}",
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = theme.cardAccent)
                        Text(next.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = theme.cardTextColor)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(if (next.room == "Virtual") Icons.Filled.Videocam else Icons.Filled.LocationOn, null, Modifier.size(17.dp), tint = theme.cardSubtextColor)
                            Text(next.room, style = MaterialTheme.typography.bodySmall, color = theme.cardSubtextColor)
                        }
                        Text("${campusFormat(next.start, "HH:mm")} – ${campusFormat(next.end, "HH:mm")}", style = MaterialTheme.typography.bodyMedium, color = theme.cardSubtextColor)
                    }
                }
            }
            classes.filter { it != next }.forEach { lesson ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(campusFormat(lesson.start, "HH:mm"), style = MaterialTheme.typography.labelLarge, color = theme.cardAccent, fontWeight = FontWeight.Bold)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(lesson.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = theme.cardTextColor)
                        Text("${lesson.room} · ${if (lesson.end <= now) "Finalizada" else "Hasta ${campusFormat(lesson.end, "HH:mm")}"}", style = MaterialTheme.typography.bodySmall, color = theme.cardSubtextColor)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (!current && updated != null) {
                    Column(Modifier.weight(1f)) { SourceStamp(updated, current, color = theme.cardSubtextColor) }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = onSchedule) { Text("Ver horario", color = theme.cardAccent, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun TodayAssignments(items: List<AgendaItem>, now: Long, updated: Long?, current: Boolean, onCanvas: () -> Unit) {
    if (items.isEmpty()) return
    val theme = rememberTodayCardTheme()

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = theme.cardBg, contentColor = theme.cardTextColor),
        border = BorderStroke(1.dp, theme.cardBorder)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = theme.cardIconBg) {
                    Icon(Icons.AutoMirrored.Filled.Assignment, null, Modifier.padding(12.dp).size(22.dp), tint = theme.cardAccent)
                }
                Column(Modifier.weight(1f)) {
                    Text("Por entregar hoy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = theme.cardTextColor)
                    Text("Tus tareas y entregas del día", style = MaterialTheme.typography.bodySmall, color = theme.cardSubtextColor)
                }
                Surface(shape = RoundedCornerShape(12.dp), color = theme.cardIconBg) {
                    Text(items.size.toString(), Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = theme.cardAccent)
                }
            }
            if (items.isEmpty()) {
                TodayEmptyState(if (updated == null) "Conecta Canvas para ver tus entregas" else "Sin entregas pendientes para hoy",
                    if (updated == null) "Conecta tu cuenta desde la pestaña Canvas." else if (current) "No tienes entregas pendientes que venzan hoy." else "No hay entregas de hoy en los datos guardados.")
            } else {
                items.forEach { assignment -> androidx.compose.runtime.key(assignment.key) { TodayAssignment(assignment, now, true, theme) } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCanvas) { Text("Ver Canvas", color = theme.cardAccent, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun TodayAssignment(item: AgendaItem, now: Long, todayOnly: Boolean, theme: TodayCardTheme = rememberTodayCardTheme()) {
    val context = LocalContext.current
    val deliveryUrl = item.url?.takeIf { Uri.parse(it).scheme == "https" && Uri.parse(it).host == "upao.instructure.com" }
    fun openDelivery() { deliveryUrl?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }
    val late = !item.submitted && !item.graded && (item.dueMillis ?: Long.MAX_VALUE) < now
    val accent = if (late) MaterialTheme.colorScheme.error else theme.cardAccent

    Card(
        Modifier.fillMaxWidth().clickable(enabled = deliveryUrl != null, onClick = { openDelivery() }),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = theme.innerCardBg, contentColor = theme.cardTextColor),
        border = BorderStroke(1.dp, theme.innerCardBorder)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.course, style = MaterialTheme.typography.labelMedium, color = theme.cardSubtextColor)
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = theme.cardTextColor)
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
                        Text("Ver entrega", color = accent, fontWeight = FontWeight.Bold)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.padding(start = 6.dp).size(16.dp), tint = accent)
                    }
                }
            }
        }
    }
}

@Composable
fun TodayNewsCard(
    items: List<NotificacionItem>,
    now: Long,
    updated: Long?,
    current: Boolean,
    onNotifications: () -> Unit
) {
    if (items.isEmpty()) return
    val theme = rememberTodayCardTheme()

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = theme.cardBg, contentColor = theme.cardTextColor),
        border = BorderStroke(1.dp, theme.cardBorder)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(14.dp), color = theme.cardIconBg) {
                    Icon(Icons.Filled.Notifications, null, Modifier.padding(12.dp).size(22.dp), tint = theme.cardAccent)
                }
                Column(Modifier.weight(1f)) {
                    Text("Novedades de hoy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = theme.cardTextColor)
                    Text("Notas, asistencias y avisos de hoy", style = MaterialTheme.typography.bodySmall, color = theme.cardSubtextColor)
                }
                Surface(shape = RoundedCornerShape(12.dp), color = theme.cardIconBg) {
                    Text(
                        items.size.toString(),
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = theme.cardAccent
                    )
                }
            }

            if (items.isEmpty()) {
                Surface(
                    color = theme.innerCardBg,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, theme.innerCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = theme.cardAccent, modifier = Modifier.size(22.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Sin novedades hoy", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = theme.cardTextColor)
                            Text(
                                if (updated == null) "Actualiza para consultar las novedades del día."
                                else if (current) "No se registraron nuevas notas ni cambios de asistencia hoy."
                                else "No hay novedades de hoy en los datos guardados.",
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.cardSubtextColor
                            )
                        }
                    }
                }
            } else {
                items.forEach { notice ->
                    Surface(
                        color = theme.innerCardBg,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, theme.innerCardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNotifications)
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Filled.Notifications, null, Modifier.size(20.dp), tint = theme.cardAccent)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        if (!notice.leida) "NUEVA NOTA" else "ACTUALIZACIÓN",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = theme.cardAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                    notificationMillis(notice.fechaCreacion)?.let {
                                        Text(campusFormat(it, "HH:mm"), style = MaterialTheme.typography.labelSmall, color = theme.cardSubtextColor)
                                    }
                                }
                                Text(notice.mensaje, style = MaterialTheme.typography.bodyMedium, color = theme.cardTextColor)
                                notice.curso?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = theme.cardSubtextColor)
                                }
                            }
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onNotifications) {
                    Text("Ver notificaciones", color = theme.cardAccent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}


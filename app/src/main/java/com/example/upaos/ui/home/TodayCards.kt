package com.example.upaos.ui.home
import com.example.upaos.ui.components.CampusCard
import com.example.upaos.ui.components.limpiarMensajeNotificacion
import com.example.upaos.ui.components.toTitleCase

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.example.upaos.ui.theme.campusIsDark
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF102C67), Color(0xFF245BE0), Color(0xFF197DAD))))
            .drawBehind {
                val center = Offset(size.width * 1.02f, size.height * .12f)
                for (radius in listOf(58.dp, 92.dp, 126.dp)) {
                    drawCircle(Color.White.copy(alpha = .09f), radius.toPx(), center, style = Stroke(1.dp.toPx()))
                }
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                campusFormat(now, "EEEE, d 'de' MMMM").replaceFirstChar { it.titlecase() },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = .85f)
            )
            Surface(color = Color.White.copy(alpha = .15f), shape = RoundedCornerShape(50)) {
                Text("HOY", Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("Tu día, a la vista", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Clases, pendientes y novedades de hoy", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .85f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = .12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = .85f), modifier = Modifier.size(13.dp))
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
    val isDark = campusIsDark
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

    CampusCard(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = theme.cardBg, contentColor = theme.cardTextColor),
        border = BorderStroke(1.dp, theme.cardBorder)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(10.dp), color = theme.cardIconBg) {
                    Icon(Icons.Filled.Schedule, null, Modifier.padding(8.dp).size(18.dp), tint = theme.cardAccent)
                }
                Column(Modifier.weight(1f)) {
                    Text("Tus clases de hoy", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = theme.cardTextColor)
                    Text(when { !available -> "Horario por actualizar"; classes.isEmpty() -> "Hoy no tienes clases programadas"; next == null -> "Clases de hoy finalizadas"; next.start <= now -> "Estás en horario de clase"; else -> "Tu siguiente clase de hoy" },
                        style = MaterialTheme.typography.bodySmall, color = theme.cardSubtextColor)
                }
            }
            Column {
                classes.forEachIndexed { index, lesson ->
                    com.example.upaos.ui.components.ClassTimelineRow(
                        start = campusFormat(lesson.start, "HH:mm"),
                        end = campusFormat(lesson.end, "HH:mm"),
                        title = lesson.name,
                        room = lesson.room,
                        accent = com.example.upaos.ui.components.courseAccent(lesson.name),
                        connectAbove = index > 0,
                        connectBelow = index < classes.lastIndex,
                        highlighted = lesson == next,
                        detail = when {
                            lesson.end <= now -> "Finalizada"
                            lesson.start <= now -> "En curso"
                            lesson == next -> "Próxima clase"
                            else -> null
                        }
                    )
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

    CampusCard(
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

    CampusCard(
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
    val unread = items.count { !it.leida }
    CampusCard(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Filled.Notifications, null, Modifier.padding(10.dp).size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Novedades de hoy", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(if (unread > 0) "$unread sin leer · ${items.size} avisos hoy" else "Estás al día · ${items.size} avisos hoy",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items.take(3).forEach { notice ->
                androidx.compose.runtime.key(notice.id) {
                    com.example.upaos.ui.components.NotificationCard(
                        notice = notice,
                        onClick = onNotifications,
                        compact = true,
                        timeLabel = notificationMillis(notice.fechaCreacion)?.let { campusFormat(it, "HH:mm") }.orEmpty()
                    )
                }
            }
            SourceStamp(updated, current)
            TextButton(onClick = onNotifications, modifier = Modifier.fillMaxWidth()) {
                Text(if (items.size > 3) "Ver todas las novedades (${items.size})" else "Ver notificaciones", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp))
            }
        }
    }
}

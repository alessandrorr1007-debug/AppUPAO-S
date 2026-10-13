package com.example.upaos.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.upaos.data.agenda.*
import com.example.upaos.data.model.*
import com.example.upaos.service.AgendaReminderWorker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun Freshness(updated: Long?, current: Boolean, loading: Boolean = false) {
    Text(if (loading) "Actualizando…" else if (current) "Consultado en esta sesión" else "Datos guardados · pendiente de actualizar",
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Última actualización: " + (updated?.let { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(it)) } ?: "sin registro"),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

data class NextClass(val name: String, val room: String, val start: Long, val end: Long)

fun nextClass(courses: List<HorarioCurso>, now: Long): NextClass? {
    val zone = TimeZone.getTimeZone("America/Lima")
    val calendar = Calendar.getInstance(zone).apply { timeInMillis = now }
    val today = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
    fun minutes(text: String?): Int? {
        val parts = text?.split(':') ?: return null
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return if (h in 0..23 && m in 0..59) h * 60 + m else null
    }
    return courses.flatMap { course -> course.bloques.mapNotNull { block ->
        val day = block.dia?.takeIf { it in 0..6 } ?: return@mapNotNull null
        val startMinutes = minutes(block.horaInicio) ?: return@mapNotNull null
        val endMinutes = minutes(block.horaFin) ?: return@mapNotNull null
        val start = (calendar.clone() as Calendar).apply {
            add(Calendar.DATE, (day - today + 7) % 7)
            set(Calendar.HOUR_OF_DAY, startMinutes / 60); set(Calendar.MINUTE, startMinutes % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        var end = start.timeInMillis + (endMinutes - startMinutes) * 60_000L
        if (end <= now) { start.add(Calendar.DATE, 7); end += 7 * 86_400_000L }
        NextClass(course.displayNombre, block.aula?.trim()?.takeIf { it.isNotEmpty() } ?: "Virtual", start.timeInMillis, end)
    } }.minByOrNull { it.start }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun TodayContent(token: String, dashboard: DashboardViewModel, usuario: String?, onSchedule: () -> Unit, onCanvas: () -> Unit, onNotifications: () -> Unit,
                 openAgenda: Boolean = false, onAgendaOpened: () -> Unit = {}) {
    if (usuario.isNullOrBlank()) return
    val context = LocalContext.current
    val shared by dashboard.state.collectAsState()
    LaunchedEffect(openAgenda) { if (openAgenda) onAgendaOpened() }
    val items = shared.agenda
    val timetable = shared.schedules[shared.currentPeriod]
    val notices = shared.notices?.notificaciones ?: emptyList()
    val agendaTime = shared.times["agenda_$usuario"]
    val scheduleTime = shared.times[dashboard.scheduleCacheKey(shared.currentPeriod)]
    val noticesTime = shared.times["notificaciones_$usuario"]
    val agendaFresh = "agenda_$usuario" in shared.fresh
    val scheduleFresh = dashboard.scheduleCacheKey(shared.currentPeriod) in shared.fresh
    val noticesFresh = "notificaciones_$usuario" in shared.fresh
    val busy = shared.busy
    val error = listOfNotNull(shared.errors["agenda_$usuario"], shared.errors[dashboard.scheduleCacheKey(shared.currentPeriod)], shared.errors["notificaciones_$usuario"]).joinToString("\n").takeIf { it.isNotBlank() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                val untilMidnight = campusDay(now).last + 1 - System.currentTimeMillis()
                kotlinx.coroutines.delay(minOf(60_000L, untilMidnight).coerceAtLeast(1L))
                now = System.currentTimeMillis()
            }
        }
    }
    val dayClasses = remember(timetable, now) { classesToday(timetable?.listaCursos ?: emptyList(), now) }
    val dayTasks = remember(items, now) { assignmentsToday(items, now) }
    val dayNotices = remember(notices, now) { notificationsToday(notices, now) }
    var showDetails by remember { mutableStateOf(false) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                IconButton(enabled = !busy, onClick = { dashboard.refresh() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Actualizar datos")
                }
            }
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            error?.let { detail ->
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(top = 10.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("Actualización pendiente", style = MaterialTheme.typography.labelLarge)
                        Text("Algunos datos no se pudieron consultar. Mostramos lo último guardado.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { showDetails = !showDetails }) { Text(if (showDetails) "Ocultar detalle" else "Ver detalle") }
                        if (showDetails) Text(detail, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
            item { TodayHero(now, if (timetable != null) dayClasses.size else null, if (agendaTime != null) dayTasks.size else null, if (noticesTime != null) dayNotices.size else null) }
            item { TodaySchedule(dayClasses, timetable != null, now, scheduleTime, scheduleFresh, onSchedule) }
            item {
                TodaySectionTitle("Por entregar hoy", dayTasks.size)
                SourceStamp(agendaTime, agendaFresh)
                if (dayTasks.isEmpty()) TodayEmptyState(
                    if (agendaTime == null) "Conecta Canvas para ver tus entregas" else "Sin entregas pendientes para hoy",
                    if (agendaTime == null) "Conecta tu cuenta desde la pestaña Canvas y actualiza esta pantalla." else if (agendaFresh) "No tienes entregas pendientes que venzan hoy." else "No hay entregas de hoy en los datos guardados. Actualiza cuando tengas conexión."
                )
            }
            items(dayTasks, key = { "today_task_${it.key}" }) { assignment -> TodayAssignment(assignment, now, true) }
            item {
                TodaySectionTitle("Novedades de hoy", dayNotices.size)
                SourceStamp(noticesTime, noticesFresh)
                if (dayNotices.isEmpty()) TodayEmptyState("Sin novedades de notas hoy",
                    if (noticesTime == null) "Actualiza para consultar las novedades de este día." else if (noticesFresh) "Cuando se registre un cambio hoy, aparecerá aquí." else "No hay novedades de hoy en los datos guardados. Actualiza cuando tengas conexión.")
            }
            items(dayNotices, key = { "today_notice_${it.id}" }) { notice -> TodayNotice(notice, onNotifications) }
    }
}

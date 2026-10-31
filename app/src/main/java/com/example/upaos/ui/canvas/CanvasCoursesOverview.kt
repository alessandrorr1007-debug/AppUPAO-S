package com.example.upaos.ui.canvas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.upaos.data.model.*
import com.example.upaos.ui.components.RefreshableContent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Instant

@Composable
internal fun CanvasCoursesOverview(token: String, courses: List<CanvasCourse>, loading: Boolean, refreshing: Boolean,
    refreshCourses: () -> Unit, open: (CanvasCourse) -> Unit) {
    var search by remember { mutableStateOf("") }
    var term by remember { mutableStateOf<String?>(null) }
    val ids = courses.map { it.id }.sorted()
    val tasks = canvasCachedList("overviewTasks:$ids", token, CanvasAssignment::class.java) {
        coroutineScope {
            val limit = Semaphore(4)
            courses.map { course -> async { limit.withPermit { assignments(course.id).map { it.copy(courseId = course.id) } } } }.awaitAll().flatten()
        }
    }
    val taskState by tasks.state.collectAsState()
    val announcements = canvasCachedList("announcements:$ids", token, CanvasAnnouncement::class.java) { announcements(ids) }
    val announcementState by announcements.state.collectAsState()
    val terms = courses.mapNotNull { it.term?.name }.distinct().sortedDescending()
    val visible = courses.filter { (term == null || it.term?.name == term) &&
        (search.isBlank() || listOfNotNull(it.name, it.courseCode, it.term?.name).any { text -> text.contains(search, true) }) }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        OutlinedTextField(search, { search = it }, label = { Text("Buscar cursos") }, modifier = Modifier.fillMaxWidth())
        if (terms.isNotEmpty()) ScrollableTabRow(if (term == null) 0 else (terms.indexOf(term) + 1).coerceAtLeast(0)) {
            Tab(term == null, onClick = { term = null }, text = { Text("Todos los periodos") })
            terms.forEach { t -> Tab(term == t, onClick = { term = t }, text = { Text(t) }) }
        }
        CanvasResourceFeedback(taskState) { tasks.refresh(true) }
        CanvasResourceFeedback(announcementState) { announcements.refresh(true) }
        RefreshableContent(refreshing || taskState.refreshing || announcementState.refreshing,
            { refreshCourses(); tasks.refresh(true); announcements.refresh(true) }, Modifier.fillMaxSize()) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    if (loading || taskState.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("${courses.size} cursos activos", style = MaterialTheme.typography.titleMedium)
                    if (!taskState.loading && taskState.failure == null) {
                        val now = Instant.now()
                        Text("${taskState.data.count { canvasTaskMatches(it, "Próximas", now) }} próximas · ${taskState.data.count { canvasTaskMatches(it, "Vencidas", now) }} vencidas · ${taskState.data.count { canvasTaskMatches(it, "Entregadas", now) }} entregadas")
                    }
                    Text("Las calificaciones de Canvas son independientes de Banner.", style = MaterialTheme.typography.bodySmall)
                }
                if (announcementState.data.isNotEmpty()) item {
                    Text("Anuncios recientes", style = MaterialTheme.typography.titleSmall)
                    announcementState.data.sortedByDescending { it.postedAt }.take(3).forEach { a -> Text("${a.title ?: "Anuncio"} · ${a.postedAt ?: "Sin fecha"}") }
                }
                items(visible, key = { it.id }) { course ->
                    Column {
                        CanvasCourseCard(course) { open(course) }
                        course.term?.name?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                        course.teachers?.takeIf { it.isNotEmpty() }?.let { people -> Text(people.mapNotNull { it.name }.joinToString(), style = MaterialTheme.typography.bodySmall) }
                        if (!taskState.loading && taskState.failure == null) Text("${taskState.data.count { it.courseId == course.id && canvasTaskMatches(it, "Pendientes", Instant.now()) }} tareas pendientes", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (!loading && visible.isEmpty()) item { Text("Sin cursos para esta búsqueda") }
            }
        }
    }
}

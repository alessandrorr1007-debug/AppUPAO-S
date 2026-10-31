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
import java.time.OffsetDateTime
import java.time.Instant

internal fun canvasTaskMatches(a: CanvasAssignment, filter: String, now: Instant): Boolean {
    val submitted = a.submission?.isSubmitted == true
    val due = a.effectiveDueAt?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
    return when (filter) {
        "Pendientes" -> !submitted
        "Próximas" -> !submitted && due != null && !due.isBefore(now)
        "Entregadas" -> submitted
        "Vencidas" -> !submitted && due != null && due.isBefore(now)
        "Calificadas" -> a.submission?.workflowState == "graded" && (a.submission.grade != null || a.submission.score != null)
        else -> true
    }
}

@Composable
internal fun CanvasCourseTasks(course: CanvasCourse, token: String, open: (CanvasTodoItem) -> Unit) {
    val vm = canvasCachedList("tasks:${course.id}", token, CanvasAssignment::class.java) { assignments(course.id) }
    val state by vm.state.collectAsState()
    CanvasRefreshOnResume { vm.refresh(true) }
    var filter by remember { mutableStateOf("Todas") }
    val filters = listOf("Todas", "Pendientes", "Próximas", "Entregadas", "Vencidas", "Calificadas")
    CanvasResourceFeedback(state) { vm.refresh(true) }
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(filters.indexOf(filter)) { filters.forEach { f -> Tab(filter == f, onClick = { filter = f }, text = { Text(f) }) } }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        RefreshableContent(state.refreshing, { vm.refresh(true) }, Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val tasks = state.data.filter { canvasTaskMatches(it, filter, Instant.now()) }
                    .sortedBy { it.effectiveDueAt ?: "9999" }
                if (tasks.isEmpty() && !state.loading) item { Text("Sin tareas en este filtro") }
                items(tasks, key = { it.id }) { a ->
                    val item = CanvasTodoItem(courseId = course.id, contextName = course.name, assignment = a)
                    CanvasTodoItemCard(item) { open(item) }
                }
            }
        }
    }
}

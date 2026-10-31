package com.example.upaos.ui.canvas

import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import com.example.upaos.data.canvas.CanvasNativeRoute
import com.example.upaos.data.model.*

@Composable
internal fun CanvasLinkedResource(route: CanvasNativeRoute, token: String, close: () -> Unit) {
    when (route.kind) {
        "pages" -> CanvasPageDetail(route.course, route.value, token, close)
        "discussion_topics" -> {
            val vm = canvasResourceViewModel("linkedTopic:${route.course}:${route.value}", token, null as CanvasDiscussionTopic?) { discussion(route.course, route.value.toLong()) }
            val state by vm.state.collectAsState()
            if (state.data != null) CanvasDiscussionDetail(route.course, state.data!!, token, close)
            else CanvasReadDetail("Discusión", close) { CanvasResourceFeedback(state) { vm.refresh(true) }; if (state.loading) CircularProgressIndicator() }
        }
        "assignments" -> {
            val vm = canvasResourceViewModel("linkedTask:${route.course}:${route.value}", token, null as CanvasAssignment?) { assignment(route.course, route.value.toLong()) }
            val state by vm.state.collectAsState()
            if (state.data != null) CanvasAssignmentDetailSheet(CanvasTodoItem(courseId = route.course, assignment = state.data), close)
            else CanvasReadDetail("Tarea", close) { CanvasResourceFeedback(state) { vm.refresh(true) }; if (state.loading) CircularProgressIndicator() }
        }
        "files" -> {
            val vm = canvasResourceViewModel("linkedFile:${route.course}:${route.value}", token, null as CanvasFileDetail?) {
                if (route.course == 0L) fileByUrl("https://upao.instructure.com/api/v1/files/${route.value}") else file(route.course, route.value.toLong())
            }
            val state by vm.state.collectAsState()
            CanvasReadDetail("Documento", close) { CanvasResourceFeedback(state) { vm.refresh(true) }; if (state.loading) CircularProgressIndicator(); state.data?.let { CanvasDocument(it, token) } }
        }
        "image" -> CanvasReadDetail("Imagen del contenido", close) {
            CanvasDocument(CanvasFileDetail(0, filename = "formula.png", contentType = "image/png", url = route.value), token)
        }
        "quizzes" -> androidx.compose.ui.window.Dialog(onDismissRequest = close,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) { Column { TextButton(onClick = close) { Text("Cerrar cuestionarios") }; Box(Modifier.weight(1f)) { CanvasQuizzes(route.course, token, route.value.toLong()) } } }
        }
    }
}

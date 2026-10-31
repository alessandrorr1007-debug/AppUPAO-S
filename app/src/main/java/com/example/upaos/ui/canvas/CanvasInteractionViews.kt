package com.example.upaos.ui.canvas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.upaos.data.model.*
import com.example.upaos.data.local.TokenManager
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun CanvasComposer(flow: String, token: String, label: String = "Respuesta", enabled: Boolean = true,
    afterSend: () -> Unit, send: suspend (String) -> Unit) {
    val app = LocalContext.current.applicationContext
    val tokens = remember(app) { TokenManager(app) }
    val vm: CanvasWriteViewModel = viewModel(key = canvasViewModelKey("write:$flow", tokens.getSavedUser().orEmpty(), tokens.getSessionId(), token),
        factory = canvasViewModelFactory { CanvasWriteViewModel() })
    val state by vm.state.collectAsState()
    var message by remember(flow, token) { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(state.confirmed) { if (state.confirmed) { message = ""; afterSend() } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(message, { if (it.length <= 20000) message = it }, label = { Text(label) },
            enabled = enabled && !state.sending && !state.uncertain && !state.confirmed, modifier = Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.confirmed) Text("Envío confirmado por Canvas")
        if (state.sending) CircularProgressIndicator(Modifier.size(24.dp))
        if (state.confirmed || state.uncertain) {
            TextButton(onClick = { confirm = true }) { Text(if (state.uncertain) "Ya revisé el hilo; redactar de nuevo" else "Nueva respuesta") }
        } else Button(enabled = enabled && message.isNotBlank() && !state.sending, onClick = { confirm = true }) { Text("Enviar") }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Confirmar") },
        text = { Text(if (state.uncertain) "Comprueba que tu mensaje no aparece antes de redactarlo de nuevo. Canvas podría haber recibido el envío anterior."
            else if (state.confirmed) "¿Redactar otra respuesta?" else "¿Enviar este mensaje a Canvas?") },
        confirmButton = { TextButton(onClick = {
            confirm = false
            if (state.confirmed || state.uncertain) { message = ""; vm.newDraft() }
            else { val draft = message; vm.send { send(draft) } }
        }) { Text("Confirmar") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } })
}

@Composable
internal fun CanvasNativeInbox(token: String, courses: List<CanvasCourse>, open: (CanvasConversation) -> Unit) {
    var scope by remember { mutableStateOf("inbox") }
    var compose by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    val vm = canvasResourceViewModel("inbox:$scope", token, emptyList<CanvasConversation>()) { inbox(scope) }
    val state by vm.state.collectAsState()
    CanvasResourceFeedback(state) { vm.refresh(true) }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        ScrollableTabRow(listOf("inbox", "sent", "unread", "archived").indexOf(scope)) {
            listOf("inbox" to "Entrada", "sent" to "Enviados", "unread" to "No leídos", "archived" to "Archivados").forEach { (id, text) ->
                Tab(scope == id, onClick = { scope = id }, text = { Text(text) })
            }
        }
        Row { TextButton(onClick = { compose = true }, enabled = courses.isNotEmpty()) { Text("Nuevo mensaje") }
            TextButton(onClick = { vm.refresh(true) }) { Text("Actualizar") } }
        OutlinedTextField(search, { search = it }, label = { Text("Buscar conversación") }, modifier = Modifier.fillMaxWidth())
        if (state.loading || state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn {
            items(state.data.filter { search.isBlank() || listOfNotNull(it.subject, it.lastMessage, it.contextName).any { s -> s.contains(search, true) } }, key = { it.id }) { conversation ->
                ListItem(headlineContent = { Text(conversation.subject ?: "Sin asunto") },
                    supportingContent = { Text(listOfNotNull(conversation.contextName, conversation.lastMessage).joinToString(" · ")) },
                    overlineContent = { if (conversation.isUnread) Text("No leído") }, modifier = Modifier.clickable { open(conversation) })
            }
            if (!state.loading && state.data.isEmpty()) item { Text("Sin conversaciones en esta bandeja") }
        }
    }
    if (compose) CanvasNewMessage(token, courses, { compose = false }) { vm.refresh(true) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CanvasNewMessage(token: String, courses: List<CanvasCourse>, close: () -> Unit, refresh: () -> Unit) {
    var course by remember { mutableStateOf(courses.first()) }
    var query by remember { mutableStateOf("") }
    var submittedQuery by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var selected by remember(course.id) { mutableStateOf(emptyList<CanvasRecipient>()) }
    val repository = rememberCanvasRepository(token)
    val vm = canvasResourceViewModel("recipients:${course.id}:$submittedQuery", token, emptyList<CanvasRecipient>()) {
        if (submittedQuery.length >= 2) recipients(course.id, submittedQuery) else emptyList()
    }
    val state by vm.state.collectAsState()
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Nuevo mensaje", style = MaterialTheme.typography.titleLarge)
                Text("Curso: ${course.displayTitle}")
                ScrollableTabRow(courses.indexOf(course).coerceAtLeast(0)) { courses.forEach { c -> Tab(c.id == course.id,
                    onClick = { course = c; submittedQuery = ""; query = "" }, text = { Text(c.displayTitle) }) } }
                OutlinedTextField(query, { query = it }, label = { Text("Buscar destinatarios del curso") })
                TextButton(onClick = { submittedQuery = query.trim() }, enabled = query.trim().length >= 2) { Text("Buscar") }
                CanvasResourceFeedback(state) { vm.refresh(true) }
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            items(state.data, key = { it.id }) { person ->
                Row { Checkbox(selected.any { it.id == person.id }, { checked -> selected = if (checked) (selected + person).distinctBy { it.id }.take(20) else selected.filterNot { it.id == person.id } })
                    Text(person.name ?: "Destinatario", Modifier.padding(top = 12.dp)) }
            }
            item {
                Text("Para: ${selected.joinToString { it.name ?: "Destinatario" }}")
                OutlinedTextField(subject, { if (it.length <= 255) subject = it }, label = { Text("Asunto") })
                // The repository validates the captured account again before and after sending.
                CanvasComposer("new:${course.id}", token, "Mensaje", selected.isNotEmpty() && subject.isNotBlank(), refresh) { body ->
                    repository.sendConversation(course.id, selected.map { it.id }, subject, body)
                }
            }
        }
    }
}

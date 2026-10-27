package com.example.upaos.ui.canvas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.upaos.data.canvas.*
import com.example.upaos.data.local.CanvasCache
import com.example.upaos.data.local.TokenManager
import com.example.upaos.data.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

@Composable
internal fun canvasSavedToken(): String {
    val context = LocalContext.current
    val tokens = remember(context) { TokenManager(context) }
    return tokens.getSavedUser()?.let { tokens.getCanvasToken(it) }.orEmpty()
}

@Composable
private fun <T> canvasCachedList(flow: String, token: String, itemClass: Class<T>,
    load: suspend CanvasCourseRepository.() -> List<T>): CanvasResourceViewModel<List<T>> {
    val context = LocalContext.current.applicationContext
    val tokens = remember(context) { TokenManager(context) }
    val user = tokens.getSavedUser().orEmpty()
    val session = tokens.getSessionId()
    val repository = rememberCanvasRepository(token)
    val cache = remember(context) { CanvasCache(context) }
    val gson = remember { Gson() }
    return androidx.lifecycle.viewmodel.compose.viewModel(
        key = canvasViewModelKey(flow, user, session, token),
        factory = canvasViewModelFactory {
            CanvasResourceViewModel(CanvasResourceRepository {
                val data = repository.load()
                repository.requireSession()
                cache.saveCanvasList(user, flow, gson.toJson(data))
                data
            }, emptyList<T>(), restore = {
                repository.requireSession()
                cache.readCanvasList(user, flow)?.let {
                    gson.fromJson<List<T>>(it, TypeToken.getParameterized(List::class.java, itemClass).type)
                }
            })
        })
}

@Composable
private fun <T> CanvasReadList(vm: CanvasResourceViewModel<List<T>>, leading: @Composable () -> Unit = {}, row: @Composable (T) -> Unit) {
    val state by vm.state.collectAsState()
    CanvasResourceFeedback(state) { vm.refresh(true) }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row {
            TextButton(onClick = { vm.refresh(true) }, enabled = !state.refreshing) { Text("Actualizar") }
            if (state.loading || state.refreshing) CircularProgressIndicator(Modifier.size(24.dp))
        }
        if (!state.loading && state.data.isEmpty()) Text("Sin elementos disponibles")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { leading() }
            items(state.data) { item -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { row(item) } } }
        }
    }
}

@Composable
private fun CanvasReadDetail(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row { Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); TextButton(onClick = onClose) { Text("Cerrar") } }
                LazyColumn(Modifier.weight(1f)) { item { Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content) } }
            }
        }
    }
}

@Composable
internal fun CanvasAnnouncements(token: String, courses: List<Long>) {
    val vm = canvasCachedList("announcements:${courses.sorted()}", token, CanvasAnnouncement::class.java) { announcements(courses) }
    val context = LocalContext.current
    val tokens = remember { TokenManager(context) }
    val user = tokens.getSavedUser().orEmpty()
    val cache = remember { CanvasCache(context) }
    var seen by remember(user) { mutableStateOf(emptySet<String>()) }
    var selected by remember { mutableStateOf<CanvasAnnouncement?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(user) { seen = cache.seenAnnouncements(user) }
    CanvasReadList(vm) { a ->
        val key = "${a.contextCode}:${a.id}"
        Text(a.title ?: "Anuncio", fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable {
                selected = a
                if (a.id != null) scope.launch { cache.markAnnouncementSeen(user, key); seen = seen + key }
            })
        Text(a.postedAt ?: "Fecha no disponible")
        if (a.id != null && key !in seen) Text("No visto · solo en este dispositivo")
    }
    selected?.let { a -> CanvasReadDetail(a.title ?: "Anuncio", { selected = null }) {
        HtmlText(a.message.orEmpty())
        CanvasAttachments(a.attachments)
        CanvasExternalButton(a.htmlUrl)
    } }
}

@Composable
internal fun CanvasAttachments(files: List<CanvasFileDetail>?) {
    val context = LocalContext.current
    files.orEmpty().forEach { file ->
        TextButton(onClick = { openCanvasContentLink(context, file.url) }) { Text(file.fileName) }
    }
}

@Composable
internal fun CanvasAssignmentExtras(item: CanvasTodoItem) {
    val course = item.courseId ?: item.assignment?.courseId ?: return
    val assignment = item.assignment ?: return
    val vm = canvasResourceViewModel("submission:$course:${assignment.id}", canvasSavedToken(), null as CanvasSubmission?) { submission(course, assignment.id) }
    val state by vm.state.collectAsState()
    CanvasResourceFeedback(state) { vm.refresh(true) }
    if (state.loading) CircularProgressIndicator(Modifier.size(24.dp))
    val submission = state.data ?: assignment.submission
    submission?.let { sub ->
        Text("Intento: ${sub.attempt ?: "No especificado"} · ${sub.workflowState ?: "No disponible"}")
        Text("Entregada: ${sub.submittedAt ?: "No disponible"}")
        if (sub.late == true) Text("Tardía")
        if (sub.missing == true) Text("Faltante")
        Text("Nota: ${sub.grade ?: canvasPoints(sub.score)} / ${canvasPoints(assignment.pointsPossible)}")
        sub.comments.orEmpty().forEach { comment ->
            HorizontalDivider()
            Text("${comment.authorName ?: "Autor no disponible"} · ${comment.createdAt.orEmpty()}", fontWeight = FontWeight.Bold)
            HtmlText(comment.comment.orEmpty())
            CanvasAttachments(comment.attachments)
        }
    }
    assignment.rubric.orEmpty().forEach { criterion ->
        val assessment = submission?.rubricAssessment?.get(criterion.id)
        HorizontalDivider()
        Text(criterion.description ?: "Criterio", fontWeight = FontWeight.Bold)
        HtmlText(criterion.longDescription.orEmpty())
        Text("${canvasPoints(assessment?.points)} / ${canvasPoints(criterion.points)} pts")
        criterion.ratings.orEmpty().forEach { rating ->
            val reached = rating.id != null && rating.id == assessment?.ratingId
            Surface(color = if (reached) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                Text("${if (reached) "✓ " else ""}${rating.description.orEmpty()} · ${canvasPoints(rating.points)} pts", Modifier.padding(6.dp))
            }
        }
        assessment?.comments?.let { HtmlText(it) }
    }
    val rubric = assignment.rubric.orEmpty()
    if (rubric.isNotEmpty()) {
        val values = rubric.map { submission?.rubricAssessment?.get(it.id)?.points }
        Text("Total rúbrica: ${if (values.all { it != null }) canvasPoints(values.filterNotNull().sum()) else "No disponible"} / ${if (rubric.all { it.points != null }) canvasPoints(rubric.sumOf { it.points ?: 0.0 }) else "No disponible"}")
    }
}

@Composable
internal fun CanvasCourseGrades(course: CanvasCourse, token: String, openAssignment: (CanvasTodoItem) -> Unit) {
    val enrollment = canvasCachedList("enrollments:${course.id}", token, CanvasCourseEnrollment::class.java) { enrollments(course.id) }
    val state by enrollment.state.collectAsState()
    CanvasResourceFeedback(state) { enrollment.refresh(true) }
    val groups = canvasCachedList("assignmentGroups:${course.id}", token, CanvasAssignmentGroup::class.java) { assignmentGroups(course.id) }
    Column(Modifier.fillMaxSize()) {
        val grades = state.data.firstOrNull()?.grades
        Text("Nota actual: ${grades?.currentGrade ?: canvasPoints(grades?.currentScore)}", Modifier.padding(12.dp))
        Text("Nota final: ${grades?.finalGrade ?: canvasPoints(grades?.finalScore)}", Modifier.padding(horizontal = 12.dp))
        CanvasReadList(groups) { group ->
            Text("${group.name ?: "Grupo"} · peso ${canvasPoints(group.groupWeight)}%", fontWeight = FontWeight.Bold)
            group.assignments.orEmpty().forEach { a ->
                TextButton(onClick = { openAssignment(CanvasTodoItem(courseId = course.id, contextName = course.name, assignment = a)) }) { Text(a.name ?: "Actividad") }
                Text("${a.submission?.grade ?: canvasPoints(a.submission?.score)} / ${canvasPoints(a.pointsPossible)} · ${a.submission?.workflowState ?: "No disponible"}")
                Text("Vence: ${a.effectiveDueAt ?: "Sin fecha de entrega"}")
                canvasPassing(a.submission?.score, a.pointsPossible)?.let { Text(if (it) "Aprobado" else "Desaprobado") }
            }
        }
    }
}

@Composable
internal fun CanvasCalendar(token: String, courses: List<Long>) {
    val vm = canvasCachedList("calendar:${courses.sorted()}", token, CanvasCalendarEvent::class.java) { calendar(courses) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf<CanvasCalendarEvent?>(null) }
    val state by vm.state.collectAsState()
    CanvasResourceFeedback(state) { vm.refresh(true) }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row {
            TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
            Text(month.toString(), Modifier.weight(1f))
            TextButton(onClick = { month = month.plusMonths(1) }) { Text("›") }
            TextButton(onClick = { vm.refresh(true) }) { Text("Actualizar") }
        }
        if (state.loading || state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
        val window = canvasCalendarWindow()
        Text("Ventana: ${window.first} a ${window.second}", style = MaterialTheme.typography.bodySmall)
        val grouped = state.data.groupBy {
            (if (it.allDay == true) it.allDayDate else null)
                ?: canvasCalendarDay(it.startAt ?: it.assignment?.effectiveDueAt) ?: "Sin fecha"
        }
        LazyColumn {
            grouped.toSortedMap().filterKeys { it.startsWith(month.toString()) || it == "Sin fecha" }.forEach { (day, events) ->
                item { Text(day, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp)) }
                items(events) { event -> ListItem(headlineContent = { Text(event.title ?: event.assignment?.name ?: "Evento") },
                    supportingContent = { Text(event.startAt ?: "Sin fecha") }, modifier = Modifier.clickable { selected = event }) }
            }
            if (state.data.isEmpty() && !state.loading) item { Text("Sin eventos disponibles") }
        }
    }
    selected?.let { event -> CanvasReadDetail(event.title ?: "Evento", { selected = null }) {
        Text("Inicio: ${event.startAt ?: "No disponible"} · Fin: ${event.endAt ?: "No disponible"}")
        Text(listOfNotNull(event.locationName, event.locationAddress).joinToString(" · "))
        HtmlText(event.description ?: event.assignment?.description.orEmpty())
        CanvasExternalButton(event.htmlUrl ?: event.assignment?.htmlUrl)
    } }
}

@Composable
internal fun CanvasPageDetail(course: Long, page: String?, token: String, onClose: () -> Unit) {
    val vm = canvasResourceViewModel("page:$course:$page", token, null as CanvasPage?) { if (page == null) frontPage(course) else this.page(course, page) }
    val state by vm.state.collectAsState()
    CanvasResourceFeedback(state) { vm.refresh(true) }
    CanvasReadDetail(state.data?.title ?: "Página", onClose) {
        if (state.loading) CircularProgressIndicator() else if (state.data == null) Text("No disponible")
        state.data?.let { HtmlText(it.body.orEmpty()); CanvasExternalButton(it.htmlUrl) }
    }
}

@Composable
internal fun CanvasPages(course: Long, token: String) {
    val vm = canvasCachedList("pages:$course", token, CanvasPage::class.java) { pages(course) }
    var selected by remember { mutableStateOf<String?>(null) }
    var front by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { front = true }) { Text("Página de inicio") }
        CanvasReadList(vm) { page -> TextButton(onClick = { selected = page.url ?: page.pageId?.let { "page_id:$it" } }, enabled = page.url != null || page.pageId != null) { Text(page.title ?: "Página") } }
    }
    if (front || selected != null) CanvasPageDetail(course, selected, token) { selected = null; front = false }
}

@Composable
internal fun CanvasQuizzes(course: Long, token: String) {
    val vm = canvasCachedList("quizzes:$course", token, CanvasQuiz::class.java) { quizzes(course) }
    val assignments = canvasCachedList("quizAssignments:$course", token, CanvasAssignment::class.java) { assignments(course) }
    val tasks by assignments.state.collectAsState()
    val quizzes by vm.state.collectAsState()
    CanvasResourceFeedback(tasks) { assignments.refresh(true) }
    var selected by remember { mutableStateOf<CanvasQuiz?>(null) }
    CanvasReadList(vm, leading = {
        // Canvas does not expose a reliable New Quizzes flag in every account: label LTI conservatively.
        Column {
        tasks.data.filter { a -> (a.isQuizAssignment == true || a.submissionTypes.orEmpty().contains("external_tool")) && quizzes.data.none { it.id == a.quizId } }.forEach { a ->
            Text(a.name ?: "Actividad externa / posible New Quiz", Modifier.padding(horizontal = 12.dp))
            Text("Actividad externa / posible New Quiz · vence ${a.effectiveDueAt ?: "Sin fecha de entrega"}", Modifier.padding(horizontal = 12.dp))
            CanvasExternalButton(a.htmlUrl)
        }
        }
    }) { quiz ->
            TextButton(onClick = { selected = quiz }) { Text(quiz.title ?: "Quiz") }
            Text("Vence: ${quiz.effectiveDueAt ?: "Sin fecha de entrega"} · ${canvasPoints(quiz.pointsPossible)} pts")
            Text("Abre: ${quiz.unlockAt ?: "No especificado"} · Cierra: ${quiz.lockAt ?: "No especificado"}")
            Text("Intentos permitidos: ${canvasAttempts(quiz.allowedAttempts)}")
            CanvasExternalButton(quiz.htmlUrl)
    }
    selected?.let { quiz ->
        val attempts = canvasResourceViewModel("quizAttempts:$course:${quiz.id}", token, emptyList<CanvasQuizAttempt>()) { quizAttempts(course, quiz.id) }
        val state by attempts.state.collectAsState()
        CanvasResourceFeedback(state) { attempts.refresh(true) }
        CanvasReadDetail(quiz.title ?: "Quiz", { selected = null }) {
            HtmlText(quiz.description.orEmpty())
            if (state.loading) CircularProgressIndicator()
            state.data.forEach { a -> Text("Intento ${a.attempt ?: "No disponible"} · nota ${canvasPoints(a.score)} · conservada ${canvasPoints(a.keptScore)}"); Text("${a.startedAt.orEmpty()} — ${a.finishedAt ?: a.endAt.orEmpty()} · ${a.workflowState.orEmpty()}") }
            if (!state.loading) Text("Intentos usados: ${state.data.maxOfOrNull { it.attempt ?: 0 } ?: 0}")
            if (!state.loading && state.data.isEmpty()) Text("Sin intentos disponibles")
            CanvasExternalButton(quiz.htmlUrl)
        }
    }
}

@Composable
internal fun CanvasDiscussionDetail(course: Long, topic: CanvasDiscussionTopic, token: String, onClose: () -> Unit) {
    val vm = canvasResourceViewModel("discussion:$course:${topic.id}", token, null as CanvasDiscussionView?) { discussionView(course, topic.id) }
    val state by vm.state.collectAsState()
    CanvasResourceFeedback(state) { vm.refresh(true) }
    CanvasReadDetail(topic.title ?: "Discusión", onClose) {
        HtmlText(topic.message.orEmpty())
        if (state.loading) CircularProgressIndicator()
        state.data?.let { data -> CanvasDiscussionEntries(data.view.orEmpty(), data.participants.orEmpty()) }
        CanvasExternalButton(topic.htmlUrl, "Responder en Canvas")
    }
}

@Composable
private fun CanvasDiscussionEntries(entries: List<CanvasDiscussionEntry>, people: List<CanvasParticipant>, depth: Int = 0) {
    val rows = remember(entries) {
        val pending = java.util.ArrayDeque<Pair<Int, CanvasDiscussionEntry>>()
        entries.asReversed().forEach { pending.addFirst(depth to it) }
        buildList {
            while (pending.isNotEmpty()) {
                val next = pending.removeFirst()
                add(next)
                next.second.replies.orEmpty().asReversed().forEach { pending.addFirst((next.first + 1) to it) }
            }
        }
    }
    rows.forEach { (level, entry) ->
        Column(Modifier.padding(start = (level.coerceAtMost(6) * 12).dp)) {
            HorizontalDivider()
            Text("${people.firstOrNull { it.id == entry.userId }?.let { it.displayName ?: it.name } ?: "Autor no disponible"} · ${entry.createdAt.orEmpty()}")
            if (entry.deleted == true) Text("Entrada eliminada") else HtmlText(entry.message.orEmpty())
            if (entry.hasMoreReplies == true) Text("Canvas indica que hay más respuestas. Abrir el hilo en Canvas.")
        }
    }
}

@Composable
internal fun CanvasFolders(course: Long, token: String) {
    val context = LocalContext.current
    val vm = canvasCachedList("folders:$course", token, CanvasFolder::class.java) { folders(course) }
    var selected by remember { mutableStateOf<CanvasFolder?>(null) }
    var metadata by remember { mutableStateOf<CanvasFileDetail?>(null) }
    CanvasReadList(vm) { folder ->
        TextButton(onClick = { selected = folder }, enabled = folder.id != null) { Text(folder.name ?: "Carpeta") }
        Text("${folder.filesCount ?: "No disponible"} archivos · carpeta superior ${folder.parentFolderId ?: "Raíz"}")
    }
    selected?.let { folder ->
        val files = canvasCachedList("folderFiles:${folder.id}", token, CanvasFileDetail::class.java) { folderFiles(folder.id!!) }
        CanvasReadDetail(folder.name ?: "Carpeta", { selected = null }) {
            val state by files.state.collectAsState()
            CanvasResourceFeedback(state) { files.refresh(true) }
            if (state.loading) CircularProgressIndicator()
            state.data.forEach { file -> TextButton(onClick = { metadata = file }) { Text(file.fileName) } }
        }
    }
    metadata?.let { initial ->
        val vmFile = canvasResourceViewModel("courseFile:$course:${initial.id}", token, initial) { file(course, initial.id) }
        val state by vmFile.state.collectAsState()
        CanvasResourceFeedback(state) { vmFile.refresh(true) }
        CanvasReadDetail(state.data.fileName, { metadata = null }) {
            Text("${state.data.formattedSize} · ${state.data.contentType.orEmpty()}")
            Text("Actualizado: ${state.data.updatedAt ?: "No disponible"}")
            TextButton(onClick = { openCanvasContentLink(context, state.data.url) }, enabled = state.data.url != null) { Text("Descargar") }
        }
    }
}

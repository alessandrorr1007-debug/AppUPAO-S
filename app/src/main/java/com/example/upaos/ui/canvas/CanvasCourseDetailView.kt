package com.example.upaos.ui.canvas

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.upaos.data.api.CanvasApiClient
import com.example.upaos.data.model.CanvasAssignment
import com.example.upaos.data.model.CanvasCourse
import com.example.upaos.data.model.CanvasDiscussionTopic
import com.example.upaos.data.model.CanvasFileDetail
import com.example.upaos.data.model.CanvasGroup
import com.example.upaos.data.model.CanvasModule
import com.example.upaos.data.model.CanvasModuleItem
import com.example.upaos.data.model.CanvasNotificationItem
import com.example.upaos.data.model.CanvasQuiz
import com.example.upaos.data.model.CanvasTab
import com.example.upaos.data.model.CanvasTodoItem
import com.example.upaos.data.model.CanvasUser
import com.example.upaos.data.model.CourseZoomMeeting
import com.example.upaos.ui.components.AppCard
import com.example.upaos.ui.components.EmptyState
import com.example.upaos.ui.components.ModernTextField
import com.example.upaos.ui.components.RefreshableContent
import com.example.upaos.ui.components.cursoColor
import com.example.upaos.ui.components.toTitleCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasCourseDetailView(
    course: CanvasCourse,
    token: String,
    onBack: () -> Unit,
    onOpenAssignment: (CanvasTodoItem) -> Unit,
    onOpenGroup: (CanvasGroup) -> Unit,
    onOpenFile: (CanvasModuleItem, String) -> Unit,
    onOpenUrl: (String, String) -> Unit,
    todoItems: List<CanvasTodoItem>,
    notifications: List<CanvasNotificationItem>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val courseAccentColor = remember(course.name) { cursoColor(course.name) }

    // Interceptar botón atrás físico para volver a la lista de cursos
    BackHandler { onBack() }

    // 9 Secciones del Curso:
    // 0: Inicio, 1: Módulos, 2: Personas, 3: Grupos, 4: Calificaciones,
    // 5: Foro de discusión, 6: Archivos, 7: Programa de curso, 8: Zoom
    var selectedSection by remember { mutableIntStateOf(0) }

    val sections = remember {
        listOf(
            Triple(0, "Inicio", Icons.Filled.Home),
            Triple(1, "Módulos", Icons.Filled.Folder),
            Triple(2, "Personas", Icons.Filled.Person),
            Triple(3, "Grupos", Icons.Filled.Groups),
            Triple(4, "Calificaciones", Icons.Filled.Star),
            Triple(5, "Foro de discusión", Icons.Filled.Forum),
            Triple(6, "Archivos", Icons.AutoMirrored.Filled.InsertDriveFile),
            Triple(7, "Programa de curso", Icons.Filled.Description),
            Triple(8, "Zoom", Icons.Filled.Videocam)
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Top Bar del Curso
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver a mis cursos",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = course.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = course.courseCode ?: "Curso activo",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!course.nrcNumber.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = courseAccentColor.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "NRC ${course.nrcNumber}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = courseAccentColor,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Navegación de Secciones (ScrollableTabRow)
        ScrollableTabRow(
            selectedTabIndex = selectedSection,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 12.dp,
            divider = {}
        ) {
            sections.forEach { (index, title, icon) ->
                Tab(
                    selected = selectedSection == index,
                    onClick = { selectedSection = index },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(title, fontSize = 12.sp, fontWeight = if (selectedSection == index) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // Contenido de la Sección seleccionada
        Box(modifier = Modifier.weight(1f)) {
            when (selectedSection) {
                0 -> CourseHomeSection(
                    course = course,
                    courseColor = courseAccentColor,
                    todoItems = todoItems,
                    notifications = notifications,
                    onNavigateToSection = { selectedSection = it },
                    onOpenAssignment = onOpenAssignment,
                    onOpenUrl = onOpenUrl
                )
                1 -> CourseModulesSection(
                    courseId = course.id,
                    token = token,
                    onItemClick = { item, modName ->
                        if (item.isFile) {
                            onOpenFile(item, modName)
                        } else if (!item.htmlUrl.isNullOrBlank()) {
                            onOpenUrl(item.htmlUrl, item.title ?: "Módulo")
                        }
                    }
                )
                2 -> CoursePeopleSection(
                    courseId = course.id,
                    token = token,
                    onNavigateToGroups = { selectedSection = 3 }
                )
                3 -> CourseGroupsSection(
                    courseId = course.id,
                    token = token,
                    onOpenGroup = onOpenGroup
                )
                4 -> CourseGradesSection(
                    course = course,
                    token = token,
                    onOpenAssignment = onOpenAssignment
                )
                5 -> CourseDiscussionsSection(
                    courseId = course.id,
                    token = token,
                    onOpenTopic = { topic ->
                        if (!topic.htmlUrl.isNullOrBlank()) {
                            onOpenUrl(topic.htmlUrl, topic.title ?: "Foro de discusión")
                        }
                    }
                )
                6 -> CourseFilesSection(
                    courseId = course.id,
                    token = token,
                    onOpenFile = { file ->
                        if (!file.url.isNullOrBlank()) {
                            downloadCanvasFileInDetail(context, file.url, file.fileName)
                        }
                    }
                )
                7 -> CourseSyllabusSection(
                    course = course,
                    token = token,
                    onOpenAssignment = onOpenAssignment
                )
                8 -> CourseZoomSection(
                    course = course,
                    token = token,
                    onOpenUrl = onOpenUrl
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 0. PÁGINA DE INICIO DEL CURSO
// -------------------------------------------------------------
@Composable
private fun CourseHomeSection(
    course: CanvasCourse,
    courseColor: Color,
    todoItems: List<CanvasTodoItem>,
    notifications: List<CanvasNotificationItem>,
    onNavigateToSection: (Int) -> Unit,
    onOpenAssignment: (CanvasTodoItem) -> Unit,
    onOpenUrl: (String, String) -> Unit
) {
    // Filtrar tareas pendientes de este curso
    val courseTodos = remember(todoItems, course.id) {
        todoItems.filter {
            it.courseId == course.id ||
            it.contextName?.contains(course.name, ignoreCase = true) == true ||
            course.name.contains(it.courseDisplayName, ignoreCase = true)
        }
    }

    // Filtrar notificaciones de este curso
    val courseNotifs = remember(notifications, course.id) {
        notifications.filter {
            it.courseId == course.id ||
            it.courseName?.contains(course.name, ignoreCase = true) == true
        }.take(3)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Banner Hero del Curso
        item {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentPadding = PaddingValues(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = courseColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.School,
                                contentDescription = null,
                                tint = courseColor,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = course.displayTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${course.courseCode ?: "UPAO"} • Periodo Académico Activo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Acceso directo a Clase Virtual Zoom
        item {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF2D8CFF).copy(alpha = 0.10f),
                contentPadding = PaddingValues(14.dp),
                onClick = { onNavigateToSection(8) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF2D8CFF),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Videocam,
                                contentDescription = "Zoom",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Clase Virtual (Zoom)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Ver enlace, unirse a la reunión o ingresar ID",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color(0xFF2D8CFF),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Accesos rápidos a secciones
        item {
            Text(
                text = "Secciones del curso",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))

            val quickActions = listOf(
                Triple("Módulos", Icons.Filled.Folder, 1),
                Triple("Personas", Icons.Filled.Person, 2),
                Triple("Grupos", Icons.Filled.Groups, 3),
                Triple("Calificaciones", Icons.Filled.Star, 4),
                Triple("Foro de discusión", Icons.Filled.Forum, 5),
                Triple("Archivos", Icons.AutoMirrored.Filled.InsertDriveFile, 6),
                Triple("Programa de curso", Icons.Filled.Description, 7),
                Triple("Zoom", Icons.Filled.Videocam, 8)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                quickActions.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { (label, icon, sectionIdx) ->
                            AppCard(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(10.dp),
                                onClick = { onNavigateToSection(sectionIdx) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Tareas pendientes de este curso
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Tareas pendientes de este curso",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (courseTodos.isNotEmpty()) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Text("${courseTodos.size}", fontSize = 10.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (courseTodos.isEmpty()) {
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "¡Al día! No tienes tareas pendientes en este curso.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    courseTodos.forEach { todo ->
                        AppCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(12.dp),
                            onClick = { onOpenAssignment(todo) }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (todo.isQuiz) Icons.Filled.Quiz else Icons.AutoMirrored.Filled.Assignment,
                                    contentDescription = null,
                                    tint = if (todo.isQuiz) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = todo.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val dueText = todo.dueAt?.let { formatRelativeDateText(it) } ?: "Sin fecha límite"
                                    Text(
                                        text = dueText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Avisos recientes de este curso
        if (courseNotifs.isNotEmpty()) {
            item {
                Text(
                    text = "Avisos recientes del curso",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    courseNotifs.forEach { notif ->
                        AppCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(10.dp),
                            onClick = {
                                if (!notif.htmlUrl.isNullOrBlank()) {
                                    onOpenUrl(notif.htmlUrl, notif.title ?: "Aviso")
                                }
                            }
                        ) {
                            Column {
                                Text(
                                    text = notif.title ?: "Aviso de Canvas",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!notif.message.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = notif.message,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

// -------------------------------------------------------------
// 1. SECCIÓN DE MÓDULOS DEL CURSO
// -------------------------------------------------------------
@Composable
private fun CourseModulesSection(
    courseId: Long,
    token: String,
    onItemClick: (CanvasModuleItem, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var modules by remember { mutableStateOf<List<CanvasModule>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    fun loadModules(isManual: Boolean = false) {
        scope.launch {
            if (isManual) isRefreshing = true else isLoading = true
            try {
                val resp = withContext(Dispatchers.IO) {
                    CanvasApiClient.pagedApiService.getCourseModules("Bearer $token", courseId)
                }
                if (resp.isSuccessful && resp.body() != null) {
                    modules = resp.body()!!
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { android.util.Log.w("CanvasDetail", "No se pudo actualizar la sección de Canvas") }
            finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(courseId) {
        loadModules()
    }

    RefreshableContent(
        isRefreshing = isRefreshing,
        onRefresh = { loadModules(isManual = true) },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
                modules.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Filled.Folder,
                            title = "Sin módulos publicados",
                            subtitle = "Este curso aún no tiene módulos con materiales publicados en Canvas."
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(modules, key = { it.id }) { module ->
                            CanvasModuleCard(
                                module = module,
                                onItemClick = { item -> onItemClick(item, module.name) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 2. SECCIÓN DE CALIFICACIONES (NOTAS)
// -------------------------------------------------------------
@Composable
private fun CourseGradesSection(
    course: CanvasCourse,
    token: String,
    onOpenAssignment: (CanvasTodoItem) -> Unit
) {
    val scope = rememberCoroutineScope()
    var assignments by remember { mutableStateOf<List<CanvasAssignment>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    fun loadGrades(isManual: Boolean = false) {
        scope.launch {
            if (isManual) isRefreshing = true else isLoading = true
            try {
                val resp = withContext(Dispatchers.IO) {
                    CanvasApiClient.pagedApiService.getCourseAssignments("Bearer $token", course.id)
                }
                if (resp.isSuccessful && resp.body() != null) {
                    assignments = resp.body()!!
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { android.util.Log.w("CanvasDetail", "No se pudo actualizar la sección de Canvas") }
            finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(course.id) {
        loadGrades()
    }

    RefreshableContent(
        isRefreshing = isRefreshing,
        onRefresh = { loadGrades(isManual = true) },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
                assignments.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Filled.Star,
                            title = "Sin calificaciones registradas",
                            subtitle = "No se encontraron tareas ni notas evaluadas en este curso."
                        )
                    }
                }
                else -> {
                    // Métricas de calificaciones
                    val gradedAssignments = remember(assignments) {
                        assignments.filter { it.submission?.score != null || !it.submission?.grade.isNullOrBlank() }
                    }
                    val submittedAssignments = remember(assignments) {
                        assignments.filter { it.isSubmitted }
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Tarjeta de resumen de notas
                        item {
                            AppCard(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentPadding = PaddingValues(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "${assignments.size}",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "Total Tareas",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "${submittedAssignments.size}",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "Entregadas",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "${gradedAssignments.size}",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "Calificadas",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }

                        // Lista de tareas y notas
                        items(assignments, key = { it.id }) { assignment ->
                            val submission = assignment.submission
                            val hasGrade = submission?.score != null || !submission?.grade.isNullOrBlank()
                            val isSubmitted = assignment.isSubmitted

                            AppCard(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(12.dp),
                                onClick = {
                                    val todo = CanvasTodoItem(
                                        type = "submitting",
                                        contextType = "Course",
                                        courseId = course.id,
                                        contextName = course.name,
                                        contextShortName = course.courseCode,
                                        htmlUrl = assignment.htmlUrl,
                                        assignment = assignment,
                                        quiz = if (assignment.isQuizAssignment == true || assignment.quizId != null) {
                                            CanvasQuiz(
                                                id = assignment.quizId ?: assignment.id,
                                                title = assignment.name,
                                                dueAt = assignment.effectiveDueAt,
                                                lockAt = assignment.lockAt,
                                                unlockAt = assignment.unlockAt,
                                                pointsPossible = assignment.pointsPossible,
                                                htmlUrl = assignment.htmlUrl
                                            )
                                        } else null
                                    )
                                    onOpenAssignment(todo)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = assignment.name ?: "Tarea sin título",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val dueText = assignment.effectiveDueAt?.let { formatRelativeDateText(it) } ?: "Sin fecha límite"
                                        Text(
                                            text = dueText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        if (assignment.pointsPossible != null && assignment.pointsPossible > 0) {
                                            Text(
                                                text = "Puntaje máximo: ${assignment.pointsPossible.toInt()} pts",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Badge de Nota / Estado
                                    when {
                                        hasGrade -> {
                                            val scoreVal = submission?.score
                                            val gradeText = submission?.grade ?: scoreVal?.toString() ?: "-"
                                            val maxPts = assignment.pointsPossible?.toInt() ?: 20
                                            val isPassing = (scoreVal ?: 0.0) >= (maxPts * 0.55)

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isPassing) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                                modifier = Modifier.padding(2.dp)
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text(
                                                        text = gradeText,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 16.sp,
                                                        color = if (isPassing) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                                    )
                                                    Text(
                                                        text = "de $maxPts pts",
                                                        fontSize = 10.sp,
                                                        color = if (isPassing) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                                    )
                                                }
                                            }
                                        }
                                        isSubmitted -> {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFFE3F2FD)
                                            ) {
                                                Text(
                                                    text = "Entregado",
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF1976D2),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                        else -> {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Text(
                                                    text = "Sin entregar",
                                                    fontWeight = FontWeight.Normal,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 2. SECCIÓN DE PERSONAS (DOCENTES Y ESTUDIANTES)
// -------------------------------------------------------------
@Composable
private fun CoursePeopleSection(
    courseId: Long,
    token: String,
    onNavigateToGroups: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<CanvasUser>>(emptyList()) }
    var isLoadingUsers by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(courseId) {
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    CanvasApiClient.pagedApiService.getCourseUsers("Bearer $token", courseId)
                }
                if (resp.isSuccessful && resp.body() != null) {
                    users = resp.body()!!
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { android.util.Log.w("CanvasDetail", "No se pudo actualizar la sección de Canvas") }
            finally {
                isLoadingUsers = false
            }
        }
    }

    val filteredUsers = remember(users, searchQuery) {
        if (searchQuery.isBlank()) users
        else users.filter { it.name?.contains(searchQuery, ignoreCase = true) == true }
    }
    val teachers = remember(filteredUsers) { filteredUsers.filter { it.isTeacher } }
    val students = remember(filteredUsers) { filteredUsers.filter { !it.isTeacher } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Acceso rápido a grupos
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToGroups() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Groups,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "¿Buscas tus equipos de trabajo? Ir a pestaña Grupos",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Buscador de personas
        ModernTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = "Buscar personas (${users.size})",
            placeholder = "Buscar docente o estudiante...",
            leadingIcon = Icons.Filled.Search,
            trailingIcon = if (searchQuery.isNotBlank()) {
                {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Limpiar", modifier = Modifier.size(16.dp))
                    }
                }
            } else null,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoadingUsers) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        } else if (filteredUsers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.Person,
                    title = "No se encontraron personas",
                    subtitle = if (searchQuery.isNotBlank()) "Ningún resultado para '$searchQuery'" else "La lista de personas del curso está vacía."
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Sección Docentes
                if (teachers.isNotEmpty()) {
                    item {
                        Text(
                            text = "Docentes (${teachers.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(teachers, key = { "teacher_${it.id}" }) { teacher ->
                        UserItemCard(user = teacher, isTeacher = true)
                    }
                }

                // Sección Estudiantes
                if (students.isNotEmpty()) {
                    item {
                        Text(
                            text = "Estudiantes (${students.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                        )
                    }
                    items(students, key = { "student_${it.id}" }) { student ->
                        UserItemCard(user = student, isTeacher = false)
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

// -------------------------------------------------------------
// 3. SECCIÓN DE GRUPOS DEL CURSO
// -------------------------------------------------------------
@Composable
private fun CourseGroupsSection(
    courseId: Long,
    token: String,
    onOpenGroup: (CanvasGroup) -> Unit
) {
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf<List<CanvasGroup>>(emptyList()) }
    var isLoadingGroups by remember { mutableStateOf(true) }

    LaunchedEffect(courseId) {
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    CanvasApiClient.pagedApiService.getCourseGroups("Bearer $token", courseId)
                }
                if (resp.isSuccessful && resp.body() != null) {
                    groups = resp.body()!!
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { android.util.Log.w("CanvasDetail", "No se pudo actualizar la sección de Canvas") }
            finally {
                isLoadingGroups = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        if (isLoadingGroups) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        } else if (groups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.Groups,
                    title = "Sin grupos creados",
                    subtitle = "Aún no se han configurado equipos ni grupos de trabajo para este curso en Canvas."
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Text(
                        text = "Grupos y Equipos de Trabajo (${groups.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(groups, key = { it.id }) { group ->
                    CanvasGroupCard(
                        group = group,
                        onClick = { onOpenGroup(group) }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun UserItemCard(
    user: CanvasUser,
    isTeacher: Boolean
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isTeacher) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (isTeacher) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = user.name?.firstOrNull()?.uppercase() ?: "U",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (isTeacher) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = toTitleCase(user.name ?: "Usuario"),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isTeacher) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!user.email.isNullOrBlank()) {
                    Text(
                        text = user.email,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isTeacher) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "Docente",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 4. SECCIÓN DE FORO DE DISCUSIÓN
// -------------------------------------------------------------
@Composable
private fun CourseDiscussionsSection(
    courseId: Long,
    token: String,
    onOpenTopic: (CanvasDiscussionTopic) -> Unit
) {
    val scope = rememberCoroutineScope()
    var topics by remember { mutableStateOf<List<CanvasDiscussionTopic>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    fun loadDiscussions(isManual: Boolean = false) {
        scope.launch {
            if (isManual) isRefreshing = true else isLoading = true
            try {
                val resp = withContext(Dispatchers.IO) {
                    CanvasApiClient.pagedApiService.getCourseDiscussions("Bearer $token", courseId)
                }
                if (resp.isSuccessful && resp.body() != null) {
                    topics = resp.body()!!
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { android.util.Log.w("CanvasDetail", "No se pudo actualizar la sección de Canvas") }
            finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(courseId) {
        loadDiscussions()
    }

    RefreshableContent(
        isRefreshing = isRefreshing,
        onRefresh = { loadDiscussions(isManual = true) },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
                topics.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Filled.Forum,
                            title = "Sin foros abiertos",
                            subtitle = "No hay temas ni foros de discusión activos en este curso."
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(topics, key = { it.id }) { topic ->
                            AppCard(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(12.dp),
                                onClick = { onOpenTopic(topic) }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.Forum,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = topic.title ?: "Tema de discusión",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        val authorText = topic.userName ?: topic.author?.name ?: "Profesor"
                                        Text(
                                            text = "Por $authorText",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    val count = topic.repliesCount ?: 0
                                    if (count > 0) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        ) {
                                            Text("$count", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 5. SECCIÓN DE ARCHIVOS DEL CURSO
// -------------------------------------------------------------
@Composable
private fun CourseFilesSection(
    courseId: Long,
    token: String,
    onOpenFile: (CanvasFileDetail) -> Unit
) {
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<CanvasFileDetail>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    fun loadFiles(isManual: Boolean = false) {
        scope.launch {
            if (isManual) isRefreshing = true else isLoading = true
            try {
                val resp = withContext(Dispatchers.IO) {
                    CanvasApiClient.pagedApiService.getCourseFiles("Bearer $token", courseId)
                }
                if (resp.isSuccessful && resp.body() != null) {
                    files = resp.body()!!
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { android.util.Log.w("CanvasDetail", "No se pudo actualizar la sección de Canvas") }
            finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(courseId) {
        loadFiles()
    }

    RefreshableContent(
        isRefreshing = isRefreshing,
        onRefresh = { loadFiles(isManual = true) },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // Buscador de archivos
            ModernTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = "Buscar archivos",
                placeholder = "Buscar archivo por nombre...",
                leadingIcon = Icons.Filled.Search,
                trailingIcon = if (searchQuery.isNotBlank()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Limpiar", modifier = Modifier.size(16.dp))
                        }
                    }
                } else null,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            val filteredFiles = remember(files, searchQuery) {
                if (searchQuery.isBlank()) files
                else files.filter { it.fileName.contains(searchQuery, ignoreCase = true) }
            }

            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
                filteredFiles.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                            title = "Sin archivos",
                            subtitle = if (searchQuery.isNotBlank()) "Ningún archivo coincide con '$searchQuery'." else "No hay archivos subidos en este curso."
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredFiles, key = { it.id }) { file ->
                            val isPdf = file.fileName.endsWith(".pdf", ignoreCase = true)
                            val iconColor = if (isPdf) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary

                            AppCard(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(10.dp),
                                onClick = { onOpenFile(file) }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isPdf) Icons.Filled.PictureAsPdf else Icons.AutoMirrored.Filled.InsertDriveFile,
                                        contentDescription = null,
                                        tint = iconColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.fileName,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (file.formattedSize.isNotBlank()) {
                                            Text(
                                                text = file.formattedSize,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    IconButton(onClick = { onOpenFile(file) }) {
                                        Icon(
                                            imageVector = Icons.Filled.Download,
                                            contentDescription = "Descargar archivo",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 6. SECCIÓN DE PROGRAMA DE CURSO (SÍLABO)
// -------------------------------------------------------------
@Composable
private fun CourseSyllabusSection(
    course: CanvasCourse,
    token: String,
    onOpenAssignment: (CanvasTodoItem) -> Unit
) {
    val scope = rememberCoroutineScope()
    var courseDetail by remember { mutableStateOf<CanvasCourse?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(course.id) {
        try {
            val resp = withContext(Dispatchers.IO) {
                CanvasApiClient.apiService.getCourseDetail("Bearer $token", course.id)
            }
            if (resp.isSuccessful && resp.body() != null) {
                courseDetail = resp.body()!!
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { android.util.Log.w("CanvasDetail", "No se pudo actualizar la sección de Canvas") }
        finally {
            isLoading = false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(14.dp)
            ) {
                Column {
                    Text(
                        text = "Programa y Sílabo del Curso",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${course.name}\nCódigo: ${course.courseCode ?: "UPAO"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            } else {
                val body = courseDetail?.syllabusBody
                if (!body.isNullOrBlank()) {
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        HtmlText(html = body)
                    }
                } else {
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "El docente no ha incluido un sílabo en texto directo para este curso.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Puedes encontrar el archivo PDF del sílabo en la sección 'Archivos' o en los 'Módulos'.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

// -------------------------------------------------------------
// TARJETA DE CURSO (LISTA PRINCIPAL DE CURSOS - TAB 0)
// -------------------------------------------------------------
@Composable
fun CanvasCourseCard(
    course: CanvasCourse,
    onClick: () -> Unit
) {
    val accentColor = remember(course.name) { cursoColor(course.name) }

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icono con color característico del curso
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(alpha = 0.15f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.School,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = course.courseCode ?: "UPAO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!course.nrcNumber.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = accentColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "NRC ${course.nrcNumber}",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Entrar al curso",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// Helpers locales para fechas y descargas
private fun formatRelativeDateText(iso: String?): String {
    if (iso.isNullOrBlank()) return "Sin fecha"
    return try {
        val base = if (iso.contains(".")) iso.substringBefore(".") + "Z" else iso
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val date = fmt.parse(base) ?: return "Sin fecha"
        val outFmt = SimpleDateFormat("d 'de' MMM, h:mm a", Locale.forLanguageTag("es-ES"))
        "Vence: ${outFmt.format(date)}"
    } catch (_: Exception) {
        "Sin fecha"
    }
}

private fun downloadCanvasFileInDetail(context: Context, url: String, fileName: String) {
    try {
        val request = android.app.DownloadManager.Request(android.net.Uri.parse(url)).apply {
            setTitle(fileName)
            setDescription("Descargando archivo de Canvas LMS")
            setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        dm.enqueue(request)
        android.widget.Toast.makeText(context, "Iniciando descarga: $fileName", android.widget.Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Error al descargar: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

// -------------------------------------------------------------
// 8. SECCIÓN DE ZOOM PARA EL CURSO
// -------------------------------------------------------------

object CourseZoomPreferences {
    private const val PREFS_NAME = "course_zoom_preferences"

    fun getCustomZoomMeeting(context: Context, courseId: Long): CourseZoomMeeting? {
        val prefs = context.getSharedPreferences("${PREFS_NAME}_${com.example.upaos.data.local.TokenManager(context).getSavedUser() ?: "anonymous"}", Context.MODE_PRIVATE)
        // Migrate the old course preference on first use, preserving saved meeting links.
        if (!prefs.contains("zoom_url_$courseId")) {
            val legacy = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (legacy.contains("zoom_url_$courseId")) {
                val editor = prefs.edit()
                val oldEditor = legacy.edit()
                listOf("zoom_url", "zoom_title", "zoom_id", "zoom_pwd").forEach { prefix ->
                    val key = "${prefix}_$courseId"
                    legacy.getString(key, null)?.let { editor.putString(key, it) }
                    oldEditor.remove(key)
                }
                if (editor.commit()) oldEditor.commit()
            }
        }
        val url = prefs.getString("zoom_url_$courseId", null) ?: return null
        val title = prefs.getString("zoom_title_$courseId", "Clase Virtual Zoom") ?: "Clase Virtual Zoom"
        val meetingId = prefs.getString("zoom_id_$courseId", null)
        val passcode = prefs.getString("zoom_pwd_$courseId", null)
        return CourseZoomMeeting(
            courseId = courseId,
            title = title,
            joinUrl = url,
            meetingId = meetingId,
            passcode = passcode,
            origin = "CUSTOM"
        )
    }

    fun saveCustomZoomMeeting(context: Context, meeting: CourseZoomMeeting) {
        val prefs = context.getSharedPreferences("${PREFS_NAME}_${com.example.upaos.data.local.TokenManager(context).getSavedUser() ?: "anonymous"}", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("zoom_url_${meeting.courseId}", meeting.joinUrl)
            .putString("zoom_title_${meeting.courseId}", meeting.title)
            .putString("zoom_id_${meeting.courseId}", meeting.meetingId)
            .putString("zoom_pwd_${meeting.courseId}", meeting.passcode)
            .apply()
    }

    fun deleteCustomZoomMeeting(context: Context, courseId: Long) {
        val prefs = context.getSharedPreferences("${PREFS_NAME}_${com.example.upaos.data.local.TokenManager(context).getSavedUser() ?: "anonymous"}", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("zoom_url_$courseId")
            .remove("zoom_title_$courseId")
            .remove("zoom_id_$courseId")
            .remove("zoom_pwd_$courseId")
            .apply()
    }
}

object ZoomUrlExtractor {
    private val ZOOM_URL_REGEX = """https?://[a-zA-Z0-9.\-_]*zoom\.us/[jsw]/[0-9]+[^\s<>"']*""".toRegex(RegexOption.IGNORE_CASE)
    private val MEETING_ID_REGEX = """(?:ID(?:\s*de\s*reuni[oó]n)?|Meeting\s*ID)\s*[:：]?\s*([0-9\s]{9,14})""".toRegex(RegexOption.IGNORE_CASE)
    private val PASSCODE_REGEX = """(?:C[oó]digo(?:\s*de\s*acceso)?|Contrase[ñn]a|Clave|Passcode|Password)\s*[:：]?\s*([A-Za-z0-9@#*_\-]+)""".toRegex(RegexOption.IGNORE_CASE)

    fun extractZoomMeetingsFromText(
        text: String,
        courseId: Long,
        defaultTitle: String,
        origin: String
    ): List<CourseZoomMeeting> {
        val matches = ZOOM_URL_REGEX.findAll(text).map { it.value.trim() }.distinct().toList()
        if (matches.isEmpty()) return emptyList()

        val foundMeetingId = MEETING_ID_REGEX.find(text)?.groupValues?.getOrNull(1)?.replace(" ", "")?.trim()
        val foundPasscode = PASSCODE_REGEX.find(text)?.groupValues?.getOrNull(1)?.trim()

        return matches.map { url ->
            val idFromUrl = if (foundMeetingId.isNullOrBlank()) {
                """zoom\.us/[jsw]/([0-9]+)""".toRegex().find(url)?.groupValues?.getOrNull(1)
            } else foundMeetingId

            val pwdFromUrl = if (foundPasscode.isNullOrBlank()) {
                """[?&]pwd=([^&#]+)""".toRegex().find(url)?.groupValues?.getOrNull(1)
            } else foundPasscode

            CourseZoomMeeting(
                courseId = courseId,
                title = defaultTitle,
                joinUrl = url,
                meetingId = idFromUrl,
                passcode = pwdFromUrl,
                origin = origin
            )
        }
    }
}

private fun launchZoomMeetingIntent(context: Context, url: String) {
    try {
        val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("zoomus://")) {
            "https://$url"
        } else {
            url
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No se pudo abrir Zoom: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun copyZoomText(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copiado al portapapeles", Toast.LENGTH_SHORT).show()
}

@Composable
fun CourseZoomSection(
    course: CanvasCourse,
    token: String,
    onOpenUrl: (String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var zoomMeetings by remember { mutableStateOf<List<CourseZoomMeeting>>(emptyList()) }
    var canvasLtiTabUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showDialog by remember { mutableStateOf(false) }

    fun refreshMeetings() {
        isLoading = true
        scope.launch {
            val foundMeetings = mutableListOf<CourseZoomMeeting>()
            val custom = CourseZoomPreferences.getCustomZoomMeeting(context, course.id)
            if (custom != null) {
                foundMeetings.add(custom)
            }

            try {
                // 1. Consultar pestañas de Canvas
                val tabsResp = withContext(Dispatchers.IO) {
                    CanvasApiClient.pagedApiService.getCourseTabs("Bearer $token", course.id)
                }
                if (tabsResp.isSuccessful && tabsResp.body() != null) {
                    val zoomTab = tabsResp.body()!!.firstOrNull {
                        it.label?.contains("zoom", ignoreCase = true) == true ||
                        it.id.contains("zoom", ignoreCase = true)
                    }
                    if (zoomTab != null) {
                        val full = zoomTab.fullUrl ?: if (!zoomTab.htmlUrl.isNullOrBlank()) {
                            if (zoomTab.htmlUrl.startsWith("http")) zoomTab.htmlUrl
                            else "https://upao.instructure.com${zoomTab.htmlUrl}"
                        } else null
                        canvasLtiTabUrl = full
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { android.util.Log.w("CanvasDetail", "No se pudo actualizar la sección de Canvas") }

            try {
                // 2. Escanear módulos de Canvas
                val modResp = withContext(Dispatchers.IO) {
                    CanvasApiClient.pagedApiService.getCourseModules("Bearer $token", course.id)
                }
                if (modResp.isSuccessful && modResp.body() != null) {
                    modResp.body()!!.forEach { mod ->
                        mod.items?.forEach { item ->
                            val titleMatches = item.title?.contains("zoom", ignoreCase = true) == true
                            val urlMatches = item.url?.contains("zoom.us", ignoreCase = true) == true ||
                                            item.htmlUrl?.contains("zoom.us", ignoreCase = true) == true
                            val targetUrl = item.url ?: item.htmlUrl
                            if ((titleMatches || urlMatches) && !targetUrl.isNullOrBlank()) {
                                val extracted = ZoomUrlExtractor.extractZoomMeetingsFromText(
                                    targetUrl, course.id, item.title ?: "Enlace Zoom en ${mod.name}", "MODULE"
                                )
                                if (extracted.isNotEmpty()) {
                                    foundMeetings.addAll(extracted)
                                } else if (targetUrl.contains("zoom", ignoreCase = true)) {
                                    foundMeetings.add(
                                        CourseZoomMeeting(
                                            courseId = course.id,
                                            title = item.title ?: "Enlace Zoom (${mod.name})",
                                            joinUrl = targetUrl,
                                            origin = "MODULE"
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { android.util.Log.w("CanvasDetail", "No se pudo actualizar la sección de Canvas") }

            try {
                // 3. Escanear discusiones y anuncios
                val discResp = withContext(Dispatchers.IO) {
                    CanvasApiClient.pagedApiService.getCourseDiscussions("Bearer $token", course.id)
                }
                if (discResp.isSuccessful && discResp.body() != null) {
                    discResp.body()!!.forEach { topic ->
                        val textToScan = "${topic.title.orEmpty()} \n ${topic.message.orEmpty()}"
                        if (textToScan.contains("zoom", ignoreCase = true)) {
                            val extracted = ZoomUrlExtractor.extractZoomMeetingsFromText(
                                textToScan, course.id, topic.title ?: "Aviso con Zoom", "ANNOUNCEMENT"
                            )
                            foundMeetings.addAll(extracted)
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { android.util.Log.w("CanvasDetail", "No se pudo actualizar la sección de Canvas") }

            try {
                // 4. Escanear syllabus si tiene enlace de Zoom
                if (!course.syllabusBody.isNullOrBlank() && course.syllabusBody.contains("zoom", ignoreCase = true)) {
                    val extracted = ZoomUrlExtractor.extractZoomMeetingsFromText(
                        course.syllabusBody, course.id, "Zoom en Sílabo", "SYLLABUS"
                    )
                    foundMeetings.addAll(extracted)
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { android.util.Log.w("CanvasDetail", "No se pudo actualizar la sección de Canvas") }

            // Deduplicar por URL
            val uniqueMeetings = mutableListOf<CourseZoomMeeting>()
            val seenUrls = mutableSetOf<String>()
            foundMeetings.forEach { meeting ->
                val normUrl = meeting.joinUrl.trim().lowercase()
                if (normUrl !in seenUrls) {
                    seenUrls.add(normUrl)
                    uniqueMeetings.add(meeting)
                }
            }

            zoomMeetings = uniqueMeetings
            isLoading = false
        }
    }

    LaunchedEffect(course.id) {
        refreshMeetings()
    }

    val zoomPrimaryColor = Color(0xFF2D8CFF)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Banner Superior Zoom
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            color = zoomPrimaryColor.copy(alpha = 0.08f),
            contentPadding = PaddingValues(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = zoomPrimaryColor,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Videocam,
                            contentDescription = "Zoom",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Clase Virtual (Zoom)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = course.displayTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { showDialog = true }) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Configurar enlace",
                        tint = zoomPrimaryColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp), color = zoomPrimaryColor)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Buscando enlace de Zoom en anuncios, módulos y Canvas...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (zoomMeetings.isEmpty() && canvasLtiTabUrl.isNullOrBlank()) {
            // Estado vacío con llamada a la acción
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Videocam,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No se detectó enlace automático de Zoom",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Si tu docente compartió el link de Zoom por correo, WhatsApp o mensaje, puedes guardarlo aquí para entrar con 1 solo toque siempre que tengas clase.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = zoomPrimaryColor),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Agregar enlace de Zoom")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            onOpenUrl(
                                "https://upao.instructure.com/courses/${course.id}",
                                "Canvas - ${course.displayTitle}"
                            )
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Abrir curso en Canvas Web")
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Si hay enlace oficial LTI de Canvas
                if (!canvasLtiTabUrl.isNullOrBlank()) {
                    item {
                        AppCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.School,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Zoom Oficial en Canvas (LTI)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Este curso cuenta con la herramienta de Zoom integrada en Canvas.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { onOpenUrl(canvasLtiTabUrl!!, "Zoom - ${course.displayTitle}") },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Abrir Zoom de Canvas")
                                }
                            }
                        }
                    }
                }

                // Lista de reuniones detectadas o configuradas
                items(zoomMeetings) { meeting ->
                    ZoomMeetingCard(
                        meeting = meeting,
                        onJoin = { launchZoomMeetingIntent(context, meeting.joinUrl) },
                        onCopyLink = { copyZoomText(context, "Enlace de Zoom", meeting.joinUrl) },
                        onCopyId = { meeting.meetingId?.let { id -> copyZoomText(context, "ID de reunión", id) } },
                        onCopyPasscode = { meeting.passcode?.let { pwd -> copyZoomText(context, "Código de acceso", pwd) } },
                        onEdit = { showDialog = true }
                    )
                }

                item {
                    OutlinedButton(
                        onClick = { showDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Configurar o cambiar enlace de Zoom")
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    if (showDialog) {
        val currentMeeting = CourseZoomPreferences.getCustomZoomMeeting(context, course.id)
            ?: zoomMeetings.firstOrNull()

        ZoomConfigDialog(
            courseId = course.id,
            initialMeeting = currentMeeting,
            onDismiss = { showDialog = false },
            onSave = { meeting ->
                CourseZoomPreferences.saveCustomZoomMeeting(context, meeting)
                showDialog = false
                refreshMeetings()
                Toast.makeText(context, "Enlace de Zoom guardado", Toast.LENGTH_SHORT).show()
            },
            onDelete = {
                CourseZoomPreferences.deleteCustomZoomMeeting(context, course.id)
                showDialog = false
                refreshMeetings()
                Toast.makeText(context, "Enlace personalizado eliminado", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun ZoomMeetingCard(
    meeting: CourseZoomMeeting,
    onJoin: () -> Unit,
    onCopyLink: () -> Unit,
    onCopyId: () -> Unit,
    onCopyPasscode: () -> Unit,
    onEdit: () -> Unit
) {
    val zoomPrimaryColor = Color(0xFF2D8CFF)

    val originBadge = when (meeting.origin) {
        "CUSTOM" -> "Enlace personalizado"
        "ANNOUNCEMENT" -> "Publicado en Anuncios"
        "MODULE" -> "Encontrado en Módulos"
        "SYLLABUS" -> "Encontrado en Sílabo"
        else -> "Detectado en Canvas"
    }

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = zoomPrimaryColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = originBadge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = zoomPrimaryColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Editar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = meeting.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Botón principal de Unirse a Zoom
            Button(
                onClick = onJoin,
                colors = ButtonDefaults.buttonColors(containerColor = zoomPrimaryColor),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Videocam,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Unirse a la clase en Zoom",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Detalles de reunión (ID y Contraseña si existen)
            if (!meeting.meetingId.isNullOrBlank() || !meeting.passcode.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        if (!meeting.meetingId.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "ID de la reunión",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = meeting.meetingId,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                IconButton(onClick = onCopyId, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = "Copiar ID",
                                        tint = zoomPrimaryColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        if (!meeting.meetingId.isNullOrBlank() && !meeting.passcode.isNullOrBlank()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }

                        if (!meeting.passcode.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Código de acceso / Contraseña",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = meeting.passcode,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                IconButton(onClick = onCopyPasscode, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = "Copiar clave",
                                        tint = zoomPrimaryColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // Botón secundario para copiar enlace
            OutlinedButton(
                onClick = onCopyLink,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copiar enlace de invitación")
            }
        }
    }
}

@Composable
private fun ZoomConfigDialog(
    courseId: Long,
    initialMeeting: CourseZoomMeeting?,
    onDismiss: () -> Unit,
    onSave: (CourseZoomMeeting) -> Unit,
    onDelete: () -> Unit
) {
    var title by remember { mutableStateOf(initialMeeting?.title ?: "Clase Virtual Zoom") }
    var joinUrl by remember { mutableStateOf(initialMeeting?.joinUrl ?: "") }
    var meetingId by remember { mutableStateOf(initialMeeting?.meetingId ?: "") }
    var passcode by remember { mutableStateOf(initialMeeting?.passcode ?: "") }

    val isCustom = initialMeeting?.origin == "CUSTOM"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Configurar Enlace de Zoom",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Pega el enlace o datos de la reunión de Zoom para este curso.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ModernTextField(
                    value = joinUrl,
                    onValueChange = { input ->
                        joinUrl = input
                        // Si pega un link zoom con ID o pwd, auto-extraerlos
                        val extracted = ZoomUrlExtractor.extractZoomMeetingsFromText(input, courseId, "Clase Zoom", "CUSTOM").firstOrNull()
                        if (extracted != null) {
                            if (meetingId.isBlank() && !extracted.meetingId.isNullOrBlank()) {
                                meetingId = extracted.meetingId
                            }
                            if (passcode.isBlank() && !extracted.passcode.isNullOrBlank()) {
                                passcode = extracted.passcode
                            }
                        }
                    },
                    label = "Enlace de Zoom (URL)",
                    placeholder = "https://zoom.us/j/...",
                    leadingIcon = Icons.Filled.Link,
                    modifier = Modifier.fillMaxWidth()
                )

                ModernTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "Nombre de la sesión",
                    placeholder = "Ej. Clase de Teoría o Práctica",
                    leadingIcon = Icons.Filled.Videocam,
                    modifier = Modifier.fillMaxWidth()
                )

                ModernTextField(
                    value = meetingId,
                    onValueChange = { meetingId = it },
                    label = "ID de reunión (opcional)",
                    placeholder = "Ej. 987 6543 2100",
                    leadingIcon = Icons.Filled.Info,
                    modifier = Modifier.fillMaxWidth()
                )

                ModernTextField(
                    value = passcode,
                    onValueChange = { passcode = it },
                    label = "Código de acceso / Clave (opcional)",
                    placeholder = "Ej. 123456",
                    leadingIcon = Icons.Filled.Key,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (joinUrl.isNotBlank()) {
                        onSave(
                            CourseZoomMeeting(
                                courseId = courseId,
                                title = title.ifBlank { "Clase Virtual Zoom" },
                                joinUrl = joinUrl.trim(),
                                meetingId = meetingId.ifBlank { null },
                                passcode = passcode.ifBlank { null },
                                origin = "CUSTOM"
                            )
                        )
                    }
                },
                enabled = joinUrl.isNotBlank()
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            Row {
                if (isCustom) {
                    TextButton(onClick = onDelete) {
                        Text("Eliminar", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        }
    )
}


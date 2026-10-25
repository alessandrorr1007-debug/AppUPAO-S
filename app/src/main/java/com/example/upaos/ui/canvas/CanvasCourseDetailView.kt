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
import com.example.upaos.data.canvas.*
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
    val courseAccentColor = remember(course.name) { cursoColor(course.name) }

    // Interceptar botón atrás físico para volver a la lista de cursos
    BackHandler { onBack() }

    // 9 Secciones del Curso:
    // 0: Inicio, 1: Módulos, 2: Personas, 3: Grupos, 4: Calificaciones,
    // 5: Foro de discusión, 6: Archivos, 7: Programa de curso, 8: Zoom
    var selectedSection by remember { mutableIntStateOf(0) }
    var selectedPage by remember { mutableStateOf<String?>(null) }
    var selectedDiscussion by remember { mutableStateOf<CanvasDiscussionTopic?>(null) }
    selectedPage?.let { CanvasPageDetail(course.id, it, token) { selectedPage = null } }
    selectedDiscussion?.let { CanvasDiscussionDetail(course.id, it, token) { selectedDiscussion = null } }


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
            Triple(8, "Zoom", Icons.Filled.Videocam),
            Triple(9, "Anuncios", Icons.Filled.Info),
            Triple(10, "Páginas", Icons.Filled.Description),
            Triple(11, "Quizzes", Icons.Filled.Quiz),
            Triple(12, "Carpetas", Icons.Filled.Folder)
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
                        if (item.type.equals("Page", true) && (item.pageUrl != null || item.contentId != null)) {
                            selectedPage = item.pageUrl ?: "page_id:${item.contentId}"
                        } else if (item.isFile) {
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
                    onOpenTopic = { topic -> selectedDiscussion = topic }
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
                9 -> CanvasAnnouncements(token, listOf(course.id))
                10 -> CanvasPages(course.id, token)
                11 -> CanvasQuizzes(course.id, token)
                12 -> CanvasFolders(course.id, token)

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
                                    val dueText = todo.dueAt?.let { formatRelativeDateText(it) } ?: "Sin fecha de entrega"
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
    val resource = canvasResourceViewModel("modules:${courseId}", token, emptyList<CanvasModule>()) { modules(courseId) }
    val resourceState by resource.state.collectAsState()
    CanvasResourceFeedback(resourceState) { resource.refresh(true) }
    val modules = resourceState.data
    val isLoading = resourceState.loading
    val isRefreshing = resourceState.refreshing
    fun loadModules(isManual: Boolean = false) = resource.refresh(isManual)


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
    CanvasCourseGrades(course, token, onOpenAssignment)
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
    val resource = canvasResourceViewModel("users:${courseId}", token, emptyList<CanvasUser>()) { people(courseId) }
    val resourceState by resource.state.collectAsState()
    CanvasResourceFeedback(resourceState) { resource.refresh(true) }
    val users = resourceState.data
    val isLoadingUsers = resourceState.loading
    var searchQuery by remember { mutableStateOf("") }


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
    val resource = canvasResourceViewModel("groups:${courseId}", token, emptyList<CanvasGroup>()) { courseGroups(courseId) }
    val resourceState by resource.state.collectAsState()
    CanvasResourceFeedback(resourceState) { resource.refresh(true) }
    val groups = resourceState.data
    val isLoadingGroups = resourceState.loading


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
    val resource = canvasResourceViewModel("topics:${courseId}", token, emptyList<CanvasDiscussionTopic>()) { discussions(courseId) }
    val resourceState by resource.state.collectAsState()
    CanvasResourceFeedback(resourceState) { resource.refresh(true) }
    val topics = resourceState.data
    val isLoading = resourceState.loading
    val isRefreshing = resourceState.refreshing
    fun loadDiscussions(isManual: Boolean = false) = resource.refresh(isManual)


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
    val resource = canvasResourceViewModel("files:${courseId}", token, emptyList<CanvasFileDetail>()) { files(courseId) }
    val resourceState by resource.state.collectAsState()
    CanvasResourceFeedback(resourceState) { resource.refresh(true) }
    val files = resourceState.data
    val isLoading = resourceState.loading
    val isRefreshing = resourceState.refreshing
    var searchQuery by remember { mutableStateOf("") }
    fun loadFiles(isManual: Boolean = false) = resource.refresh(isManual)


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
    val resource = canvasResourceViewModel("courseDetail:${course.id}", token, null as CanvasCourse?) { syllabus(course.id) }
    val resourceState by resource.state.collectAsState()
    CanvasResourceFeedback(resourceState) { resource.refresh(true) }
    val courseDetail = resourceState.data
    val isLoading = resourceState.loading


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
    val repository = rememberCanvasRepository(token)
    val zoomRepository = remember(repository, course.id) {
        DefaultCanvasZoomRepository(context.applicationContext, repository, course, repository::requireSession)
    }
    val tokens = remember(context) { com.example.upaos.data.local.TokenManager(context.applicationContext) }
    val zoomVm: CanvasZoomViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = canvasViewModelKey("zoom:" + course.id, tokens.getSavedUser().orEmpty(), tokens.getSessionId(), token),
        factory = canvasViewModelFactory { CanvasZoomViewModel(zoomRepository) })
    val zoomState by zoomVm.state.collectAsState()
    CanvasResourceFeedback(zoomState) { zoomVm.refresh() }
    val zoomMeetings = zoomState.data.meetings
    val canvasLtiTabUrl = zoomState.data.ltiUrl
    val isLoading = zoomState.loading
    var showDialog by remember { mutableStateOf(false) }
    fun refreshMeetings() = zoomVm.refresh()

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
        val currentMeeting = zoomState.data.custom
            ?: zoomMeetings.firstOrNull()

        ZoomConfigDialog(
            courseId = course.id,
            initialMeeting = currentMeeting,
            onDismiss = { showDialog = false },
            onSave = { meeting ->
                zoomVm.save(meeting)
                showDialog = false
                refreshMeetings()
                Toast.makeText(context, "Enlace de Zoom guardado", Toast.LENGTH_SHORT).show()
            },
            onDelete = {
                zoomVm.delete()
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


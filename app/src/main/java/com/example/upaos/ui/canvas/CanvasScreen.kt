package com.example.upaos.ui.canvas

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.example.upaos.data.canvas.*
import com.example.upaos.data.refresh.*
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AssignmentLate
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.text.HtmlCompat
import com.example.upaos.data.local.CanvasCache
import com.example.upaos.data.local.TokenManager
import com.example.upaos.data.model.CanvasConversation
import com.example.upaos.data.model.CanvasCourse
import com.example.upaos.data.model.CanvasFileDetail
import com.example.upaos.data.model.CanvasGroup
import com.example.upaos.data.model.CanvasModule
import com.example.upaos.data.model.CanvasModuleItem
import com.example.upaos.data.model.CanvasNotificationItem
import com.example.upaos.data.model.CanvasQuiz
import com.example.upaos.data.model.CanvasTodoItem
import com.example.upaos.data.model.CanvasUser
import com.example.upaos.ui.components.AppCard
import com.example.upaos.ui.components.EmptyState
import com.example.upaos.ui.components.ErrorView
import com.example.upaos.ui.components.ModernTextField
import com.example.upaos.ui.components.PrimaryButton
import com.example.upaos.ui.components.RefreshableContent
import com.example.upaos.ui.components.StatusBadge
import com.example.upaos.ui.components.cursoColor
import com.example.upaos.ui.components.toTitleCase
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private fun parseIsoDate(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return try {
        val base = if (iso.contains(".")) iso.substringBefore(".") + "Z" else iso
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        fmt.parse(base)?.time
    } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e;
        null
    }
}

private fun formatCanvasDueDate(iso: String?): Pair<String, Color> {
    val millis = parseIsoDate(iso) ?: return Pair("Sin fecha límite", Color(0xFF757575))
    val calHoy = Calendar.getInstance()
    val calTarget = Calendar.getInstance().apply { timeInMillis = millis }

    val esMismoDia = calHoy.get(Calendar.YEAR) == calTarget.get(Calendar.YEAR) &&
            calHoy.get(Calendar.DAY_OF_YEAR) == calTarget.get(Calendar.DAY_OF_YEAR)

    val calManana = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    val esManana = calManana.get(Calendar.YEAR) == calTarget.get(Calendar.YEAR) &&
            calManana.get(Calendar.DAY_OF_YEAR) == calTarget.get(Calendar.DAY_OF_YEAR)

    val horaStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))
    val ahora = System.currentTimeMillis()
    val diff = millis - ahora

    return when {
        diff < 0 -> {
            val diasPasados = (-diff / (24 * 3600 * 1000L)).coerceAtLeast(1)
            Pair("Vencido hace $diasPasados d ($horaStr)", Color(0xFFD32F2F))
        }
        esMismoDia -> Pair("Vence hoy a las $horaStr", Color(0xFFD32F2F))
        esManana -> Pair("Vence mañana a las $horaStr", Color(0xFFE65100))
        diff <= 3 * 24 * 3600 * 1000L -> {
            val dias = (diff / (24 * 3600 * 1000L)).coerceAtLeast(2)
            Pair("Vence en $dias días ($horaStr)", Color(0xFFF57C00))
        }
        else -> {
            val fechaStr = SimpleDateFormat("d 'de' MMM, h:mm a", Locale.forLanguageTag("es-ES")).format(Date(millis))
            Pair("Vence el $fechaStr", Color(0xFF2E7D32))
        }
    }
}

private fun formatRelativeDate(iso: String?): String {
    val millis = parseIsoDate(iso) ?: return ""
    val ahora = System.currentTimeMillis()
    val diff = ahora - millis
    val minutos = diff / 60000
    return when {
        minutos < 1 -> "hace un momento"
        minutos < 60 -> "hace $minutos min"
        minutos < 60 * 24 -> "hace ${minutos / 60} h"
        minutos < 60 * 24 * 7 -> "hace ${minutos / (60 * 24)} d"
        else -> SimpleDateFormat("d 'de' MMM", Locale.forLanguageTag("es-ES")).format(Date(millis))
    }
}

private fun formatActualizado(millis: Long?): String {
    if (millis == null || millis <= 0) return ""
    val diffMin = (System.currentTimeMillis() - millis) / 60000
    return when {
        diffMin < 1 -> "Actualizado hace un momento"
        diffMin < 60 -> "Actualizado hace $diffMin min"
        else -> {
            val hora = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))
            "Actualizado a las $hora"
        }
    }
}

private fun downloadCanvasFile(context: Context, url: String, fileName: String) {
    try {
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(fileName)
            setDescription("Descargando archivo de Canvas LMS")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        Toast.makeText(context, "Iniciando descarga: $fileName", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e;
        Toast.makeText(context, "Error al iniciar descarga: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun HtmlText(
    html: String,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()

    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                movementMethod = LinkMovementMethod.getInstance()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(textColor)
                setLinkTextColor(linkColor)
                setLineSpacing(4f, 1.15f)
            }
        },
        update = { tv ->
            tv.setTextColor(textColor)
            tv.setLinkTextColor(linkColor)
            tv.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasContent(
    token: String,
    usuario: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }

    val userKey = usuario ?: "anonimo"
    var savedCanvasToken by remember(usuario) { mutableStateOf(tokenManager.getCanvasToken(userKey)) }
    var isSetupMode by remember(userKey) { mutableStateOf(savedCanvasToken.isNullOrBlank()) }

    var selectedMainTab by remember(userKey) { mutableIntStateOf(0) } // 0: Cursos, 1: Pendientes, 2: Notificaciones, 3: Bandeja de entrada

    // Modales In-App (visualización 100% nativa)
    var selectedCourseForDetail by remember(userKey) { mutableStateOf<CanvasCourse?>(null) }
    var selectedTodoDetail by remember(userKey) { mutableStateOf<CanvasTodoItem?>(null) }
    var selectedConversationDetail by remember(userKey) { mutableStateOf<CanvasConversation?>(null) }
    var selectedGroupDetail by remember(userKey) { mutableStateOf<CanvasGroup?>(null) }
    var selectedFileItem by remember(userKey) { mutableStateOf<Pair<CanvasModuleItem, String>?>(null) }
    var inAppWebUrl by remember(userKey) { mutableStateOf<Pair<String, String>?>(null) }

    val repository = rememberCanvasRepository(savedCanvasToken.orEmpty(), userKey)
    val hub: CanvasHubViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = canvasViewModelKey("hub", userKey, tokenManager.getSessionId(), savedCanvasToken.orEmpty()),
        factory = canvasViewModelFactory { CanvasHubViewModel(userKey, savedCanvasToken.orEmpty(), repository) })
    val hubState by hub.state.collectAsState()
    val courses = hubState.courses
    val isLoadingCourses = hubState.isLoadingCourses
    val isRefreshingCourses = hubState.isRefreshingCourses
    val items = hubState.items
    val isLoadingTodo = hubState.isLoadingTodo
    val isRefreshingTodo = hubState.isRefreshingTodo
    val lastUpdatedMillis = hubState.lastUpdatedMillis
    val conversations = hubState.conversations
    val isLoadingConvs = hubState.isLoadingConvs
    val isRefreshingConvs = hubState.isRefreshingConvs
    val notifications = hubState.notifications
    val isLoadingNotifs = hubState.isLoadingNotifs
    val isRefreshingNotifs = hubState.isRefreshingNotifs
    val groups = hubState.groups
    val isLoadingGroups = hubState.isLoadingGroups
    val isRefreshingGroups = hubState.isRefreshingGroups
    val errorMessage = hubState.errorMessage
    val isTokenExpired = hubState.isTokenExpired
    val isOffline = hubState.isOffline
    fun fetchCourses(isManual: Boolean = false) = hub.fetchCourses(isManual)
    fun fetchTodo(isManual: Boolean = false) = hub.fetchTodo(isManual)
    fun fetchConversations(isManual: Boolean = false) = hub.fetchConversations(isManual)
    fun fetchNotifications(isManual: Boolean = false) = hub.fetchNotifications(isManual)
    fun fetchGroups(isManual: Boolean = false) = hub.fetchGroups(isManual)

    if (isSetupMode) {
        CanvasSetupView(
            usuario = userKey,
            initialToken = savedCanvasToken ?: "",
            onTokenSaved = { newToken ->
                savedCanvasToken = newToken
                isSetupMode = false
            },
            onCancel = if (!savedCanvasToken.isNullOrBlank()) {
                { isSetupMode = false }
            } else null
        )
    } else {
        if (selectedCourseForDetail != null) {
            CanvasCourseDetailView(
                course = selectedCourseForDetail!!,
                token = savedCanvasToken ?: "",
                onBack = { selectedCourseForDetail = null },
                onOpenAssignment = { selectedTodoDetail = it },
                onOpenGroup = { selectedGroupDetail = it },
                onOpenFile = { fileItem, modName -> selectedFileItem = Pair(fileItem, modName) },
                onOpenUrl = { url, title -> inAppWebUrl = Pair(url, title) },
                todoItems = items,
                notifications = notifications,
                modifier = modifier
            )
        } else {
            Column(modifier = modifier.fillMaxSize()) {
                // Header superior con botón de token
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Canvas LMS Hub",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        com.example.upaos.ui.home.Freshness(lastUpdatedMillis, !isOffline && lastUpdatedMillis != null)
                        val subtexto = when {
                            isOffline -> "Datos guardados · pendiente de actualizar"
                            lastUpdatedMillis != null -> formatActualizado(lastUpdatedMillis)
                            else -> "Sin datos actualizados"
                        }
                        Text(
                            text = subtexto,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOffline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { isSetupMode = true },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Key,
                                    contentDescription = "Configurar token Canvas",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Alerta de Token Expirado
                if (isTokenExpired) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        AppCard(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Token expirado. Pulsa para actualizar.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                // Tabs principales de Canvas: Cursos, Pendientes, Notificaciones, Bandeja de entrada
                ScrollableTabRow(
                    selectedTabIndex = selectedMainTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 12.dp,
                    divider = {}
                ) {
                    // Tab 0: Cursos
                    Tab(
                        selected = selectedMainTab == 0,
                        onClick = {
                            selectedMainTab = 0
                            if (courses.isEmpty()) fetchCourses()
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.School, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cursos", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                if (courses.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        Text("${courses.size}", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    )

                    // Tab 1: Pendientes
                    Tab(
                        selected = selectedMainTab == 1,
                        onClick = { selectedMainTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pendientes", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                if (items.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        Text("${items.size}", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    )

                    // Tab 2: Notificaciones
                    Tab(
                        selected = selectedMainTab == 2,
                        onClick = {
                            selectedMainTab = 2
                            if (notifications.isEmpty()) fetchNotifications()
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Notificaciones", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                val unreadNotifs = notifications.count { it.isUnread }
                                if (unreadNotifs > 0) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ) {
                                        Text("$unreadNotifs", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    )

                    // Tab 3: Bandeja de entrada
                    Tab(
                        selected = selectedMainTab == 3,
                        onClick = {
                            selectedMainTab = 3
                            if (conversations.isEmpty()) fetchConversations()
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Mail, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Bandeja de entrada", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                val unread = conversations.count { it.isUnread }
                                if (unread > 0) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ) {
                                        Text("$unread", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                errorMessage?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }

                // Contenido por Pestaña
                when (selectedMainTab) {
                    0 -> {
                        // TAB CURSOS
                        RefreshableContent(
                            isRefreshing = isRefreshingCourses,
                            onRefresh = { fetchCourses(isManual = true) },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                when {
                                    isLoadingCourses -> {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    "Cargando tus cursos de Canvas...",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    courses.isEmpty() -> {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            EmptyState(
                                                icon = Icons.Filled.School,
                                                title = "Sin cursos activos",
                                                subtitle = "No se encontraron cursos activos matriculados en tu cuenta de Canvas."
                                            )
                                        }
                                    }
                                    else -> {
                                        LazyColumn(
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            item {
                                                Text(
                                                    text = "Mis cursos matriculados (${courses.size})",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                )
                                            }
                                            items(courses, key = { it.id }) { course ->
                                                CanvasCourseCard(
                                                    course = course,
                                                    onClick = { selectedCourseForDetail = course }
                                                )
                                            }
                                            item { Spacer(modifier = Modifier.height(16.dp)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // TAB PENDIENTES
                        RefreshableContent(
                            isRefreshing = isRefreshingTodo,
                            onRefresh = { fetchTodo(isManual = true) },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                when {
                                    isLoadingTodo -> {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    "Verificando tareas en todos tus cursos...",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    items.isEmpty() -> {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            EmptyState(
                                                icon = Icons.Filled.CheckCircle,
                                                title = "¡Al día en Canvas!",
                                                subtitle = "No tienes tareas pendientes por subir ni exámenes por rendir."
                                            )
                                        }
                                    }
                                    else -> {
                                        LazyColumn(
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            items(items, key = { it.assignment?.id ?: it.quiz?.id ?: it.title.hashCode() }) { item ->
                                                CanvasTodoItemCard(
                                                    item = item,
                                                    onClick = { selectedTodoDetail = item }
                                                )
                                            }
                                            item { Spacer(modifier = Modifier.height(16.dp)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        // TAB NOTIFICACIONES
                        RefreshableContent(
                            isRefreshing = isRefreshingNotifs,
                            onRefresh = { fetchNotifications(isManual = true) },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                when {
                                    isLoadingNotifs -> {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                        }
                                    }
                                    notifications.isEmpty() -> {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            EmptyState(
                                                icon = Icons.Filled.Notifications,
                                                title = "Sin notificaciones",
                                                subtitle = "No tienes avisos ni notificaciones recientes en Canvas."
                                            )
                                        }
                                    }
                                    else -> {
                                        LazyColumn(
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            items(notifications, key = { it.id }) { notif ->
                                                CanvasNotificationCard(
                                                    item = notif,
                                                    onClick = {
                                                        if (!notif.htmlUrl.isNullOrBlank()) {
                                                            inAppWebUrl = Pair(notif.htmlUrl, notif.title ?: "Aviso Canvas")
                                                        }
                                                    }
                                                )
                                            }
                                            item { Spacer(modifier = Modifier.height(16.dp)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    3 -> {
                        // TAB BANDEJA DE ENTRADA
                        RefreshableContent(
                            isRefreshing = isRefreshingConvs,
                            onRefresh = { fetchConversations(isManual = true) },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                when {
                                    isLoadingConvs -> {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                        }
                                    }
                                    conversations.isEmpty() -> {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            EmptyState(
                                                icon = Icons.Filled.Mail,
                                                title = "Bandeja de entrada vacía",
                                                subtitle = "No tienes comunicaciones recientes en la bandeja de entrada de Canvas."
                                            )
                                        }
                                    }
                                    else -> {
                                        LazyColumn(
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            items(conversations, key = { it.id }) { conv ->
                                                CanvasConversationCard(
                                                    conversation = conv,
                                                    onClick = { selectedConversationDetail = conv }
                                                )
                                            }
                                            item { Spacer(modifier = Modifier.height(16.dp)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

            // Hojas y Visores Nativos In-App
            selectedTodoDetail?.let { item ->
                CanvasAssignmentDetailSheet(
                    item = item,
                    onDismiss = { selectedTodoDetail = null }
                )
            }

            selectedGroupDetail?.let { group ->
                CanvasGroupDetailSheet(
                    group = group,
                    token = savedCanvasToken ?: "",
                    onDismiss = { selectedGroupDetail = null }
                )
            }

            selectedConversationDetail?.let { conv ->
                CanvasConversationDetailSheet(
                    conversation = conv,
                    token = savedCanvasToken ?: "",
                    onDismiss = { selectedConversationDetail = null }
                )
            }

            selectedFileItem?.let { (fileItem, modName) ->
                CanvasFileViewerSheet(
                    item = fileItem,
                    moduleName = modName,
                    token = savedCanvasToken ?: "",
                    onOpenInAppViewer = { viewerUrl, title ->
                        inAppWebUrl = Pair(viewerUrl, title)
                    },
                    onDismiss = { selectedFileItem = null }
                )
            }

            inAppWebUrl?.let { (url, title) ->
                InAppWebViewModal(
                    url = url,
                    title = title,
                    onDismiss = { inAppWebUrl = null }
                )
            }
    }
}

@Composable
fun CanvasConversationCard(
    conversation: CanvasConversation,
    onClick: () -> Unit
) {
    val relativeDate = remember(conversation.lastMessageAt) { formatRelativeDate(conversation.lastMessageAt) }

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    shape = CircleShape,
                    color = if (conversation.isUnread) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = conversation.authorName.firstOrNull()?.uppercase() ?: "C",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (conversation.isUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = toTitleCase(conversation.authorName),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (conversation.isUnread) FontWeight.Bold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!conversation.contextName.isNullOrBlank()) {
                        Text(
                            text = toTitleCase(conversation.contextName),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (relativeDate.isNotBlank()) {
                Text(
                    text = relativeDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = conversation.subject?.ifBlank { "Sin asunto" } ?: "Aviso del curso",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (!conversation.lastMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = conversation.lastMessage.trim(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tocar para leer mensaje",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.Visibility,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
fun CanvasModuleCard(
    module: CanvasModule,
    onItemClick: (CanvasModuleItem) -> Unit
) {
    val items = module.items ?: emptyList()

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = module.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (items.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEach { item ->
                    if (item.isHeader) {
                        Text(
                            text = item.title ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else {
                        val icon = when {
                            item.title?.endsWith(".pdf", ignoreCase = true) == true -> Icons.Filled.PictureAsPdf
                            item.isFile -> Icons.AutoMirrored.Filled.InsertDriveFile
                            item.type.equals("Assignment", ignoreCase = true) -> Icons.AutoMirrored.Filled.Assignment
                            else -> Icons.Filled.Description
                        }
                        val iconColor = if (item.title?.endsWith(".pdf", ignoreCase = true) == true) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onItemClick(item) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item.title ?: "Archivo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (item.isFile) Icons.Filled.Download else Icons.Filled.Visibility,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CanvasTodoItemCard(
    item: CanvasTodoItem,
    onClick: () -> Unit
) {
    val esExamen = item.isQuiz
    val (dueText, dueColor) = remember(item.dueAt) { formatCanvasDueDate(item.dueAt) }
    val courseColor = remember(item.courseDisplayName) { cursoColor(item.courseDisplayName) }

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
        onClick = onClick
    ) {
        // Tag superior: Curso y Tipo (Tarea vs Examen)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = courseColor.copy(alpha = 0.12f),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.School,
                        contentDescription = null,
                        tint = courseColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = toTitleCase(item.courseDisplayName),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = courseColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Badge Tarea / Examen
            Surface(
                shape = RoundedCornerShape(50),
                color = if (esExamen) Color(0xFFFFEBEE) else Color(0xFFEDE7F6)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (esExamen) Icons.Filled.Quiz else Icons.AutoMirrored.Filled.Assignment,
                        contentDescription = null,
                        tint = if (esExamen) Color(0xFFD32F2F) else Color(0xFF673AB7),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (esExamen) "EXAMEN" else "TAREA",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (esExamen) Color(0xFFD32F2F) else Color(0xFF673AB7)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Título del pendiente
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Fecha de entrega y puntuación
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.DateRange,
                    contentDescription = null,
                    tint = dueColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = dueText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = dueColor
                )
            }

            if (item.pointsPossible != null && item.pointsPossible!! > 0) {
                Text(
                    text = "${item.pointsPossible!!.toInt()} pts",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tocar para ver detalle e instrucciones",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.Visibility,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasAssignmentDetailSheet(
    item: CanvasTodoItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val assignment = item.assignment
    val esExamen = item.isQuiz
    val (dueText, dueColor) = remember(item.dueAt) { formatCanvasDueDate(item.dueAt) }
    val courseColor = remember(item.courseDisplayName) { cursoColor(item.courseDisplayName) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // Header Row: Course Badge + Type Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = courseColor.copy(alpha = 0.12f),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.School, contentDescription = null, tint = courseColor, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = toTitleCase(item.courseDisplayName),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = courseColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (esExamen) Color(0xFFFFEBEE) else Color(0xFFEDE7F6)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (esExamen) Icons.Filled.Quiz else Icons.AutoMirrored.Filled.Assignment,
                            contentDescription = null,
                            tint = if (esExamen) Color(0xFFD32F2F) else Color(0xFF673AB7),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (esExamen) "EXAMEN" else "TAREA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (esExamen) Color(0xFFD32F2F) else Color(0xFF673AB7)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Title
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Due Date
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (dueText.contains("Vencid")) Icons.Filled.AssignmentLate else Icons.Filled.DateRange,
                    contentDescription = null,
                    tint = dueColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = dueText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = dueColor
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Metrics row: Points, Submission Type, Status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                val points = item.pointsPossible
                if (points != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Puntos", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${points.toInt()} pts", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                val hasSubmitted = assignment?.isSubmitted == true || assignment?.hasSubmittedSubmissions == true
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Estado", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (hasSubmitted) "Entregado" else "Pendiente",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hasSubmitted) Color(0xFF2E7D32) else Color(0xFFE65100)
                    )
                }

                val attempts = assignment?.allowedAttempts
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Intentos", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (attempts != null && attempts > 0) "$attempts" else "Ilimitados",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Instructions / Description Section
            val rawDesc = assignment?.description ?: ""
            if (rawDesc.isNotBlank()) {
                Text(
                    text = "Instrucciones de la Actividad",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Box(modifier = Modifier.padding(14.dp)) {
                        HtmlText(html = rawDesc, modifier = Modifier.fillMaxWidth())
                    }
                }
            } else {
                Text(
                    text = "Esta actividad no incluye texto de instrucciones.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Rubric if available
            val rubric = assignment?.rubric
            if (!rubric.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Criterios de Evaluación (Rúbrica)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rubric.forEach { crit ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = crit.description ?: "Criterio",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                crit.points?.let { pts ->
                                    Text(
                                        text = "${pts.toInt()} pts",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            CanvasExternalButton(assignment?.htmlUrl ?: item.webUrl, "Entregar en Canvas")

            PrimaryButton(
                text = "Cerrar",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun CanvasNotificationCard(
    item: CanvasNotificationItem,
    onClick: () -> Unit
) {
    val relativeDate = remember(item.createdAt) { formatRelativeDate(item.createdAt) }
    val icon = when (item.type?.lowercase()) {
        "announcement" -> Icons.Filled.Campaign
        "discussiontopic" -> Icons.Filled.Forum
        "submission" -> Icons.Filled.CheckCircle
        "conversation", "message" -> Icons.Filled.Mail
        else -> Icons.Filled.Notifications
    }
    val iconColor = when (item.type?.lowercase()) {
        "announcement" -> Color(0xFFE65100)
        "discussiontopic" -> Color(0xFF0288D1)
        "submission" -> Color(0xFF2E7D32)
        else -> MaterialTheme.colorScheme.primary
    }

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = CircleShape,
                color = iconColor.copy(alpha = 0.12f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title ?: "Aviso de Canvas",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (item.isUnread) FontWeight.Bold else FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (relativeDate.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = relativeDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!item.message.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = HtmlCompat.fromHtml(item.message, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!item.htmlUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Ver detalle",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun CanvasGroupCard(
    group: CanvasGroup,
    onClick: () -> Unit
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Groups,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!group.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = group.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val miembros = group.membersCount
                    if (miembros != null && miembros > 0) {
                        Text(
                            text = "$miembros miembros",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = "Tocar para ver integrantes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasGroupDetailSheet(
    group: CanvasGroup,
    token: String,
    onDismiss: () -> Unit
) {
    val resource = canvasResourceViewModel("members:${group.id}", token, emptyList<CanvasUser>()) { groupPeople(group.id) }
    val resourceState by resource.state.collectAsState()
    val members = resourceState.data
    val isLoadingMembers = resourceState.loading

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Grupo de Canvas",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!group.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = group.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Integrantes del grupo",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (isLoadingMembers) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            } else if (members.isEmpty()) {
                Text(
                    text = "No se pudieron listar los integrantes o el grupo no tiene miembros visibles.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(members, key = { it.id }) { member ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = member.name?.firstOrNull()?.uppercase() ?: "U",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = member.name ?: "Estudiante",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            PrimaryButton(
                text = "Cerrar",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasConversationDetailSheet(
    conversation: CanvasConversation,
    token: String,
    onDismiss: () -> Unit
) {
    val resource = canvasResourceViewModel("detail:${conversation.id}", token, null as CanvasConversation?) { conversation(conversation.id) }
    val resourceState by resource.state.collectAsState()
    val detail = resourceState.data
    val isLoading = resourceState.loading

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // Header
            Text(
                text = conversation.subject ?: "Sin asunto",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (!conversation.contextName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = toTitleCase(conversation.contextName),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Participants
            val participants = detail?.participants ?: conversation.participants ?: emptyList()
            if (participants.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Participantes: " + participants.joinToString(", ") { toTitleCase(it.name ?: "") },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            } else {
                val messages = detail?.messages ?: emptyList()
                if (messages.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = conversation.authorName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = conversation.lastMessage ?: "Mensaje vacío",
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        messages.forEach { msg ->
                            val author = participants.firstOrNull { it.id == msg.authorId }?.name ?: conversation.authorName
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = toTitleCase(author),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = formatRelativeDate(msg.createdAt),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = msg.body ?: "",
                                        fontSize = 14.sp,
                                        lineHeight = 21.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            PrimaryButton(
                text = "Cerrar",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasFileViewerSheet(
    item: CanvasModuleItem,
    moduleName: String,
    token: String,
    onOpenInAppViewer: (url: String, title: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val resource = canvasResourceViewModel("fileDetail:${item.id}", token, null as CanvasFileDetail?) { if (item.url.isNullOrBlank()) null else fileByUrl(item.url) }
    val resourceState by resource.state.collectAsState()
    val fileDetail = resourceState.data
    val isLoading = resourceState.loading

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                item.title?.endsWith(".pdf", ignoreCase = true) == true -> Icons.Filled.PictureAsPdf
                                item.title?.endsWith(".docx", ignoreCase = true) == true || item.title?.endsWith(".doc", ignoreCase = true) == true -> Icons.Filled.Description
                                else -> Icons.AutoMirrored.Filled.InsertDriveFile
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title ?: "Archivo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = moduleName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else {
                fileDetail?.let { fd ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tamaño del archivo", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(fd.formattedSize, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }

            // Action 1: Ver en Visor Interno (In-App)
            PrimaryButton(
                text = "Ver en Visor Interno (In-App)",
                icon = Icons.Filled.Visibility,
                onClick = {
                    val directUrl = fileDetail?.url
                    if (!directUrl.isNullOrBlank()) {
                        val isPdf = item.title?.endsWith(".pdf", ignoreCase = true) == true
                        val viewerUrl = if (isPdf) {
                            "private-pdf:" + directUrl
                        } else {
                            directUrl
                        }
                        onOpenInAppViewer(viewerUrl, item.title ?: "Visor de Documentos")
                        onDismiss()
                    } else {
                        Toast.makeText(context, "No se pudo obtener el enlace del archivo", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !isLoading && fileDetail?.url != null,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action 2: Descargar al celular
            OutlinedButton(
                onClick = {
                    val directUrl = fileDetail?.url
                    if (!directUrl.isNullOrBlank()) {
                        downloadCanvasFile(context, directUrl, item.title ?: "archivo_canvas")
                        onDismiss()
                    }
                },
                enabled = !isLoading && fileDetail?.url != null,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Descargar al celular", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppWebViewModal(
    url: String,
    title: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(
                            text = title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                var isLoading by remember { mutableStateOf(true) }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (url.startsWith("private-pdf:")) {
                        PrivatePdfViewer(url.removePrefix("private-pdf:"))
                    } else {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isLoading = false
                                    }
                                }
                                loadUrl(url)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    }
                }
            }
        }
    }
}

@Composable
fun CanvasSetupView(
    usuario: String,
    initialToken: String = "",
    onTokenSaved: (String) -> Unit,
    onCancel: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var tokenInput by remember(usuario, initialToken) { mutableStateOf(initialToken) }
    val tokens = remember(context) { TokenManager(context.applicationContext) }
    val session = tokens.getSessionId()
    val setupRepository = remember(context, usuario, session) {
        com.example.upaos.data.canvas.DefaultCanvasSetupRepository(
            com.example.upaos.data.api.CanvasApiClient.apiService,
            validateOwner = { tokens.getSavedUser() == usuario && tokens.getSessionId() == session },
            save = { tokens.saveCanvasToken(usuario, it) })
    }
    val setup: CanvasSetupViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = canvasViewModelKey("setup", usuario, session, ""),
        factory = canvasViewModelFactory { CanvasSetupViewModel(setupRepository) })
    val setupState by setup.state.collectAsState()
    val isValidating = setupState.loading
    val errorText = setupState.error
    val userNameDetected = setupState.userName
    fun validarYGuardar() = setup.validate(tokenInput)
    LaunchedEffect(setupState.savedToken) {
        setupState.savedToken?.let { saved ->
            setup.consumeSavedToken()
            Toast.makeText(context, "¡Conectado con Canvas como " + userNameDetected + "!", Toast.LENGTH_LONG).show()
            onTokenSaved(saved)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Conectar Canvas LMS",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Sincroniza tus tareas, exámenes, avisos y materiales de clase directamente desde Canvas LMS.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        AppCard(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentPadding = PaddingValues(14.dp)
        ) {
            Text(
                text = "¿Cómo obtener tu token?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "1. Abre Canvas UPAO en tu navegador web.\n" +
                        "2. Entra a Cuenta (menú lateral) → Configuración.\n" +
                        "3. En 'Tokens de acceso aprobados', pulsa '+ Nuevo token de acceso'.\n" +
                        "4. Copia el token generado y pégalo abajo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            TextButton(
                onClick = {
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://upao.instructure.com/profile/settings"))
                        context.startActivity(browserIntent)
                    } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; android.util.Log.w("Canvas", "No se pudo completar la operación de Canvas") }
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Ir a Configuración Canvas", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        ModernTextField(
            value = tokenInput,
            onValueChange = {
                tokenInput = it
                setup.clearError()
            },
            label = "Token de Acceso de Canvas",
            placeholder = "Pega aquí tu token (ej. 17977~...)",
            singleLine = false,
            maxLines = 3,
            isError = errorText != null,
            supportingText = errorText,
            trailingIcon = {
                TextButton(
                    onClick = {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrBlank()) {
                            tokenInput = clip.trim()
                            setup.clearError()
                        }
                    }
                ) {
                    Text("Pegar", fontSize = 12.sp)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        PrimaryButton(
            text = "Validar y Conectar",
            onClick = { validarYGuardar() },
            loading = isValidating,
            icon = Icons.Filled.CheckCircle,
            modifier = Modifier.fillMaxWidth()
        )

        if (onCancel != null) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancelar")
            }
        }
    }
}

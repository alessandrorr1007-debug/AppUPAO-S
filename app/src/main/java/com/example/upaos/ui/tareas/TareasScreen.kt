package com.example.upaos.ui.tareas

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.upaos.data.api.RetrofitClient
import com.example.upaos.data.local.GradesCache
import com.example.upaos.data.local.TasksPreferences
import com.example.upaos.data.model.TaskModel
import com.example.upaos.service.TaskReminderManager
import com.example.upaos.ui.components.AppCard
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun formatoFechaHora(millis: Long): String {
    val calHoy = Calendar.getInstance()
    val calTarget = Calendar.getInstance().apply { timeInMillis = millis }

    val esMismoDia = calHoy.get(Calendar.YEAR) == calTarget.get(Calendar.YEAR) &&
            calHoy.get(Calendar.DAY_OF_YEAR) == calTarget.get(Calendar.DAY_OF_YEAR)

    val calManana = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    val esManana = calManana.get(Calendar.YEAR) == calTarget.get(Calendar.YEAR) &&
            calManana.get(Calendar.DAY_OF_YEAR) == calTarget.get(Calendar.DAY_OF_YEAR)

    val horaStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))

    return when {
        esMismoDia -> "Hoy a las $horaStr"
        esManana -> "Mañana a las $horaStr"
        else -> {
            val fechaStr = SimpleDateFormat("d 'de' MMM", Locale.forLanguageTag("es-ES")).format(Date(millis))
            "$fechaStr, $horaStr"
        }
    }
}

private fun calcularEstadoVencimiento(millis: Long, completada: Boolean, esExamen: Boolean): Triple<String, Color, Color> {
    if (completada) {
        return Triple("Completada", Color(0xFF4CAF50), Color(0xFFE8F5E9))
    }
    val ahora = System.currentTimeMillis()
    val diff = millis - ahora

    return when {
        diff < 0 -> Triple("Vencido", Color(0xFFD32F2F), Color(0xFFFFEBEE))
        diff <= 24 * 3600 * 1000L -> {
            if (esExamen) Triple("¡Examen hoy!", Color(0xFFD32F2F), Color(0xFFFFEBEE))
            else Triple("Vence pronto", Color(0xFFE65100), Color(0xFFFFF3E0))
        }
        diff <= 48 * 3600 * 1000L -> {
            if (esExamen) Triple("Examen mañana", Color(0xFFE65100), Color(0xFFFFF3E0))
            else Triple("En 2 días", Color(0xFFF57C00), Color(0xFFFFF8E1))
        }
        else -> {
            val dias = (diff / (24 * 3600 * 1000L)).toInt()
            Triple("En $dias días", Color(0xFF1976D2), Color(0xFFE3F2FD))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TareasScreen(
    token: String = "",
    usuario: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val tasksPrefs = remember { TasksPreferences(context) }
    val scope = rememberCoroutineScope()
    val safeUser = usuario ?: "anonimo"

    val tasks by tasksPrefs.getTasksFlow(safeUser).collectAsState(initial = emptyList())
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Pendientes, 1: Completadas, 2: Todas
    var filterType by remember { mutableStateOf<String?>(null) } // null: todos, "TAREA", "EXAMEN"

    var showDialog by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<TaskModel?>(null) }
    var taskToDelete by remember { mutableStateOf<TaskModel?>(null) }

    // Cursos disponibles del ciclo actual
    var cursosDisponibles by remember { mutableStateOf<List<String>>(emptyList()) }
    var loadingCursos by remember { mutableStateOf(true) }

    // Cargar cursos desde la caché local o API
    LaunchedEffect(safeUser, token) {
        withContext(Dispatchers.IO) {
            val cache = GradesCache(context)
            var lista = cache.obtenerCursosActuales(safeUser)

            if (lista.isEmpty() && token.isNotBlank()) {
                try {
                    val req = mapOf("periodo" to "202610", "carrera" to "UG")
                    val res = RetrofitClient.apiService.buscarNotas("Bearer $token", req)
                    if (res.isSuccessful && res.body() != null) {
                        val body = res.body()!!
                        val nombres = body.cursos.mapNotNull { it.displayNombre.trim() }.filter { it.isNotBlank() }
                        if (nombres.isNotEmpty()) {
                            lista = nombres
                            cache.guardar("notas_$safeUser", Gson().toJson(body))
                        }
                    }
                } catch (_: Exception) {}
            }

            withContext(Dispatchers.Main) {
                cursosDisponibles = lista
                loadingCursos = false
            }
        }
    }

    val tareasFiltradasPorEstado = when (selectedTab) {
        0 -> tasks.filter { !it.completada }.sortedBy { it.fechaEntregaMillis }
        1 -> tasks.filter { it.completada }.sortedByDescending { it.fechaEntregaMillis }
        else -> tasks.sortedBy { it.fechaEntregaMillis }
    }

    val tareasFinales = remember(tareasFiltradasPorEstado, filterType) {
        if (filterType == null) tareasFiltradasPorEstado
        else tareasFiltradasPorEstado.filter { it.tipo.equals(filterType, ignoreCase = true) }
    }

    val totalPendientes = remember(tasks) { tasks.count { !it.completada } }
    val examenesPendientes = remember(tasks) { tasks.count { !it.completada && it.tipo.equals("EXAMEN", ignoreCase = true) } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Tareas y Exámenes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (totalPendientes > 0) {
                                val detalleExamenes = if (examenesPendientes > 0) " ($examenesPendientes examen${if (examenesPendientes > 1) "es" else ""})" else ""
                                "$totalPendientes pendiente${if (totalPendientes > 1) "s" else ""}$detalleExamenes"
                            } else {
                                "¡Todo al día!"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (examenesPendientes > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    taskToEdit = null
                    showDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Nuevo pendiente")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Agregar", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tabs principales (Pendientes / Completadas / Todas)
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Pendientes", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal)
                            if (totalPendientes > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                    Text("$totalPendientes")
                                }
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text("Completadas", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Text("Todas (${tasks.size})", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal)
                    }
                )
            }

            // Filtros rápidos por Tipo (Todas, Tareas, Exámenes)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterType == null,
                    onClick = { filterType = null },
                    label = { Text("Todo", fontSize = 12.sp) },
                    shape = RoundedCornerShape(8.dp)
                )
                FilterChip(
                    selected = filterType == "TAREA",
                    onClick = { filterType = if (filterType == "TAREA") null else "TAREA" },
                    label = { Text("📘 Solo Tareas", fontSize = 12.sp) },
                    shape = RoundedCornerShape(8.dp)
                )
                FilterChip(
                    selected = filterType == "EXAMEN",
                    onClick = { filterType = if (filterType == "EXAMEN") null else "EXAMEN" },
                    label = { Text("🎯 Solo Exámenes", fontSize = 12.sp) },
                    shape = RoundedCornerShape(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (tareasFinales.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (selectedTab == 1) Icons.Default.AssignmentTurnedIn else Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = when {
                                selectedTab == 1 -> "No hay elementos completados"
                                filterType == "EXAMEN" -> "¡No tienes exámenes pendientes!"
                                filterType == "TAREA" -> "¡No tienes tareas pendientes!"
                                selectedTab == 0 -> "¡Estás completamente al día!"
                                else -> "No hay registros aún"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (selectedTab == 0) "Usa el botón Agregar para anotar tus tareas y exámenes con recordatorio."
                            else "Las tareas y exámenes que marques aparecerán organizados aquí.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(tareasFinales, key = { it.id }) { tarea ->
                        TaskItemCard(
                            tarea = tarea,
                            onToggleComplete = {
                                scope.launch {
                                    val nuevoEstado = !tarea.completada
                                    tasksPrefs.toggleTaskCompleted(tarea.id, safeUser)
                                    if (nuevoEstado) {
                                        TaskReminderManager.cancelReminder(context, tarea.id)
                                    } else {
                                        TaskReminderManager.scheduleReminder(context, tarea.copy(completada = false))
                                    }
                                }
                            },
                            onEdit = {
                                taskToEdit = tarea
                                showDialog = true
                            },
                            onDelete = {
                                taskToDelete = tarea
                            }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
        }
    }

    // Modal para registrar o editar tarea / examen
    if (showDialog) {
        TaskFormDialog(
            taskToEdit = taskToEdit,
            cursosSugeridos = cursosDisponibles,
            loadingCursos = loadingCursos,
            onDismiss = {
                showDialog = false
                taskToEdit = null
            },
            onSave = { nuevaTarea ->
                scope.launch {
                    val tareaFinal = nuevaTarea.copy(usuario = safeUser)
                    tasksPrefs.saveTask(tareaFinal)
                    TaskReminderManager.scheduleReminder(context, tareaFinal)
                    showDialog = false
                    taskToEdit = null
                    val tipoMsg = if (tareaFinal.tipo == "EXAMEN") "Examen guardado" else "Tarea guardada"
                    Toast.makeText(context, tipoMsg, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Diálogo de confirmación para eliminar
    taskToDelete?.let { tarea ->
        val esExamen = tarea.tipo.equals("EXAMEN", ignoreCase = true)
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text(if (esExamen) "Eliminar examen" else "Eliminar tarea", fontWeight = FontWeight.Bold) },
            text = { Text("¿Deseas eliminar '${tarea.titulo}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            TaskReminderManager.cancelReminder(context, tarea.id)
                            tasksPrefs.deleteTask(tarea.id, safeUser)
                            taskToDelete = null
                            Toast.makeText(context, "Eliminado correctamente", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun TaskItemCard(
    tarea: TaskModel,
    onToggleComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val esExamen = tarea.tipo.equals("EXAMEN", ignoreCase = true)
    val (estadoTexto, estadoColor, estadoBg) = calcularEstadoVencimiento(tarea.fechaEntregaMillis, tarea.completada, esExamen)

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        corner = 16.dp,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = tarea.completada,
                onCheckedChange = { onToggleComplete() },
                colors = CheckboxDefaults.colors(
                    checkedColor = if (esExamen) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Fila con Etiqueta de Tipo (EXAMEN o TAREA) y el Curso
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (esExamen) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFFEBEE)
                        ) {
                            Text(
                                text = "🎯 EXAMEN",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC62828),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "📘 TAREA",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (tarea.curso.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = tarea.curso,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Título
                Text(
                    text = tarea.titulo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (tarea.completada) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (tarea.completada) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Descripción si tiene
                if (tarea.descripcion.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tarea.descripcion,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Fecha y Estado de vencimiento
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatoFechaHora(tarea.fechaEntregaMillis),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = estadoBg
                    ) {
                        Text(
                            text = estadoTexto,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = estadoColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                // Recordatorio activo
                val recordatorioMillis = when {
                    tarea.recordatorioFechaHoraMillis != null && tarea.recordatorioFechaHoraMillis > 0 -> tarea.recordatorioFechaHoraMillis
                    tarea.recordatorioMinutosAntes >= 0 -> tarea.fechaEntregaMillis - (tarea.recordatorioMinutosAntes * 60 * 1000L)
                    else -> null
                }

                if (recordatorioMillis != null && !tarea.completada) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            tint = if (esExamen) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Aviso: ${formatoFechaHora(recordatorioMillis)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (esExamen) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Opciones",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Editar") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskFormDialog(
    taskToEdit: TaskModel?,
    cursosSugeridos: List<String>,
    loadingCursos: Boolean,
    onDismiss: () -> Unit,
    onSave: (TaskModel) -> Unit
) {
    val context = LocalContext.current
    var tipo by remember { mutableStateOf(taskToEdit?.tipo ?: "TAREA") }
    val esExamen = tipo == "EXAMEN"

    var titulo by remember { mutableStateOf(taskToEdit?.titulo ?: "") }
    var curso by remember { mutableStateOf(taskToEdit?.curso ?: "") }
    var descripcion by remember { mutableStateOf(taskToEdit?.descripcion ?: "") }

    var showCoursePicker by remember { mutableStateOf(false) }
    var manualCourseInput by remember { mutableStateOf(false) }

    val calendar = remember {
        Calendar.getInstance().apply {
            if (taskToEdit != null) {
                timeInMillis = taskToEdit.fechaEntregaMillis
            } else {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
    }

    var fechaMillis by remember { mutableLongStateOf(calendar.timeInMillis) }

    var activarAviso by remember {
        mutableStateOf(
            taskToEdit?.recordatorioFechaHoraMillis != null && taskToEdit.recordatorioFechaHoraMillis > 0 ||
                    (taskToEdit?.recordatorioMinutosAntes ?: -1) >= 0 ||
                    taskToEdit == null
        )
    }

    var fechaAvisoMillis by remember {
        val inicial = taskToEdit?.recordatorioFechaHoraMillis
            ?: if (taskToEdit != null && taskToEdit.recordatorioMinutosAntes >= 0) {
                taskToEdit.fechaEntregaMillis - (taskToEdit.recordatorioMinutosAntes * 60 * 1000L)
            } else {
                val calAviso = Calendar.getInstance().apply {
                    timeInMillis = calendar.timeInMillis
                    add(Calendar.HOUR_OF_DAY, -2)
                }
                if (calAviso.timeInMillis <= System.currentTimeMillis()) {
                    Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }.timeInMillis
                } else {
                    calAviso.timeInMillis
                }
            }
        mutableLongStateOf(inicial)
    }

    fun abrirDatePicker() {
        val c = Calendar.getInstance().apply { timeInMillis = fechaMillis }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val nuevoCal = Calendar.getInstance().apply {
                    timeInMillis = fechaMillis
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                fechaMillis = nuevoCal.timeInMillis
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun abrirTimePicker() {
        val c = Calendar.getInstance().apply { timeInMillis = fechaMillis }
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val nuevoCal = Calendar.getInstance().apply {
                    timeInMillis = fechaMillis
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                }
                fechaMillis = nuevoCal.timeInMillis
            },
            c.get(Calendar.HOUR_OF_DAY),
            c.get(Calendar.MINUTE),
            false
        ).show()
    }

    fun abrirDatePickerAviso() {
        val c = Calendar.getInstance().apply { timeInMillis = fechaAvisoMillis }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val nuevoCal = Calendar.getInstance().apply {
                    timeInMillis = fechaAvisoMillis
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                fechaAvisoMillis = nuevoCal.timeInMillis
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun abrirTimePickerAviso() {
        val c = Calendar.getInstance().apply { timeInMillis = fechaAvisoMillis }
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val nuevoCal = Calendar.getInstance().apply {
                    timeInMillis = fechaAvisoMillis
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                }
                fechaAvisoMillis = nuevoCal.timeInMillis
            },
            c.get(Calendar.HOUR_OF_DAY),
            c.get(Calendar.MINUTE),
            false
        ).show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(
                    text = if (taskToEdit == null) "Nuevo Pendiente" else "Editar Pendiente",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Selector de Tipo: TAREA vs EXAMEN
                Text(
                    text = "Tipo de actividad:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = tipo == "TAREA",
                        onClick = { tipo = "TAREA" },
                        label = { Text("📘 Tarea / Trabajo", fontWeight = if (tipo == "TAREA") FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    FilterChip(
                        selected = tipo == "EXAMEN",
                        onClick = { tipo = "EXAMEN" },
                        label = { Text("🎯 Examen / Evaluación", fontWeight = if (tipo == "EXAMEN") FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Selector de Curso OBLIGATORIAMENTE con las opciones del ciclo actual
                Text(
                    text = "Curso del ciclo:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (!manualCourseInput) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCoursePicker = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = null,
                                    tint = if (curso.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (curso.isNotBlank()) curso else if (loadingCursos) "Cargando tus cursos..." else "Toca para seleccionar tu curso",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (curso.isNotBlank()) FontWeight.Bold else FontWeight.Normal,
                                    color = if (curso.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Ver cursos",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = curso,
                        onValueChange = { curso = it },
                        label = { Text("Escribe el nombre del curso") },
                        placeholder = { Text("Ej. Proyecto de Tesis") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            TextButton(onClick = { manualCourseInput = false }) {
                                Text("Ver lista", fontSize = 12.sp)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Título
                OutlinedTextField(
                    value = titulo,
                    onValueChange = { titulo = it },
                    label = { Text(if (esExamen) "¿Qué examen es?" else "¿Qué tarea tienes?") },
                    placeholder = { Text(if (esExamen) "Ej. Examen Parcial, Práctica 2" else "Ej. Informe Lab 3, Ensayo, etc.") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Sugerencias rápidas para exámenes
                if (esExamen) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val titulosExamen = listOf("Examen Parcial", "Examen Final", "Práctica Calificada", "Exposición Final", "Sustitutorio")
                        items(titulosExamen) { sug ->
                            AssistChip(
                                onClick = { titulo = sug },
                                label = { Text(sug, fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Selector de Fecha y Hora
                Text(
                    text = if (esExamen) "Fecha y hora del examen:" else "Fecha y hora de entrega:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedCard(
                        onClick = { abrirDatePicker() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(fechaMillis)),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    OutlinedCard(
                        onClick = { abrirTimePicker() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(fechaMillis)),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Descripción / Notas
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text("Notas o temas a estudiar (opcional)") },
                    placeholder = { Text(if (esExamen) "Capítulos 1 al 4, llevar calculadora..." else "Subir en PDF, trabajo grupal...") },
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Avisarme al celular: Switch y Selectores exactos de Fecha y Hora
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Avisarme al celular:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (activarAviso) "Notificación en la fecha y hora que elijas" else "Sin recordatorio automático",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = activarAviso,
                        onCheckedChange = { activarAviso = it }
                    )
                }

                if (activarAviso) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Fecha y hora del aviso:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedCard(
                            onClick = { abrirDatePickerAviso() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(fechaAvisoMillis)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        OutlinedCard(
                            onClick = { abrirTimePickerAviso() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(fechaAvisoMillis)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Te llegará el aviso: ${formatoFechaHora(fechaAvisoMillis)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Botones Cancelar / Guardar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (titulo.isBlank()) {
                                Toast.makeText(context, "Ingresa un título para ${if (esExamen) "el examen" else "la tarea"}", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (curso.isBlank()) {
                                Toast.makeText(context, "Por favor selecciona el curso", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val avisoFinal = if (activarAviso) fechaAvisoMillis else null
                            if (activarAviso && fechaAvisoMillis <= System.currentTimeMillis()) {
                                Toast.makeText(context, "La fecha y hora del aviso deben ser posteriores a este momento", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            val tarea = taskToEdit?.copy(
                                tipo = tipo,
                                titulo = titulo.trim(),
                                curso = curso.trim(),
                                descripcion = descripcion.trim(),
                                fechaEntregaMillis = fechaMillis,
                                recordatorioFechaHoraMillis = avisoFinal,
                                recordatorioMinutosAntes = -1
                            ) ?: TaskModel(
                                usuario = "",
                                tipo = tipo,
                                titulo = titulo.trim(),
                                curso = curso.trim(),
                                descripcion = descripcion.trim(),
                                fechaEntregaMillis = fechaMillis,
                                recordatorioFechaHoraMillis = avisoFinal,
                                recordatorioMinutosAntes = -1
                            )
                            onSave(tarea)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (esExamen) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Guardar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Selector Modal de Cursos del Ciclo
    if (showCoursePicker) {
        Dialog(onDismissRequest = { showCoursePicker = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Selecciona tu Curso",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Cursos matriculados en tu ciclo actual",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (cursosSugeridos.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (loadingCursos) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            } else {
                                Text(
                                    text = "No se encontraron cursos en caché.\nPuedes escribirlo manualmente.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(cursosSugeridos) { nombreCurso ->
                                val esSeleccionado = curso.equals(nombreCurso, ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (esSeleccionado) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            curso = nombreCurso
                                            showCoursePicker = false
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Book,
                                            contentDescription = null,
                                            tint = if (esSeleccionado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = nombreCurso,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (esSeleccionado) FontWeight.Bold else FontWeight.Medium,
                                            color = if (esSeleccionado) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (esSeleccionado) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Seleccionado",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                manualCourseInput = true
                                showCoursePicker = false
                            }
                        ) {
                            Text("+ Escribir otro nombre", fontSize = 12.sp)
                        }

                        TextButton(onClick = { showCoursePicker = false }) {
                            Text("Cerrar")
                        }
                    }
                }
            }
        }
    }
}

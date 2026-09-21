package com.example.upaos.ui.tareas

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.TaskAlt
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
import com.example.upaos.data.local.GradesCache
import com.example.upaos.data.local.TasksPreferences
import com.example.upaos.data.model.GradesResponse
import com.example.upaos.data.model.TaskModel
import com.example.upaos.service.TaskReminderManager
import com.example.upaos.ui.components.AppCard
import com.google.gson.Gson
import kotlinx.coroutines.launch
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

private fun calcularEstadoVencimiento(millis: Long, completada: Boolean): Triple<String, Color, Color> {
    if (completada) {
        return Triple("Completada", Color(0xFF4CAF50), Color(0xFFE8F5E9))
    }
    val ahora = System.currentTimeMillis()
    val diff = millis - ahora

    return when {
        diff < 0 -> Triple("Vencida", Color(0xFFD32F2F), Color(0xFFFFEBEE))
        diff <= 24 * 3600 * 1000L -> Triple("Vence pronto", Color(0xFFE65100), Color(0xFFFFF3E0))
        diff <= 48 * 3600 * 1000L -> Triple("En 2 días", Color(0xFFF57C00), Color(0xFFFFF8E1))
        else -> {
            val dias = (diff / (24 * 3600 * 1000L)).toInt()
            Triple("En $dias días", Color(0xFF1976D2), Color(0xFFE3F2FD))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TareasScreen(
    usuario: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val tasksPrefs = remember { TasksPreferences(context) }
    val scope = rememberCoroutineScope()
    val safeUser = usuario ?: "anonimo"

    val tasks by tasksPrefs.getTasksFlow(safeUser).collectAsState(initial = emptyList())
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Pendientes, 1: Completadas, 2: Todas

    var showDialog by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<TaskModel?>(null) }
    var taskToDelete by remember { mutableStateOf<TaskModel?>(null) }

    // Cursos disponibles para sugerir
    var cursosDisponibles by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(safeUser) {
        try {
            val cache = GradesCache(context)
            val periodos = listOf("202610", "202620", "202520", "202510")
            for (p in periodos) {
                val json = cache.cargar("${p}_UG") ?: cache.cargar("${p}_EPG")
                if (!json.isNullOrBlank()) {
                    val resp = Gson().fromJson(json, GradesResponse::class.java)
                    val nombres = resp.cursos.mapNotNull { it.displayNombre }.filter { it.isNotBlank() }
                    if (nombres.isNotEmpty()) {
                        cursosDisponibles = nombres
                        break
                    }
                }
            }
        } catch (_: Exception) {}
    }

    val tareasPendientes = remember(tasks) { tasks.filter { !it.completada }.sortedBy { it.fechaEntregaMillis } }
    val tareasCompletadas = remember(tasks) { tasks.filter { it.completada }.sortedByDescending { it.fechaEntregaMillis } }

    val tareasFiltradas = when (selectedTab) {
        0 -> tareasPendientes
        1 -> tareasCompletadas
        else -> tasks.sortedBy { it.fechaEntregaMillis }
    }

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
                            text = "Mis Tareas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (tareasPendientes.isNotEmpty()) {
                                "${tareasPendientes.size} pendiente${if (tareasPendientes.size > 1) "s" else ""}"
                            } else {
                                "Al día"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Nueva tarea")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Nueva Tarea", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tabs de filtro
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
                            if (tareasPendientes.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                    Text("${tareasPendientes.size}")
                                }
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Completadas", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
                            if (tareasCompletadas.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                                    Text("${tareasCompletadas.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
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

            Spacer(modifier = Modifier.height(8.dp))

            if (tareasFiltradas.isEmpty()) {
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
                            text = when (selectedTab) {
                                1 -> "Aún no tienes tareas completadas"
                                0 -> "¡Estás al día!"
                                else -> "No hay tareas registradas"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when (selectedTab) {
                                1 -> "Las tareas que marques como terminadas aparecerán aquí."
                                0 -> "No tienes deberes pendientes por ahora."
                                else -> "Crea una nueva tarea con el botón de abajo para recordar tus entregas."
                            },
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
                    items(tareasFiltradas, key = { it.id }) { tarea ->
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

    // Modal para crear / editar tarea
    if (showDialog) {
        TaskFormDialog(
            taskToEdit = taskToEdit,
            cursosSugeridos = cursosDisponibles,
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
                    Toast.makeText(context, "Tarea guardada", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Diálogo de confirmación para eliminar
    taskToDelete?.let { tarea ->
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("Eliminar tarea", fontWeight = FontWeight.Bold) },
            text = { Text("¿Estás seguro de que deseas eliminar '${tarea.titulo}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            TaskReminderManager.cancelReminder(context, tarea.id)
                            tasksPrefs.deleteTask(tarea.id, safeUser)
                            taskToDelete = null
                            Toast.makeText(context, "Tarea eliminada", Toast.LENGTH_SHORT).show()
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
    val (estadoTexto, estadoColor, estadoBg) = calcularEstadoVencimiento(tarea.fechaEntregaMillis, tarea.completada)

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        corner = 16.dp,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Checkbox para completar
            Checkbox(
                checked = tarea.completada,
                onCheckedChange = { onToggleComplete() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
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

                // Curso si está presente
                if (tarea.curso.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = tarea.curso,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Descripción
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

                    // Badge de urgencia / completado
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
                if (tarea.recordatorioMinutosAntes >= 0 && !tarea.completada) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (tarea.recordatorioMinutosAntes) {
                                0 -> "Recordatorio al momento"
                                60 -> "Recordatorio 1h antes"
                                1440 -> "Recordatorio 1 día antes"
                                2880 -> "Recordatorio 2 días antes"
                                else -> "Recordatorio programado"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Menú de opciones (Editar / Eliminar)
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
    onDismiss: () -> Unit,
    onSave: (TaskModel) -> Unit
) {
    val context = LocalContext.current
    var titulo by remember { mutableStateOf(taskToEdit?.titulo ?: "") }
    var curso by remember { mutableStateOf(taskToEdit?.curso ?: "") }
    var descripcion by remember { mutableStateOf(taskToEdit?.descripcion ?: "") }

    // Fecha por defecto: hoy a las 23:59 o la guardada
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
    var recordatorioMinutos by remember { mutableIntStateOf(taskToEdit?.recordatorioMinutosAntes ?: 1440) }
    var cursoMenuExpanded by remember { mutableStateOf(false) }

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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = if (taskToEdit == null) "Nueva Tarea" else "Editar Tarea",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Título
                OutlinedTextField(
                    value = titulo,
                    onValueChange = { titulo = it },
                    label = { Text("¿Qué tarea tienes?") },
                    placeholder = { Text("Ej. Informe Lab 2, Exposición, etc.") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Curso
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = curso,
                        onValueChange = { curso = it },
                        label = { Text("Curso (opcional)") },
                        placeholder = { Text("Ej. Sistemas Operativos") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (cursosSugeridos.isNotEmpty()) {
                                IconButton(onClick = { cursoMenuExpanded = true }) {
                                    Icon(Icons.Default.Book, contentDescription = "Elegir curso", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (cursosSugeridos.isNotEmpty()) {
                        DropdownMenu(
                            expanded = cursoMenuExpanded,
                            onDismissRequest = { cursoMenuExpanded = false }
                        ) {
                            cursosSugeridos.forEach { nombreCurso ->
                                DropdownMenuItem(
                                    text = { Text(nombreCurso, fontSize = 13.sp) },
                                    onClick = {
                                        curso = nombreCurso
                                        cursoMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Sugerencias de cursos en Chips horizontales si existen
                if (cursosSugeridos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(cursosSugeridos.take(5)) { nombreCurso ->
                            val corto = if (nombreCurso.length > 18) nombreCurso.take(16) + "..." else nombreCurso
                            AssistChip(
                                onClick = { curso = nombreCurso },
                                label = { Text(corto, fontSize = 11.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Fecha y Hora
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

                Spacer(modifier = Modifier.height(12.dp))

                // Descripción / Notas
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text("Notas o detalles (opcional)") },
                    placeholder = { Text("En formato PDF, en grupos de 3, etc.") },
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Recordatorio anticipado
                Text(
                    text = "Avisarme al celular:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val opciones = listOf(
                        -1 to "Sin aviso",
                        60 to "1h antes",
                        1440 to "1 día antes",
                        2880 to "2 días antes"
                    )
                    items(opciones) { (minutos, texto) ->
                        FilterChip(
                            selected = recordatorioMinutos == minutos,
                            onClick = { recordatorioMinutos = minutos },
                            label = { Text(texto, fontSize = 12.sp) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

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
                                Toast.makeText(context, "Ingresa un título para la tarea", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val tarea = taskToEdit?.copy(
                                titulo = titulo.trim(),
                                curso = curso.trim(),
                                descripcion = descripcion.trim(),
                                fechaEntregaMillis = fechaMillis,
                                recordatorioMinutosAntes = recordatorioMinutos
                            ) ?: TaskModel(
                                usuario = "",
                                titulo = titulo.trim(),
                                curso = curso.trim(),
                                descripcion = descripcion.trim(),
                                fechaEntregaMillis = fechaMillis,
                                recordatorioMinutosAntes = recordatorioMinutos
                            )
                            onSave(tarea)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Guardar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

package com.example.upaos.ui.canvas

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AssignmentLate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.upaos.data.api.CanvasApiClient
import com.example.upaos.data.local.CanvasCache
import com.example.upaos.data.local.TokenManager
import com.example.upaos.data.model.CanvasTodoItem
import com.example.upaos.ui.components.AppCard
import com.example.upaos.ui.components.EmptyState
import com.example.upaos.ui.components.ErrorView
import com.example.upaos.ui.components.ModernTextField
import com.example.upaos.ui.components.PrimaryButton
import com.example.upaos.ui.components.RefreshableContent
import com.example.upaos.ui.components.StatusBadge
import com.example.upaos.ui.components.cursoColor
import com.example.upaos.ui.components.toTitleCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    } catch (_: Exception) {
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

@Composable
fun CanvasContent(
    token: String,
    usuario: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokenManager = remember { TokenManager(context) }
    val cache = remember { CanvasCache(context) }

    val userKey = usuario ?: "anonimo"
    var savedCanvasToken by remember(usuario) { mutableStateOf(tokenManager.getCanvasToken(userKey)) }
    var isSetupMode by remember(savedCanvasToken) { mutableStateOf(savedCanvasToken.isNullOrBlank()) }

    var items by remember { mutableStateOf<List<CanvasTodoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isTokenExpired by remember { mutableStateOf(false) }
    var lastUpdatedMillis by remember { mutableStateOf<Long?>(null) }
    var isOffline by remember { mutableStateOf(false) }

    var selectedFilter by remember { mutableIntStateOf(0) } // 0: Todos, 1: Tareas, 2: Exámenes

    fun sortItems(list: List<CanvasTodoItem>): List<CanvasTodoItem> {
        return list.sortedWith(
            compareBy<CanvasTodoItem> { item ->
                parseIsoDate(item.dueAt) ?: Long.MAX_VALUE
            }.thenBy { it.title }
        )
    }

    fun fetchTodo(isManualRefresh: Boolean = false) {
        val currentToken = savedCanvasToken
        if (currentToken.isNullOrBlank()) {
            isSetupMode = true
            return
        }

        scope.launch {
            if (isManualRefresh) isRefreshing = true else if (items.isEmpty()) isLoading = true
            errorMessage = null
            isTokenExpired = false

            try {
                val response = withContext(Dispatchers.IO) {
                    CanvasApiClient.apiService.getTodoList("Bearer $currentToken")
                }

                if (response.isSuccessful && response.body() != null) {
                    val rawItems = response.body()!!
                    val sorted = sortItems(rawItems)
                    items = sorted
                    isOffline = false
                    lastUpdatedMillis = System.currentTimeMillis()
                    cache.guardar(userKey, sorted)
                } else if (response.code() == 401) {
                    isTokenExpired = true
                    errorMessage = "Tu token de Canvas ha expirado o no es válido."
                } else {
                    errorMessage = "Error en Canvas (${response.code()}): ${response.message()}"
                }
            } catch (e: Exception) {
                isOffline = true
                if (items.isEmpty()) {
                    errorMessage = "Sin conexión a internet. No se pudieron cargar los pendientes de Canvas."
                }
            } finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    // Cargar caché local primero
    LaunchedEffect(userKey, savedCanvasToken) {
        if (!savedCanvasToken.isNullOrBlank()) {
            val (cachedList, cachedTime) = cache.cargar(userKey)
            if (cachedList.isNotEmpty()) {
                items = sortItems(cachedList)
                lastUpdatedMillis = cachedTime
            }
            fetchTodo()
        }
    }

    if (isSetupMode) {
        CanvasSetupView(
            usuario = userKey,
            initialToken = savedCanvasToken ?: "",
            onTokenSaved = { newToken ->
                savedCanvasToken = newToken
                isSetupMode = false
                fetchTodo()
            },
            onCancel = if (!savedCanvasToken.isNullOrBlank()) {
                { isSetupMode = false }
            } else null
        )
    } else {
        RefreshableContent(
            isRefreshing = isRefreshing,
            onRefresh = { fetchTodo(isManualRefresh = true) },
            modifier = modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                // Header con botón para cambiar token y estado
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pendientes Canvas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val subtexto = when {
                            isOffline -> "Modo sin conexión (datos guardados)"
                            lastUpdatedMillis != null -> formatActualizado(lastUpdatedMillis)
                            else -> "Sincronizado con Canvas LMS"
                        }
                        Text(
                            text = subtexto,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOffline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FilledTonalIconButton(
                        onClick = { isSetupMode = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = "Cambiar token de Canvas",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Banner de error o token expirado
                if (isTokenExpired) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AppCard(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Token inválido o expirado",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "Tu token personal de Canvas ya no tiene acceso. Actualízalo para volver a sincronizar.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { isSetupMode = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Text("Actualizar Token", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (errorMessage != null && items.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ErrorView(
                        message = errorMessage!!,
                        onRetry = { fetchTodo() }
                    )
                }

                // Filtros de pendientes
                if (items.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val totalTareas = items.count { !it.isQuiz }
                    val totalExamenes = items.count { it.isQuiz }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = selectedFilter == 0,
                                onClick = { selectedFilter = 0 },
                                label = { Text("Todos (${items.size})", fontSize = 12.sp) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedFilter == 1,
                                onClick = { selectedFilter = 1 },
                                label = { Text("Tareas ($totalTareas)", fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null, modifier = Modifier.size(15.dp))
                                }
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedFilter == 2,
                                onClick = { selectedFilter = 2 },
                                label = { Text("Exámenes ($totalExamenes)", fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Quiz, contentDescription = null, modifier = Modifier.size(15.dp))
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Lista de pendientes
                val filteredItems = remember(items, selectedFilter) {
                    when (selectedFilter) {
                        1 -> items.filter { !it.isQuiz }
                        2 -> items.filter { it.isQuiz }
                        else -> items
                    }
                }

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Consultando pendientes en Canvas...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    filteredItems.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyState(
                                icon = Icons.Filled.CheckCircle,
                                title = "¡Al día en Canvas!",
                                subtitle = if (items.isEmpty())
                                    "No tienes tareas ni exámenes pendientes por entregar en Canvas LMS."
                                else
                                    "No hay entregas pendientes en esta categoría."
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredItems, key = { it.assignment?.id ?: it.quiz?.id ?: it.title.hashCode() }) { item ->
                                CanvasTodoItemCard(item = item)
                            }
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CanvasTodoItemCard(item: CanvasTodoItem) {
    val context = LocalContext.current
    val esExamen = item.isQuiz
    val (dueText, dueColor) = remember(item.dueAt) { formatCanvasDueDate(item.dueAt) }
    val courseColor = remember(item.courseDisplayName) { cursoColor(item.courseDisplayName) }

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
        onClick = if (!item.webUrl.isNullOrBlank()) {
            {
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(item.webUrl))
                    context.startActivity(browserIntent)
                } catch (e: Exception) {
                    Toast.makeText(context, "No se pudo abrir el enlace", Toast.LENGTH_SHORT).show()
                }
            }
        } else null
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

        if (!item.webUrl.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Abrir en Canvas",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.OpenInBrowser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
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
    val scope = rememberCoroutineScope()
    val tokenManager = remember { TokenManager(context) }
    val clipboardManager = LocalClipboardManager.current

    var tokenInput by remember { mutableStateOf(initialToken) }
    var isValidating by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var userNameDetected by remember { mutableStateOf<String?>(null) }

    fun validarYGuardar() {
        val cleanToken = tokenInput.trim()
        if (cleanToken.isBlank()) {
            errorText = "Por favor ingresa un token de acceso."
            return
        }

        isValidating = true
        errorText = null

        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    CanvasApiClient.apiService.getUserSelf("Bearer $cleanToken")
                }

                if (resp.isSuccessful && resp.body() != null) {
                    val user = resp.body()!!
                    userNameDetected = user.name ?: user.shortName
                    // Persistir en EncryptedSharedPreferences para la cuenta activa
                    tokenManager.saveCanvasToken(usuario, cleanToken)
                    Toast.makeText(context, "¡Conectado con Canvas como ${userNameDetected}!", Toast.LENGTH_LONG).show()
                    onTokenSaved(cleanToken)
                } else if (resp.code() == 401) {
                    errorText = "Token inválido o expirado. Verifica que lo hayas copiado completo."
                } else {
                    errorText = "Error al validar token (${resp.code()}): ${resp.message()}"
                }
            } catch (e: Exception) {
                errorText = "Error de conexión: no se pudo contactar a Canvas LMS (${e.localizedMessage ?: "Revisa tu internet"})."
            } finally {
                isValidating = false
            }
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
            text = "Sincroniza tus tareas y exámenes pendientes directamente desde la plataforma oficial de la universidad.",
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
                    } catch (_: Exception) {}
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
                errorText = null
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
                            errorText = null
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

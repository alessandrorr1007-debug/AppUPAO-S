package com.example.upaos.ui.canvas

import androidx.compose.runtime.*
import androidx.compose.material3.*
import com.example.upaos.data.canvas.CanvasError
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

internal val canvasTokenRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
@Composable
internal fun CanvasResourceFeedback(state: CanvasResourceState<*>, retry: () -> Unit) {
    var dismissed by remember(state.failure, state.error) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (state.error != null && !dismissed) AlertDialog(
        onDismissRequest = { dismissed = true }, title = { Text(state.error) },
        confirmButton = { TextButton(onClick = {
            dismissed = true
            if (state.failure == CanvasError.TOKEN) scope.launch { canvasTokenRequests.emit(Unit) } else retry()
        }) { Text(if (state.failure == CanvasError.TOKEN) "Actualizar token" else "Reintentar") } },
        dismissButton = { TextButton(onClick = { dismissed = true }) { Text("Cerrar") } }
    )
}

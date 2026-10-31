package com.example.upaos.ui.canvas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.upaos.data.canvas.canvasError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CanvasWriteState(val sending: Boolean = false, val confirmed: Boolean = false,
    val uncertain: Boolean = false, val error: String? = null)

/** One deliberate send, no automatic retry or optimistic success. Scope includes account/token. */
class CanvasWriteViewModel : ViewModel() {
    private val mutable = MutableStateFlow(CanvasWriteState())
    val state = mutable.asStateFlow()
    fun send(action: suspend () -> Unit) {
        if (state.value.sending || state.value.confirmed || state.value.uncertain) return
        mutable.value = CanvasWriteState(sending = true)
        viewModelScope.launch {
            try {
                action()
                mutable.value = CanvasWriteState(confirmed = true)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val rejected = e is retrofit2.HttpException && e.code() in setOf(400, 401, 403, 404, 422, 429)
                mutable.value = CanvasWriteState(uncertain = !rejected, error = if (rejected) canvasError(e).text
                    else "No se pudo confirmar el envío. Actualiza el hilo antes de intentar otra publicación; podría haberse enviado.")
            }
        }
    }
    fun newDraft() { if (!state.value.sending) mutable.value = CanvasWriteState() }
}

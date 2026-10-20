package com.example.upaos.ui.canvas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.upaos.data.canvas.CanvasSetupRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CanvasSetupState(
    val loading: Boolean = false, val error: String? = null,
    val userName: String? = null, val savedToken: String? = null
)
class CanvasSetupViewModel(private val repository: CanvasSetupRepository) : ViewModel() {
    private val mutable = MutableStateFlow(CanvasSetupState())
    val state = mutable.asStateFlow()
    private var request: Job? = null
    fun validate(input: String) {
        if (request?.isActive == true) return
        val token = input.trim()
        if (token.isBlank()) {
            mutable.value=state.value.copy(error="Por favor ingresa un token de acceso.")
            return
        }
        request=viewModelScope.launch(start=CoroutineStart.LAZY) {
            mutable.value=state.value.copy(loading=true,error=null)
            try {
                val user=repository.validateAndSave(token)
                ensureActive()
                mutable.value=state.value.copy(userName=user.name ?: user.shortName,savedToken=token)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value=state.value.copy(error=e.message ?: "Error de conexión con Canvas LMS.") }
            finally { mutable.value=state.value.copy(loading=false) }
        }
        request!!.start()
    }
    fun clearError() { mutable.value=state.value.copy(error=null) }
    fun consumeSavedToken() { mutable.value=state.value.copy(savedToken=null) }
}

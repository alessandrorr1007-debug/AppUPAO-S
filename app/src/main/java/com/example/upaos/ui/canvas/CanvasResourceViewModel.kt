package com.example.upaos.ui.canvas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.upaos.data.canvas.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CanvasResourceState<T>(
    val data: T,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val failure: CanvasError? = null
)

class CanvasResourceViewModel<T>(
    private val repository: CanvasResourceRepository<T>,
    initial: T,
    private val restore: (suspend () -> T?)? = null
) : ViewModel() {
    private val mutable = MutableStateFlow(CanvasResourceState(initial))
    val state = mutable.asStateFlow()
    private var request: Job? = null
    private var restored = false
    init { refresh() }

    fun refresh(manual: Boolean = false) {
        if (request?.isActive == true) return
        request = viewModelScope.launch(start = CoroutineStart.LAZY) {
            mutable.value = state.value.copy(loading = !manual, refreshing = manual, error = null, failure = null)
            try {
                if (!restored) {
                    restored = true
                    try { restore?.invoke()?.let { mutable.value = state.value.copy(data = it, loading = false, refreshing = true) } }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { /* A corrupt cache must not block the live request. */ }
                }
                val data = canvasReadWithBackoff { repository.load() }
                ensureActive()
                mutable.value = state.value.copy(data = data)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                mutable.value = state.value.copy(error = canvasError(e).text, failure = canvasError(e))
            } finally {
                mutable.value = state.value.copy(loading = false, refreshing = false)
            }
        }
        request!!.start()
    }
}

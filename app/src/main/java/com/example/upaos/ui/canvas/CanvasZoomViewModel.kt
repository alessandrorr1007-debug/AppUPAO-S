package com.example.upaos.ui.canvas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.upaos.data.canvas.*
import com.example.upaos.data.model.CourseZoomMeeting
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class CanvasZoomViewModel(private val repository: CanvasZoomRepository) : ViewModel() {
    private val mutable = MutableStateFlow(CanvasResourceState(CanvasZoomSnapshot()))
    val state = mutable.asStateFlow()
    private var request: Job? = null
    init { refresh() }
    fun refresh() {
        if (request?.isActive == true) return
        request = viewModelScope.launch(start = CoroutineStart.LAZY) {
            mutable.value = state.value.copy(loading=true,error=null)
            try {
                val next = repository.load()
                ensureActive()
                val old = state.value.data
                val retained = old.meetings.filter { it.origin in next.failedSources }
                val merged = next.copy(
                    meetings=(next.meetings + retained).distinctBy { it.joinUrl.trim().lowercase() },
                    ltiUrl=if ("TABS" in next.failedSources) old.ltiUrl else next.ltiUrl)
                val failure = next.failure ?: CanvasError.INVALID.takeIf { next.failedSources.isNotEmpty() }
                mutable.value = state.value.copy(data=merged,
                    error=failure?.text, failure=failure)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value=state.value.copy(error=canvasError(e).text, failure=canvasError(e)) }
            finally { mutable.value=state.value.copy(loading=false) }
        }
        request!!.start()
    }
    fun save(meeting: CourseZoomMeeting) { repository.save(meeting) }
    fun delete() { repository.delete() }
}

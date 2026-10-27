package com.example.upaos.ui.canvas

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.upaos.data.api.CanvasApiClient
import com.example.upaos.data.canvas.*
import com.example.upaos.data.local.CanvasCache
import com.example.upaos.data.local.TokenManager
import java.security.MessageDigest

internal fun <T : ViewModel> canvasViewModelFactory(make: () -> T) = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <V : ViewModel> create(modelClass: Class<V>): V = make() as V
}

internal fun canvasViewModelKey(flow: String, user: String, session: String?, token: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        .joinToString("") { "%02x".format(it) }
    return "canvas:$user:$session:$flow:$digest"
}

@Composable
internal fun rememberCanvasRepository(token: String, user: String? = null): DefaultCanvasRepository {
    val app = LocalContext.current.applicationContext
    val tokens = remember(app) { TokenManager(app) }
    val owner = user ?: tokens.getSavedUser().orEmpty()
    val session = tokens.getSessionId()
    return remember(app, owner, session, token) {
        DefaultCanvasRepository(owner, token, CanvasApiClient.pagedApiService, CanvasCache(app)) {
            tokens.getSavedUser() == owner && tokens.getSessionId() == session && tokens.getCanvasToken(owner) == token
        }
    }
}

@Composable
internal fun <T> canvasResourceViewModel(
    flow: String, token: String, initial: T, load: suspend CanvasCourseRepository.() -> T
): CanvasResourceViewModel<T> {
    val app = LocalContext.current.applicationContext
    val tokens = remember(app) { TokenManager(app) }
    val repository = rememberCanvasRepository(token)
    return viewModel(
        key = canvasViewModelKey(flow, tokens.getSavedUser().orEmpty(), tokens.getSessionId(), token),
        factory = canvasViewModelFactory {
            CanvasResourceViewModel(CanvasResourceRepository { repository.load() }, initial)
        })
}

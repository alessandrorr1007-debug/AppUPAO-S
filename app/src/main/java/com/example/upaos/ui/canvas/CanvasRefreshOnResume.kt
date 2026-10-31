package com.example.upaos.ui.canvas

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun CanvasRefreshOnResume(refresh: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(refresh)
    DisposableEffect(owner) {
        var leftApp = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) leftApp = true
            if (event == Lifecycle.Event.ON_RESUME && leftApp) { leftApp = false; latest() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

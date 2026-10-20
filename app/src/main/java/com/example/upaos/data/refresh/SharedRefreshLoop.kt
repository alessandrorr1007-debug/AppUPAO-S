package com.example.upaos.data.refresh

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** One foreground scheduler; its scope and last deadline survive screen recreation. */
class SharedRefreshLoop(
    private val scope: CoroutineScope,
    private val refresh: suspend () -> Unit,
    private val intervalMillis: Long = FIVE_MINUTES,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
    private val nextIntervalMillis: () -> Long = { intervalMillis }
) {
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var timer: Job? = null
    private var round: Job? = null
    private var attempted = false
    private var nextAt = 0L

    fun setForeground(active: Boolean) {
        if (!active) { timer?.cancel(); timer = null; return }
        if (timer?.isActive == true) return
        timer = scope.launch {
            if (!attempted) requestRefresh().join()
            while (isActive) {
                val remaining = (nextAt - clock()).coerceAtLeast(1L)
                if (withTimeoutOrNull(remaining) { wake.receive(); true } == null) {
                    requestRefresh().join()
                }
            }
        }
    }

    /** Coalesce simultaneous gestures/ticks into one round, then reset the deadline. */
    fun requestRefresh(): Job {
        round?.takeIf { it.isActive }?.let { return it }
        attempted = true
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try { refresh() } finally {
                nextAt = clock() + nextIntervalMillis().coerceAtLeast(1L)
                wake.trySend(Unit)
            }
        }
        round = job
        job.start()
        return job
    }

    companion object { const val FIVE_MINUTES = 5 * 60 * 1_000L }
}

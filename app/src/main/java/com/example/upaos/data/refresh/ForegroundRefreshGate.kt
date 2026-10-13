package com.example.upaos.data.refresh

import kotlinx.coroutines.sync.Mutex

/** Background notification workers keep their existing job, but never race the dashboard. */
object ForegroundRefreshGate {
    @Volatile var active = false
    val lock = Mutex()
}

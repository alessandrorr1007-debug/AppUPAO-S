package com.example.upaos.data.local

/** Serialize alarm publication against logout; invalidation always precedes task cancellation. */
object SessionCleanup {
    val lock = Any()
    fun publishIfCurrent(owner: com.example.upaos.service.ReminderOwner, current: () -> Pair<String?, String?>,
                         publish: () -> Unit): Boolean = synchronized(lock) {
        val (user, session) = current()
        if (!owner.matches(user, session)) return@synchronized false
        publish()
        true
    }

    fun logout(invalidateSession: () -> Unit, cancelCookies: () -> Unit, cancelReminders: () -> Unit,
               cancelSync: () -> Unit, cancelAgenda: () -> Unit) = synchronized(lock) {
        invalidateSession()
        cancelCookies()
        cancelReminders()
        cancelSync()
        cancelAgenda()
    }
}

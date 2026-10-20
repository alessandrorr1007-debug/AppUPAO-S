package com.example.upaos.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.*
import com.example.upaos.data.local.NotificationPreferences
import com.example.upaos.data.local.SessionCleanup
import com.example.upaos.data.local.TokenManager
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** One adaptive WorkManager chain. Exact alarms are reserved for class reminders. */
object SyncScheduler {
    const val ACTION_SYNC = "com.example.upaos.ACTION_SYNC_10MIN"
    private const val WORK_NAME = "upao_adaptive_sync"

    fun start(context: Context) = synchronized(SessionCleanup.lock) {
        removeLegacySchedules(context)
        enqueue(context, ExistingWorkPolicy.KEEP)
    }

    /** Called once by a completed worker; APPEND avoids cancelling that running worker. */
    fun scheduleNext(
        context: Context,
        fallosConsecutivos: Int = NotificationPreferences(context).syncConsecutiveFailures,
        esErrorFatal: Boolean = false
    ) = synchronized(SessionCleanup.lock) {
        enqueue(context, ExistingWorkPolicy.APPEND_OR_REPLACE, fallosConsecutivos, esErrorFatal)
    }

    private fun enqueue(
        context: Context,
        policy: ExistingWorkPolicy,
        failures: Int = NotificationPreferences(context).syncConsecutiveFailures,
        fatal: Boolean = false,
        immediate: Boolean = false
    ) {
        val tokens = TokenManager(context)
        val prefs = NotificationPreferences(context)
        if (!tokens.shouldKeepLoggedIn() || (!prefs.checkAsistenciaEnabled && !prefs.checkNotasEnabled)) {
            cancel(context)
            return
        }
        val delay = SyncIntervalPolicy.proximoIntervalo(
            ZonedDateTime.now(SyncIntervalPolicy.ZONE_LIMA),
            SyncIntervalPolicy.SyncContext(fallosConsecutivos = failures, esErrorFatal = fatal)
        )
        if (delay.isZero) return
        val request = OneTimeWorkRequestBuilder<AsistenciaWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(if (immediate) 0 else delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
    }

    fun runNow(context: Context) = synchronized(SessionCleanup.lock) {
        removeLegacySchedules(context)
        // A manual request replaces a delayed chain; cancellation propagates from any old round.
        enqueue(context, ExistingWorkPolicy.REPLACE, immediate = true)
    }

    fun cancel(context: Context) = synchronized(SessionCleanup.lock) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        removeLegacySchedules(context)
    }

    /** Migration from releases with three independent polling schedules. */
    private fun removeLegacySchedules(context: Context) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val intent = Intent(context, BackgroundSyncReceiver::class.java).apply { action = ACTION_SYNC }
        PendingIntent.getBroadcast(context, 9988, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
            alarms?.cancel(it)
            it.cancel()
        }
        for (name in listOf("upao_unified_periodic_sync", "upao_unified_onetime_sync",
            "upao_immediate_sync", "upao_asistencia_check", "upao_asistencia_5min_check")) {
            WorkManager.getInstance(context).cancelUniqueWork(name)
        }
    }
}

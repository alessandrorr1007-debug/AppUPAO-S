package com.example.upaos.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.*
import com.example.upaos.data.local.NotificationPreferences
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private const val TAG = "UPAO_SyncScheduler"
    const val ACTION_SYNC = "com.example.upaos.ACTION_SYNC_5MIN"
    private const val ALARM_REQUEST_CODE = 9988
    private const val PERIODIC_WORK_NAME = "upao_unified_periodic_sync"
    private const val ONE_TIME_WORK_NAME = "upao_unified_onetime_sync"
    const val INTERVAL_MINUTES = 5L

    /**
     * Inicia o actualiza la programación en segundo plano cada 5 minutos
     * usando AlarmManager (preciso y compatible con Doze Mode)
     * más un respaldo periódico en WorkManager.
     */
    fun start(context: Context) {
        val prefs = NotificationPreferences(context)
        if (!prefs.checkAsistenciaEnabled && !prefs.checkNotasEnabled) {
            Log.d(TAG, "Notificaciones de notas y asistencia desactivadas. No se programa alarma.")
            cancel(context)
            return
        }

        scheduleNextAlarm(context, INTERVAL_MINUTES)
        scheduleWorkManagerBackup(context)
    }

    /**
     * Programa la siguiente alarma exacta para dentro de [minutes] minutos.
     */
    fun scheduleNext(context: Context, minutes: Long = INTERVAL_MINUTES) {
        val prefs = NotificationPreferences(context)
        if (!prefs.checkAsistenciaEnabled && !prefs.checkNotasEnabled) {
            cancel(context)
            return
        }
        scheduleNextAlarm(context, minutes)
    }

    private fun scheduleNextAlarm(context: Context, minutes: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, BackgroundSyncReceiver::class.java).apply {
            action = ACTION_SYNC
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = System.currentTimeMillis() + (minutes * 60 * 1000L)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pendingIntent
                        )
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Alarma de sincronización programada para dentro de $minutes minutos (${triggerAtMillis}ms).")
        } catch (e: Exception) {
            Log.e(TAG, "Error programando alarma con AlarmManager: ${e.localizedMessage}")
            // Fallback a WorkManager OneTimeWork con initialDelay
            scheduleWorkManagerOneTime(context, minutes)
        }
    }

    private fun scheduleWorkManagerBackup(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // WorkManager periodic mínimo 15 min (respaldo del SO)
        val request = PeriodicWorkRequestBuilder<AsistenciaWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        Log.d(TAG, "Respaldo periódico en WorkManager configurado (cada 15 min).")
    }

    private fun scheduleWorkManagerOneTime(context: Context, minutes: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<AsistenciaWorker>()
            .setConstraints(constraints)
            .setInitialDelay(minutes, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val intent = Intent(context, BackgroundSyncReceiver::class.java).apply {
            action = ACTION_SYNC
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null && alarmManager != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }

        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(ONE_TIME_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork("upao_asistencia_check")
        WorkManager.getInstance(context).cancelUniqueWork("upao_asistencia_5min_check")
        Log.d(TAG, "Todas las sincronizaciones en segundo plano han sido canceladas.")
    }

    fun runNow(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<AsistenciaWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(request)
        Log.d(TAG, "Sincronización inmediata solicitada.")
    }
}

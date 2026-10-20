package com.example.upaos.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.*
import com.example.upaos.data.local.NotificationPreferences
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private const val TAG = "UPAO_SyncScheduler"
    const val ACTION_SYNC = "com.example.upaos.ACTION_SYNC_10MIN"
    private const val ALARM_REQUEST_CODE = 9988
    private const val PERIODIC_WORK_NAME = "upao_unified_periodic_sync"
    private const val ONE_TIME_WORK_NAME = "upao_unified_onetime_sync"

    /**
     * Inicia o actualiza la programación en segundo plano calculando el próximo
     * intervalo adaptativo con SyncIntervalPolicy.
     */
    fun start(context: Context) {
        synchronized(com.example.upaos.data.local.SessionCleanup.lock) {
            if (!com.example.upaos.data.local.TokenManager(context).shouldKeepLoggedIn()) {
                cancel(context)
                return
            }

        val prefs = NotificationPreferences(context)
        if (!prefs.checkAsistenciaEnabled && !prefs.checkNotasEnabled) {
            Log.d(TAG, "Notificaciones de notas y asistencia desactivadas. No se programa alarma.")
            cancel(context)
            return
        }

        scheduleNext(context)
        scheduleWorkManagerBackup(context)
    
        }
    }

    /**
     * Programa el siguiente ciclo de sincronización usando la política adaptativa SyncIntervalPolicy.
     *
     * @param context contexto de la aplicación.
     * @param fallosConsecutivos número de fallos consecutivos previos para backoff exponencial.
     * @param esErrorFatal si es true, no reprograma (evita loops por sesión expirada o cambios de formato).
     * @param minutosForzados si se especifica, fuerza una cantidad de minutos (para pruebas o compatibilidad).
     */
    fun scheduleNext(
        context: Context,
        fallosConsecutivos: Int = NotificationPreferences(context).syncConsecutiveFailures,
        esErrorFatal: Boolean = false,
        minutosForzados: Long? = null
    ) {
        synchronized(com.example.upaos.data.local.SessionCleanup.lock) {
            if (!com.example.upaos.data.local.TokenManager(context).shouldKeepLoggedIn()) {
                cancel(context)
                return
            }

        val prefs = NotificationPreferences(context)
        if (!prefs.checkAsistenciaEnabled && !prefs.checkNotasEnabled) {
            cancel(context)
            return
        }

        val minutes = if (minutosForzados != null) {
            minutosForzados
        } else {
            val syncContext = SyncIntervalPolicy.SyncContext(
                fallosConsecutivos = fallosConsecutivos,
                esErrorFatal = esErrorFatal
            )
            val duracion = SyncIntervalPolicy.proximoIntervalo(
                ahora = ZonedDateTime.now(SyncIntervalPolicy.ZONE_LIMA),
                contexto = syncContext
            )

            if (duracion.isZero) {
                Log.w(TAG, "Error fatal detectado o política indica no insistir. Deteniendo reprogramación en bucle.")
                return
            }
            duracion.toMinutes()
        }

        scheduleNextAlarm(context, minutes)
    
        }
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
            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

            if (minutes < 15 && canExact) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
                Log.d(TAG, "Alarma exacta de sincronización programada para dentro de $minutes minutos (canExact=true).")
            } else {
                // Si el intervalo es >= 15 min o no se tiene permiso de exactas, usar alarma inexacta y WorkManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
                scheduleWorkManagerOneTime(context, minutes)
                Log.d(TAG, "Alarma inexacta + WorkManager programados para dentro de $minutes minutos (canExact=$canExact).")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Permiso de alarma exacta restringido por el sistema (${e.localizedMessage}). Usando alarma inexacta y WorkManager.")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } catch (_: Exception) {}
            scheduleWorkManagerOneTime(context, minutes)
        } catch (e: Exception) {
            Log.e(TAG, "Error programando alarma con AlarmManager: ${e.localizedMessage}")
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
        WorkManager.getInstance(context).cancelUniqueWork("upao_immediate_sync")
        WorkManager.getInstance(context).cancelUniqueWork("upao_asistencia_check")
        WorkManager.getInstance(context).cancelUniqueWork("upao_asistencia_5min_check")
        Log.d(TAG, "Todas las sincronizaciones en segundo plano han sido canceladas.")
    }

    fun runNow(context: Context) {
        synchronized(com.example.upaos.data.local.SessionCleanup.lock) {
            if (!com.example.upaos.data.local.TokenManager(context).shouldKeepLoggedIn()) {
                cancel(context)
                return
            }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<AsistenciaWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork("upao_immediate_sync", ExistingWorkPolicy.KEEP, request)
        Log.d(TAG, "Sincronización inmediata solicitada.")
    
        }
    }
}

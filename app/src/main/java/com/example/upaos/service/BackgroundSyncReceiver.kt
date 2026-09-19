package com.example.upaos.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.upaos.data.local.NotificationPreferences

class BackgroundSyncReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d(TAG, "BackgroundSyncReceiver recibido con acción: $action")

        val prefs = NotificationPreferences(context)
        val shouldRun = prefs.checkAsistenciaEnabled || prefs.checkNotasEnabled

        when (action) {
            SyncScheduler.ACTION_SYNC -> {
                if (shouldRun) {
                    // Ejecuta la sincronización en segundo plano
                    AsistenciaWorker.runOnce(context)
                    // Reprograma la siguiente alarma para dentro de 5 minutos
                    SyncScheduler.scheduleNext(context, SyncScheduler.INTERVAL_MINUTES)
                } else {
                    SyncScheduler.cancel(context)
                }
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                Log.d(TAG, "Dispositivo iniciado o app actualizada. Reiniciando sincronizador...")
                if (shouldRun) {
                    SyncScheduler.start(context)
                    AsistenciaWorker.runOnce(context)
                }
            }
        }
    }

    companion object {
        private const val TAG = "UPAO_SyncReceiver"
    }
}

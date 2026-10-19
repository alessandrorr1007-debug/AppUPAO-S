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
            SyncScheduler.ACTION_SYNC,
            "com.example.upaos.ACTION_SYNC_5MIN" -> {
                if (shouldRun) {
                    // Ejecuta la sincronización en segundo plano
                    AsistenciaWorker.runOnce(context)
                    // Reprograma la siguiente alarma con el intervalo adaptativo calculado
                    SyncScheduler.scheduleNext(context)
                } else {
                    SyncScheduler.cancel(context)
                }
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                Log.d(TAG, "Dispositivo iniciado o app actualizada. Reiniciando sincronizador y recordatorios...")
                if (shouldRun) {
                    SyncScheduler.start(context)
                    AsistenciaWorker.runOnce(context)
                }
                if (prefs.recordatorioClasesEnabled) {
                    ClassReminderScheduler.reprogramar(context)
                }
            }
        }
    }

    companion object {
        private const val TAG = "UPAO_SyncReceiver"
    }
}

package com.example.upaos.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ClassReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val id = intent.getIntExtra("id", 100)
        val titulo = intent.getStringExtra("titulo") ?: "Recordatorio de clase"
        val mensaje = intent.getStringExtra("mensaje") ?: return

        Log.d(TAG, "Notificando clase próxima: $mensaje (ID=$id)")
        NotificationService.mostrarNotificacionRecordatorioClase(
            context = context,
            id = id,
            title = titulo,
            body = mensaje
        )
    }

    companion object {
        private const val TAG = "UPAO_ClassReminderRx"
    }
}

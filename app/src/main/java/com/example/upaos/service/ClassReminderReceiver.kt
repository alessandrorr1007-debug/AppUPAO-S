package com.example.upaos.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ClassReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        synchronized(com.example.upaos.data.local.SessionCleanup.lock) {
            val owner = ReminderOwner(intent.getStringExtra("owner_user") ?: return,
                intent.getStringExtra("owner_session") ?: return)
            val tokens = com.example.upaos.data.local.TokenManager(context)
            if (!owner.matches(tokens.getSavedUser(), tokens.getSessionId()) || tokens.getToken().isNullOrBlank()) return
            if (!com.example.upaos.data.local.NotificationPreferences(context).recordatorioClasesEnabled) return
            if (reminderScheduleKey(owner.user, intent.getStringExtra("owner_term")) == null) return
            val id = intent.getIntExtra("id", 100)
            val titulo = intent.getStringExtra("titulo") ?: "Recordatorio de clase"
            val mensaje = intent.getStringExtra("mensaje") ?: return
            NotificationService.mostrarNotificacionRecordatorioClase(context, id, titulo, mensaje)
        }
    }

    companion object {
        private const val TAG = "UPAO_ClassReminderRx"
    }
}

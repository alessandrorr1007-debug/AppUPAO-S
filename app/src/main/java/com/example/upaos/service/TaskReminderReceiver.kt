package com.example.upaos.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.upaos.data.local.TasksPreferences
import com.example.upaos.data.local.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TaskReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d(TAG, "TaskReminderReceiver recibido con acción: $action")

        when (action) {
            TaskReminderManager.ACTION_REMIND_TASK -> {
                val taskId = intent.getStringExtra(TaskReminderManager.EXTRA_TASK_ID) ?: ""
                val taskTitle = intent.getStringExtra(TaskReminderManager.EXTRA_TASK_TITLE) ?: "Tarea pendiente"
                val taskCourse = intent.getStringExtra(TaskReminderManager.EXTRA_TASK_COURSE) ?: ""

                val notifTitle = if (taskCourse.isNotBlank()) {
                    "📌 Recordatorio: $taskCourse"
                } else {
                    "📌 Recordatorio de Tarea"
                }
                val notifBody = "La tarea '$taskTitle' vence pronto. ¡No olvides presentarla a tiempo!"

                NotificationService.mostrarNotificacionTarea(
                    context = context,
                    title = notifTitle,
                    body = notifBody,
                    taskId = taskId
                )
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                // Al reiniciar el móvil, reprogramar recordatorios pendientes del usuario activo
                val user = TokenManager(context).getSavedUser() ?: return
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val tasks = TasksPreferences(context).getTasks(user)
                        tasks.filter { !it.completada && it.recordatorioMinutosAntes >= 0 }.forEach { task ->
                            TaskReminderManager.scheduleReminder(context, task)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reprogramando recordatorios tras reinicio: ${e.message}")
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "UPAO_TaskReminder"
    }
}

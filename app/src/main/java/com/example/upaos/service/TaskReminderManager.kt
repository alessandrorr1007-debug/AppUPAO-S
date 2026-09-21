package com.example.upaos.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.upaos.data.model.TaskModel

object TaskReminderManager {
    private const val TAG = "TaskReminderManager"
    const val ACTION_REMIND_TASK = "com.example.upaos.ACTION_REMIND_TASK"
    const val EXTRA_TASK_ID = "extra_task_id"
    const val EXTRA_TASK_TITLE = "extra_task_title"
    const val EXTRA_TASK_COURSE = "extra_task_course"

    fun scheduleReminder(context: Context, task: TaskModel) {
        cancelReminder(context, task.id)

        if (task.completada || task.recordatorioMinutosAntes < 0) return

        val reminderTimeMillis = task.fechaEntregaMillis - (task.recordatorioMinutosAntes * 60 * 1000L)
        val now = System.currentTimeMillis()

        if (reminderTimeMillis <= now) {
            Log.d(TAG, "La hora de recordatorio ya pasó para la tarea: ${task.titulo}")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = ACTION_REMIND_TASK
            putExtra(EXTRA_TASK_ID, task.id)
            putExtra(EXTRA_TASK_TITLE, task.titulo)
            putExtra(EXTRA_TASK_COURSE, task.curso)
        }

        val requestCode = task.id.hashCode() and 0x7FFFFFFF
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTimeMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTimeMillis, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTimeMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, reminderTimeMillis, pendingIntent)
            }
            Log.d(TAG, "Recordatorio programado para tarea '${task.titulo}' en $reminderTimeMillis")
        } catch (e: Exception) {
            Log.e(TAG, "Error programando recordatorio: ${e.message}")
        }
    }

    fun cancelReminder(context: Context, taskId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = ACTION_REMIND_TASK
        }
        val requestCode = taskId.hashCode() and 0x7FFFFFFF
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Recordatorio cancelado para tarea ID $taskId")
        }
    }
}

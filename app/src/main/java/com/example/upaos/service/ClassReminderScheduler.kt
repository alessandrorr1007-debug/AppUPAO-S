package com.example.upaos.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.upaos.data.local.ApiCache
import com.example.upaos.data.local.NotificationPreferences
import com.example.upaos.data.local.TokenManager
import com.example.upaos.data.model.HorarioResponse
import com.example.upaos.data.upao.ScheduleCalculator
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime

object ClassReminderScheduler {
    private const val TAG = "UPAO_ClassReminder"
    private const val PREFS_NAME = "upaos_scheduled_reminders"
    private const val KEY_SCHEDULED_IDS = "scheduled_alarm_ids"

    /**
     * Reprograma las notificaciones locales 15 minutos antes de cada clase para hoy y mañana.
     * Cancela previamente las alarmas existentes para evitar duplicados.
     */
    fun reprogramar(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            reprogramarSuspend(context)
        }
    }

    suspend fun reprogramarSuspend(context: Context) {
        val prefs = NotificationPreferences(context)
        if (!prefs.recordatorioClasesEnabled) {
            cancelarTodos(context)
            return
        }

        val user = TokenManager(context).getSavedUser() ?: return
        val apiCache = ApiCache(context)
        val horarioJson = apiCache.cargar("horario_ultimo_$user")
            ?: apiCache.listarPorPrefijo("horario_${user}_").values.firstOrNull()
            ?: apiCache.cargar("horario_$user")
            ?: apiCache.listarPorPrefijo("horario_").values.firstOrNull()

        if (horarioJson.isNullOrBlank()) {
            Log.d(TAG, "No hay horario en caché para reprogramar recordatorios.")
            return
        }

        val horario = try {
            Gson().fromJson(horarioJson, HorarioResponse::class.java)
        } catch (_: Exception) {
            null
        } ?: return

        val cursos = horario.listaCursos
        if (cursos.isEmpty()) return

        // 1. Cancelar alarmas previamente programadas para evitar duplicados
        cancelarTodos(context)

        // 2. Calcular recordatorios para hoy y mañana
        val hoy = LocalDate.now(ScheduleCalculator.LIMA_ZONE)
        val manana = hoy.plusDays(1)
        val ahora = ZonedDateTime.now(ScheduleCalculator.LIMA_ZONE)

        val recordatoriosHoy = ClassReminderCalculator.calcularRecordatorios(cursos, hoy)
        val recordatoriosManana = ClassReminderCalculator.calcularRecordatorios(cursos, manana)
        val todos = (recordatoriosHoy + recordatoriosManana).filter { it.triggerInstant.isAfter(ahora) }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        val idsGuardados = mutableSetOf<String>()

        for (rec in todos) {
            val triggerMillis = rec.triggerInstant.toInstant().toEpochMilli()
            val intent = Intent(context, ClassReminderReceiver::class.java).apply {
                putExtra("id", rec.idAlarma)
                putExtra("titulo", rec.titulo)
                putExtra("mensaje", rec.mensaje)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                rec.idAlarma,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                if (canExact) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerMillis,
                            pendingIntent
                        )
                    } else {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                    }
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerMillis,
                            pendingIntent
                        )
                    } else {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                    }
                }
                idsGuardados.add(rec.idAlarma.toString())
                Log.d(TAG, "Recordatorio programado: ${rec.mensaje} para ${rec.triggerInstant} (ID=${rec.idAlarma})")
            } catch (e: Exception) {
                Log.e(TAG, "Error programando recordatorio ${rec.idAlarma}: ${e.message}")
            }
        }

        guardarIds(context, idsGuardados)
    }

    /**
     * Cancela todas las alarmas de recordatorio de clases pendientes en el sistema.
     */
    fun cancelarTodos(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val ids = obtenerIds(context)
        for (idStr in ids) {
            val id = idStr.toIntOrNull() ?: continue
            val intent = Intent(context, ClassReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
        guardarIds(context, emptySet())
        Log.d(TAG, "Se cancelaron todas las alarmas de recordatorio de clases.")
    }

    private fun obtenerIds(context: Context): Set<String> {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getStringSet(KEY_SCHEDULED_IDS, emptySet()) ?: emptySet()
    }

    private fun guardarIds(context: Context, ids: Set<String>) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putStringSet(KEY_SCHEDULED_IDS, ids).apply()
    }
}

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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZonedDateTime

object ClassReminderScheduler {
    private const val TAG = "UPAO_ClassReminder"
    private const val PREFS_NAME = "upaos_scheduled_reminders"
    private const val KEY_SCHEDULED_IDS = "scheduled_alarm_ids"
    private val schedulingLock = Mutex()

    /**
     * Reprograma las notificaciones locales 15 minutos antes de cada clase para hoy y mañana.
     * Cancela previamente las alarmas existentes para evitar duplicados.
     */
    fun reprogramar(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try { reprogramarSuspend(context.applicationContext) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.w(TAG, "No se pudieron reprogramar los recordatorios") }
        }
    }

    suspend fun reprogramarSuspend(context: Context) = schedulingLock.withLock {
        val prefs = NotificationPreferences(context)
        if (!prefs.recordatorioClasesEnabled) {
            cancelarTodos(context)
            return@withLock
        }

        val tokens = TokenManager(context)
        val user = tokens.getSavedUser() ?: return@withLock
        val sessionId = tokens.getSessionId() ?: return@withLock
        val owner = ReminderOwner(user, sessionId)
        val apiCache = ApiCache(context)
        val term = apiCache.cargar("horario_ultimo_periodo_$user")
        val key = reminderScheduleKey(user, term) ?: return@withLock
        val horarioJson = apiCache.cargar(key) ?: return@withLock
        val horario = try {
            Gson().fromJson(horarioJson, HorarioResponse::class.java)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            Log.w(TAG, "Caché de horario no válida; no se programan recordatorios")
            return@withLock
        }
        if (horario.periodo != term || !owner.matches(tokens.getSavedUser(), tokens.getSessionId())) return@withLock

        val cursos = horario.listaCursos
        if (cursos.isEmpty()) return@withLock

        // 1. Cancelar alarmas previamente programadas para evitar duplicados
        cancelarTodos(context)

        // 2. Calcular recordatorios para hoy y mañana
        val hoy = LocalDate.now(ScheduleCalculator.LIMA_ZONE)
        val manana = hoy.plusDays(1)
        val ahora = ZonedDateTime.now(ScheduleCalculator.LIMA_ZONE)

        val recordatoriosHoy = ClassReminderCalculator.calcularRecordatorios(cursos, hoy)
        val recordatoriosManana = ClassReminderCalculator.calcularRecordatorios(cursos, manana)
        val todos = (recordatoriosHoy + recordatoriosManana).filter { it.triggerInstant.isAfter(ahora) }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@withLock
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        val idsGuardados = mutableSetOf<String>()

        synchronized(com.example.upaos.data.local.SessionCleanup.lock) {
        if (!owner.matches(tokens.getSavedUser(), tokens.getSessionId())) return@withLock
        for (rec in todos) {
            if (!owner.matches(tokens.getSavedUser(), tokens.getSessionId())) break
            val triggerMillis = rec.triggerInstant.toInstant().toEpochMilli()
            val intent = Intent(context, ClassReminderReceiver::class.java).apply {
                data = android.net.Uri.parse(owner.identity(rec.idAlarma))
                putExtra("owner_user", user)
                putExtra("owner_session", sessionId)
                putExtra("owner_term", term)
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
                idsGuardados.add(owner.identity(rec.idAlarma))
                Log.d(TAG, "Recordatorio programado")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "No se pudo programar un recordatorio")
            }
        }

        guardarIds(context, idsGuardados)
        }
    }

    /**
     * Cancela todas las alarmas de recordatorio de clases pendientes en el sistema.
     */
    fun cancelAccount(context: Context, user: String) = synchronized(com.example.upaos.data.local.SessionCleanup.lock) {
        val owned = obtenerIds(context).filter { value ->
            // Legacy alarms had no owner and may only belong to the formerly active account.
            !value.startsWith("upaos://class/") || android.net.Uri.parse(value).pathSegments.firstOrNull() == user
        }.toSet()
        cancelIds(context, owned)
        guardarIds(context, obtenerIds(context) - owned)
    }

    fun cancelarTodos(context: Context) = synchronized(com.example.upaos.data.local.SessionCleanup.lock) {
        cancelIds(context, obtenerIds(context))
        guardarIds(context, emptySet())
    }

    private fun cancelIds(context: Context, ids: Set<String>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        for (idStr in ids) {
            val uri = if (idStr.startsWith("upaos://class/")) android.net.Uri.parse(idStr) else null
            val id = (uri?.lastPathSegment ?: idStr).toIntOrNull() ?: continue
            val intent = Intent(context, ClassReminderReceiver::class.java).apply { data = uri }
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

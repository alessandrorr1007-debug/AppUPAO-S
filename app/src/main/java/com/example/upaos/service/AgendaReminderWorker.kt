package com.example.upaos.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.example.upaos.MainActivity
import com.example.upaos.data.agenda.AgendaRepository
import com.example.upaos.data.local.TokenManager
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class AgendaReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val user = inputData.getString("user") ?: return Result.success()
        val tokens = TokenManager(applicationContext)
        val prefs = preferences(applicationContext, user)
        val hours = prefs.getInt("hours", 0)
        if (hours == 0 || tokens.getCanvasToken(user).isNullOrBlank()) return Result.success()
        val repository = AgendaRepository(applicationContext)
        try { if (!com.example.upaos.data.refresh.ForegroundRefreshGate.active) repository.refresh(user) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        val (items, updated) = repository.cached(user)
        // Avoid false alerts from submissions that could not be checked recently.
        val now = System.currentTimeMillis()
        if (updated == null || now - updated > TimeUnit.HOURS.toMillis(24)) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) return Result.success()
        val system = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) system.createNotificationChannel(NotificationChannel(CHANNEL, "Entregas de Canvas", NotificationManager.IMPORTANCE_DEFAULT))
        if (Build.VERSION.SDK_INT >= 26 && system.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return Result.success()
        for (item in items) {
            val due = item.dueMillis ?: continue
            val key = "sent_${item.key}_$due"
            if (item.submitted || item.graded || due <= now || due - now > TimeUnit.HOURS.toMillis(hours.toLong()) || prefs.getBoolean(key, false)) continue
            if (tokens.getCanvasToken(user).isNullOrBlank() || prefs.getInt("hours", 0) == 0) break
            val id = ("$user:${item.key}".hashCode() and Int.MAX_VALUE)
            val intent = Intent(applicationContext, MainActivity::class.java).putExtra("agenda_user", user)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pending = PendingIntent.getActivity(applicationContext, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val format = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
            val text = "${item.course} · vence ${format.format(java.util.Date(due))}. Última actualización: ${format.format(java.util.Date(updated))}."
            manager.notify("agenda_$user", id, NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle(item.title)
                .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pending).setAutoCancel(true).build())
            prefs.edit().putBoolean(key, true).apply()
        }
        return Result.success()
    }
    companion object {
        const val CHANNEL = "canvas_deadlines"
        fun preferences(context: Context, user: String) = context.getSharedPreferences("agenda_reminders_${user.trim().lowercase()}", Context.MODE_PRIVATE)
        fun configure(context: Context, user: String, hours: Int) {
            preferences(context, user).edit().putInt("hours", hours).apply()
            if (hours == 0) { cancel(context, user); return }
            val data = workDataOf("user" to user)
            val periodic = PeriodicWorkRequestBuilder<AgendaReminderWorker>(15, TimeUnit.MINUTES).setInputData(data).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("agenda_$user", ExistingPeriodicWorkPolicy.UPDATE, periodic)
            WorkManager.getInstance(context).enqueueUniqueWork("agenda_now_$user", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<AgendaReminderWorker>().setInputData(data).build())
        }
        fun cancel(context: Context, user: String) {
            WorkManager.getInstance(context).cancelUniqueWork("agenda_$user")
            WorkManager.getInstance(context).cancelUniqueWork("agenda_now_$user")
        }
    }
}

package com.example.upaos.service

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.*
import com.example.upaos.data.local.NotificationPreferences
import com.example.upaos.data.local.TokenManager
import com.example.upaos.data.upao.AcademicCalendar
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.concurrent.TimeUnit

object GradeSyncController {
    private const val BACKUP = "grade_sync_backup"
    private val handler = Handler(Looper.getMainLooper())
    private var visible = false
    @Volatile private var suspended = false
    fun suspendIntensive() { suspended = true }
    private var pulse: Runnable? = null
    fun mode(context: Context): SyncPolicy.Mode {
        val prefs = NotificationPreferences(context)
        if (!prefs.checkNotasEnabled || !TokenManager(context).shouldKeepLoggedIn()) return SyncPolicy.Mode.NORMAL
        val now = ZonedDateTime.now(ZoneId.of("America/Lima"))
        val week = prefs.syncAcademicPeriod?.let { AcademicCalendar.calcularSemana(it, Date.from(now.toInstant())).semana }
        return SyncPolicy.modo(week, now.toLocalTime())
    }
    fun ensureBackup(context: Context) {
        if (!NotificationPreferences(context).checkNotasEnabled || !TokenManager(context).shouldKeepLoggedIn()) {
            WorkManager.getInstance(context).cancelUniqueWork(BACKUP)
            context.stopService(Intent(context, GradeSyncService::class.java))
            return
        }
        val request = PeriodicWorkRequestBuilder<GradeBackupWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(BACKUP, ExistingPeriodicWorkPolicy.KEEP, request)
    }
    fun reconcile(context: Context, allowBackgroundStart: Boolean = false) {
        ensureBackup(context)
        val intent = Intent(context, GradeSyncService::class.java)
        if (mode(context) == SyncPolicy.Mode.NORMAL) context.stopService(intent)
        else if ((visible || allowBackgroundStart) && !suspended && !GradeSyncService.running) {
            try { ContextCompat.startForegroundService(context, intent) }
            catch (e: RuntimeException) { suspended = true; Log.w("UPAO_GradeSync", "Inicio intensivo rechazado; respaldo activo", e) }
        }
    }
    fun onVisible(context: Context) {
        suspended = false
        visible = true
        pulse?.let(handler::removeCallbacks)
        pulse = object : Runnable {
            override fun run() { reconcile(context.applicationContext); handler.postDelayed(this, 30_000) }
        }.also { handler.post(it) }
    }
    fun onHidden() {
        visible = false
        pulse?.let(handler::removeCallbacks)
        pulse = null
    }
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(BACKUP)
        context.stopService(Intent(context, GradeSyncService::class.java))
    }
}

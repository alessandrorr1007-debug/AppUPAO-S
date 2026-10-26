package com.example.upaos.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.upaos.R
import com.example.upaos.data.local.TokenManager
import com.example.upaos.data.refresh.ForegroundRefreshGate
import com.example.upaos.data.upao.UpaoAuth
import kotlinx.coroutines.*
import kotlin.random.Random

class GradeSyncService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (job != null) return START_NOT_STICKY
        try {
            val manager = getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(NotificationChannel(CHANNEL, "Revisión de notas en segundo plano", NotificationManager.IMPORTANCE_LOW).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                })
                manager.deleteNotificationChannel("grade_sync")
            }
            val open = PendingIntent.getActivity(this, 902, Intent(this, com.example.upaos.MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("UPAO S").setContentText("Revisión de notas en segundo plano")
                .setSilent(true).setOnlyAlertOnce(true).setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW).setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
                .setContentIntent(open).setOngoing(true).build()
            ServiceCompat.startForeground(this, 902, notification, if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
        } catch (e: RuntimeException) {
            GradeSyncController.suspendIntensive()
            Log.w("UPAO_GradeSync", "Foreground rechazado; respaldo activo", e)
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        GradeSyncController.ensureBackup(this)
        job = scope.launch {
            try {
                val tokens = TokenManager(this@GradeSyncService)
                val user = tokens.getSavedUser() ?: return@launch
                val session = tokens.getSessionId()
                val pass = tokens.getSavedPass() ?: return@launch
                // Bootstrap once. Subsequent reads reuse the account's HTTP client and cookies.
                var authenticated = false
                val check = GradeSyncCheck(this@GradeSyncService)
                var failures = 0
                while (isActive && tokens.getSavedUser() == user && tokens.getSessionId() == session && GradeSyncController.mode(this@GradeSyncService) == SyncPolicy.Mode.INTENSIVO) {
                    val cycleStarted = SystemClock.elapsedRealtime()
                    if (!ForegroundRefreshGate.active && ForegroundRefreshGate.lock.tryLock()) {
                        try {
                            withTimeout(90_000) {
                                if (!authenticated) {
                                    if (!UpaoAuth.ensureSession(user, pass).success) throw com.example.upaos.data.upao.UpaoSessionExpiredException()
                                    authenticated = true
                                }
                                check.check("INTENSIVO")
                            }
                            failures = 0
                        } catch (e: TimeoutCancellationException) {
                            failures = (failures + 1).coerceAtMost(5)
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) {
                            if (isFatalSyncFailure(e)) { GradeSyncController.suspendIntensive(); break }
                            failures = (failures + 1).coerceAtMost(5)
                            Log.w("UPAO_GradeSync", "Chequeo fallido; backoff=$failures", e)
                        } finally { ForegroundRefreshGate.lock.unlock() }
                    }
                    val wait = (120_000L * (1L shl failures) + Random.nextLong(-15_000, 15_001)).coerceAtLeast(90_000)
                    val until = cycleStarted + wait
                    while (isActive && SystemClock.elapsedRealtime() < until) {
                        if (GradeSyncController.mode(this@GradeSyncService) != SyncPolicy.Mode.INTENSIVO) return@launch
                        delay(minOf(30_000L, (until - SystemClock.elapsedRealtime()).coerceAtLeast(1)))
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                GradeSyncController.suspendIntensive()
                Log.w("UPAO_GradeSync", "Inicialización fallida; respaldo activo", e)
            } finally { withContext(NonCancellable + Dispatchers.Main) { finishSync() } }
        }
        return START_NOT_STICKY
    }
    private fun finishSync() {
        running = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w("UPAO_GradeSync", "dataSync onTimeout; queda WorkManager cada 15 min")
        GradeSyncController.suspendIntensive()
        job?.cancel()
        finishSync()
    }
    override fun onDestroy() {
        scope.cancel()
        running = false
        super.onDestroy()
    }
    companion object {
        private const val CHANNEL = "grade_sync_silent"
        @Volatile var running = false; private set
    }
}

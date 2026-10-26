package com.example.upaos.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.upaos.data.local.NotificationPreferences
import com.example.upaos.data.local.TokenManager
import com.example.upaos.data.refresh.ForegroundRefreshGate
import com.example.upaos.data.upao.UpaoAuth
import kotlinx.coroutines.CancellationException

class GradeBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val tokens = TokenManager(applicationContext)
        if (!NotificationPreferences(applicationContext).checkNotasEnabled || !tokens.shouldKeepLoggedIn()) return Result.success()
        GradeSyncController.reconcile(applicationContext, allowBackgroundStart = true)
        if (GradeSyncService.running) return Result.success()
        if (ForegroundRefreshGate.active || !ForegroundRefreshGate.lock.tryLock()) return Result.success()
        try {
            val user = tokens.getSavedUser() ?: return Result.success()
            val pass = tokens.getSavedPass() ?: return Result.success()
            if (!UpaoAuth.ensureSession(user, pass).success) return Result.failure()
            GradeSyncCheck(applicationContext).check(GradeSyncController.mode(applicationContext).name)
            GradeSyncController.reconcile(applicationContext, allowBackgroundStart = true)
            return Result.success()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { return if (isFatalSyncFailure(e)) Result.failure() else Result.retry() }
        finally { ForegroundRefreshGate.lock.unlock() }
    }
}

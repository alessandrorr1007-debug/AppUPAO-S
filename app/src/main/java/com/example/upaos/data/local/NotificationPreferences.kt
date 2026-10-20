package com.example.upaos.data.local

import android.content.Context
import android.content.SharedPreferences

class NotificationPreferences(context: Context, usuario: String? = null) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("upaos_notification_prefs", Context.MODE_PRIVATE)

    private val snapshotPrefs: SharedPreferences = context.getSharedPreferences(
        "upaos_notification_snapshots_${usuario ?: TokenManager(context).getSavedUser() ?: "anonymous"}", Context.MODE_PRIVATE
    )

    var checkNotasEnabled: Boolean
        get() = prefs.getBoolean(KEY_CHECK_NOTAS, true)
        set(value) = prefs.edit().putBoolean(KEY_CHECK_NOTAS, value).apply()

    var checkAsistenciaEnabled: Boolean
        get() = prefs.getBoolean(KEY_CHECK_ASISTENCIA, true)
        set(value) = prefs.edit().putBoolean(KEY_CHECK_ASISTENCIA, value).apply()

    var recordatorioClasesEnabled: Boolean
        get() = prefs.getBoolean(KEY_RECORDATORIO_CLASES, true)
        set(value) = prefs.edit().putBoolean(KEY_RECORDATORIO_CLASES, value).apply()

    var intervaloMinutos: Int
        get() = prefs.getInt(KEY_INTERVALO_MINUTOS, 10)
        set(value) = prefs.edit().putInt(KEY_INTERVALO_MINUTOS, value).apply()

    var syncConsecutiveFailures: Int
        get() = prefs.getInt(KEY_SYNC_CONSECUTIVE_FAILURES, 0)
        set(value) = prefs.edit().putInt(KEY_SYNC_CONSECUTIVE_FAILURES, value).apply()

    var syncAcademicPeriod: String?
        get() = snapshotPrefs.getString("sync_academic_period", null)
        set(value) = snapshotPrefs.edit().putString("sync_academic_period", value).apply()

    var ultimaRevisionAsistencia: Long
        get() = snapshotPrefs.getLong(KEY_ULTIMA_REVISION, 0L)
        set(value) = snapshotPrefs.edit().putLong(KEY_ULTIMA_REVISION, value).apply()

    var ultimoSnapshotAsistencia: String?
        get() = snapshotPrefs.getString(KEY_ULTIMO_SNAPSHOT, null)
        set(value) = snapshotPrefs.edit().putString(KEY_ULTIMO_SNAPSHOT, value).apply()

    var ultimaRevisionNotas: Long
        get() = snapshotPrefs.getLong(KEY_ULTIMA_REVISION_NOTAS, 0L)
        set(value) = snapshotPrefs.edit().putLong(KEY_ULTIMA_REVISION_NOTAS, value).apply()

    var ultimoSnapshotNotas: String?
        get() = snapshotPrefs.getString(KEY_ULTIMO_SNAPSHOT_NOTAS, null)
        set(value) = snapshotPrefs.edit().putString(KEY_ULTIMO_SNAPSHOT_NOTAS, value).apply()

    var ultimoSnapshotNotificacionesBackend: String?
        get() = snapshotPrefs.getString(KEY_ULTIMO_SNAPSHOT_NOTIF_BACKEND, null)
        set(value) = snapshotPrefs.edit().putString(KEY_ULTIMO_SNAPSHOT_NOTIF_BACKEND, value).apply()

    companion object {
        private const val KEY_CHECK_NOTAS = "check_notas_enabled"
        private const val KEY_CHECK_ASISTENCIA = "check_asistencia_enabled"
        private const val KEY_RECORDATORIO_CLASES = "recordatorio_clases_enabled"
        private const val KEY_INTERVALO_MINUTOS = "intervalo_minutos"
        private const val KEY_SYNC_CONSECUTIVE_FAILURES = "sync_consecutive_failures"
        private const val KEY_ULTIMA_REVISION = "ultima_revision_asistencia"
        private const val KEY_ULTIMO_SNAPSHOT = "ultimo_snapshot_asistencia"
        private const val KEY_ULTIMA_REVISION_NOTAS = "ultima_revision_notas"
        private const val KEY_ULTIMO_SNAPSHOT_NOTAS = "ultimo_snapshot_notas"
        private const val KEY_ULTIMO_SNAPSHOT_NOTIF_BACKEND = "ultimo_snapshot_notif_backend"
    }
}

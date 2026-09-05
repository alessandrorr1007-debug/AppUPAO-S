package com.example.upaos.data.local

import android.content.Context
import android.content.SharedPreferences

class NotificationPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("upaos_notification_prefs", Context.MODE_PRIVATE)

    var checkNotasEnabled: Boolean
        get() = prefs.getBoolean(KEY_CHECK_NOTAS, true)
        set(value) = prefs.edit().putBoolean(KEY_CHECK_NOTAS, value).apply()

    var checkAsistenciaEnabled: Boolean
        get() = prefs.getBoolean(KEY_CHECK_ASISTENCIA, true)
        set(value) = prefs.edit().putBoolean(KEY_CHECK_ASISTENCIA, value).apply()

    var intervaloMinutos: Int
        get() = prefs.getInt(KEY_INTERVALO_MINUTOS, 15)
        set(value) = prefs.edit().putInt(KEY_INTERVALO_MINUTOS, value).apply()

    var ultimaRevisionAsistencia: Long
        get() = prefs.getLong(KEY_ULTIMA_REVISION, 0L)
        set(value) = prefs.edit().putLong(KEY_ULTIMA_REVISION, value).apply()

    var ultimoSnapshotAsistencia: String?
        get() = prefs.getString(KEY_ULTIMO_SNAPSHOT, null)
        set(value) = prefs.edit().putString(KEY_ULTIMO_SNAPSHOT, value).apply()

    companion object {
        private const val KEY_CHECK_NOTAS = "check_notas_enabled"
        private const val KEY_CHECK_ASISTENCIA = "check_asistencia_enabled"
        private const val KEY_INTERVALO_MINUTOS = "intervalo_minutos"
        private const val KEY_ULTIMA_REVISION = "ultima_revision_asistencia"
        private const val KEY_ULTIMO_SNAPSHOT = "ultimo_snapshot_asistencia"
    }
}

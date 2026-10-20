package com.example.upaos.data.local

import android.app.NotificationManager
import android.content.Context
import com.example.upaos.service.AgendaReminderWorker
import com.example.upaos.service.SyncScheduler
import com.example.upaos.widget.ProximoCursoWidget
import com.example.upaos.widget.ResumenNotasWidget

object AccountPrivacy {
    private val deleting = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    fun isDeleting(user: String) = deleting.contains(user)
    fun markDeleting(user: String, value: Boolean) { if (value) deleting.add(user) else deleting.remove(user) }
    suspend fun clearCache(context: Context, user: String) {
        val courses = CanvasCache(context).cargarCursos(user)
        val legacyZoom = context.getSharedPreferences("course_zoom_preferences", Context.MODE_PRIVATE).edit()
        courses.forEach { course -> listOf("zoom_url", "zoom_title", "zoom_id", "zoom_pwd").forEach { legacyZoom.remove("${it}_${course.id}") } }
        legacyZoom.commit()
        ApiCache(context).limpiar(user)
        GradesCache(context).limpiar(user)
        CanvasCache(context).limpiar(user)
        context.getSharedPreferences("upaos_notification_snapshots_$user", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("course_zoom_preferences_$user", Context.MODE_PRIVATE).edit().clear().commit()
        ProximoCursoWidget.updateAll(context)
        ResumenNotasWidget.updateAll(context)
    }

    suspend fun removeLocal(context: Context, user: String) {
        val tokens = TokenManager(context)
        val current = tokens.getSavedUser() == user
        AgendaReminderWorker.configure(context, user, 0)
        tokens.removeCanvasToken(user)
        tokens.removeCuenta(user)
        if (current) { tokens.clearSession(); SyncScheduler.cancel(context) }
        clearCache(context, user)
        context.getSharedPreferences("local_notificaciones_$user", Context.MODE_PRIVATE).edit().clear().commit()
        com.example.upaos.data.upao.UpaoHttpClient.clearAccount(user)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.activeNotifications.filter { it.tag == "agenda_$user" }.forEach { manager.cancel(it.tag, it.id) }
    }
}

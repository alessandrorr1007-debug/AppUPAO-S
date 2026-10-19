package com.example.upaos.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.upaos.R

/**
 * Gestor centralizado de notificaciones locales para avisos de notas y asistencia.
 */
object NotificationService {
    const val CHANNEL_NOTAS = "upaos_notas"
    const val CHANNEL_ASISTENCIA = "upaos_asistencia"
    const val CHANNEL_RECORDATORIO_CLASES = "upaos_recordatorio_clases"

    private val notifIdCounter = java.util.concurrent.atomic.AtomicInteger((System.currentTimeMillis() % 100000).toInt())

    fun mostrarNotificacionRecordatorioClase(context: Context, id: Int, title: String, body: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_RECORDATORIO_CLASES,
                "Recordatorio de clases",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisos 15 minutos antes del inicio de tus clases"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: android.content.Intent(context, com.example.upaos.MainActivity::class.java)).apply {
            putExtra("destination_tab", "horario")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_RECORDATORIO_CLASES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setColor(0xFF2E7D32.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(id, notification)
    }

    fun mostrarNotificacionAsistencia(context: Context, title: String, body: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ASISTENCIA,
                "Asistencias UPAO",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisos de asistencias y faltas registradas"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: android.content.Intent(context, com.example.upaos.MainActivity::class.java)).apply {
            putExtra("destination_tab", "asistencia")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ASISTENCIA)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setColor(0xFFEE5D1A.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setContentIntent(pendingIntent)
            .build()

        val id = notifIdCounter.incrementAndGet() + 1000
        notificationManager.notify(id, notification)
    }

    fun mostrarNotificacionNotas(context: Context, title: String, body: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_NOTAS,
                "Notas UPAO",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisos de publicación y cambio de notas"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: android.content.Intent(context, com.example.upaos.MainActivity::class.java)).apply {
            putExtra("destination_tab", "notas")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_NOTAS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setColor(0xFF1976D2.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setContentIntent(pendingIntent)
            .build()

        val id = notifIdCounter.incrementAndGet()
        notificationManager.notify(id, notification)
    }
}

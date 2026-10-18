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

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
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
            .setContentIntent(pendingIntent)
            .build()

        val id = (System.currentTimeMillis() % 100000).toInt() + 1000
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

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
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
            .setContentIntent(pendingIntent)
            .build()

        val id = (System.currentTimeMillis() % 100000).toInt()
        notificationManager.notify(id, notification)
    }
}

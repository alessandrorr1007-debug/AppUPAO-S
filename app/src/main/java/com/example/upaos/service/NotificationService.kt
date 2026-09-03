package com.example.upaos.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.upaos.R
import com.example.upaos.data.api.RetrofitClient
import com.example.upaos.data.local.TokenManager
import com.example.upaos.data.model.DeviceTokenRequest
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Obtiene el token FCM y lo registra en el backend (POST /device-token).
 * Se llama al iniciar sesión, al abrir la app con sesión guardada y cuando
 * Firebase regenera el token.
 */
object FcmTokenHelper {
    private const val TAG = "UPAO_FCM"

    fun register(context: Context) {
        val usuario = TokenManager(context).getSavedUser() ?: return
        @Suppress("DEPRECATION")
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful && !task.result.isNullOrBlank()) {
                val fcmToken = task.result
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val res = RetrofitClient.apiService.updateDeviceToken(usuario, DeviceTokenRequest(fcmToken))
                        Log.d(TAG, "Token FCM registrado para $usuario -> HTTP ${res.code()}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error registrando token FCM: ${e.localizedMessage}")
                    }
                }
            } else {
                Log.e(TAG, "No se pudo obtener token FCM", task.exception)
            }
        }
    }
}

class NotificationService : FirebaseMessagingService() {

    @Suppress("DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nuevo token FCM: ${token.take(20)}...")
        FcmTokenHelper.register(applicationContext)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "UPAO Notas"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "Tus notas cambiaron."

        Log.d(TAG, "Mensaje FCM recibido: $title - $body")
        mostrarNotificacion(title, body)
    }

    private fun mostrarNotificacion(title: String, body: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Notas UPAO",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val id = (System.currentTimeMillis() % 100000).toInt()
        notificationManager.notify(id, notification)
    }

    companion object {
        private const val TAG = "UPAO_FCM"
        private const val CHANNEL_ID = "upaos_notas"
    }
}

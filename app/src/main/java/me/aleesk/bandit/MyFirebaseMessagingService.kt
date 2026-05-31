package me.aleesk.bandit

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title ?: "🚨 ¡ALERTA DE CRISIS!"
        val body = remoteMessage.notification?.body ?: "El paciente necesita asistencia."

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "crisis_channel"

        // Crear el canal de notificación (Requerido en Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Alertas Críticas",
                NotificationManager.IMPORTANCE_HIGH // Alta prioridad para que suene e interrumpa
            ).apply {
                description = "Canal para alertas de crisis de pacientes"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Construir la notificación
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            // Cambia la línea del icono por esta (un icono nativo estándar de sistema que siempre funciona):
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        notificationManager.notify(100, notificationBuilder.build())
    }
}
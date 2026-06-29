package me.aleesk.bandit

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth

class BanDITMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "bandit_alerts"          // debe coincidir con server.js
        const val CHANNEL_NAME = "Alertas BanDIT"

        /** Llama esto desde Application.onCreate() o MainActivity para que el
         *  canal exista antes de que llegue cualquier notificación. */
        fun createNotificationChannel(context: android.content.Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alertas de crisis del paciente"
                    enableVibration(true)
                    enableLights(true)
                }
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(channel)
            }
        }
    }

    /** Se llama cuando la app está en PRIMER PLANO y llega un mensaje. */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // Usamos el campo `notification` si existe; si no, caemos a `data`.
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "🚨 Alerta BanDIT"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "Un paciente necesita ayuda"

        showNotification(title, body)
    }

    /** Se llama cuando Firebase rota el token. Hay que guardarlo de nuevo. */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .update("fcmToken", token)
    }

    private fun showNotification(title: String, body: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)   // reemplaza con tu ícono
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
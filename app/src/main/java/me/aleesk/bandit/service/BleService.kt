package me.aleesk.bandit.service

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import me.aleesk.bandit.BleManager
import me.aleesk.bandit.MainActivity

// ============================================================
//  BleService — ForegroundService que mantiene la conexión BLE
//  viva aunque la app esté en background o la pantalla apagada.
//
//  Desde la UI:a
//    BleService.startBleService(context)   → arranca el servicio
//    BleService.stopBleService(context)    → detiene el servicio
//    context.bindService(...)              → obtiene referencia al servicio
// ============================================================

private const val TAG             = "BleService"
private const val CHANNEL_ID      = "bandit_ble"
private const val NOTIFICATION_ID = 1

class BleService : Service() {

    // ── Binder — la UI accede al servicio a través de esto ──
    inner class LocalBinder : Binder() {
        val service: BleService get() = this@BleService
    }

    private val binder = LocalBinder()

    // BleManager vive aquí dentro, no en la UI
    private var bleManager: BleManager? = null

    // Callbacks que PatientHomeScreen registra después de bindear
    var onConnectionChange: ((Boolean) -> Unit)? = null
    var onBpmUpdate: ((Int) -> Unit)? = null
    var onAlertReceived: ((Boolean) -> Unit)? = null

    // ── Ciclo de vida ─────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Buscando pulsera..."))
        Log.d(TAG, "Servicio BLE creado")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> initBleManager()
            ACTION_STOP  -> stopSelf()
        }
        // START_STICKY: Android reinicia el servicio si lo mata por falta de memoria
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        bleManager?.disconnect()
        bleManager = null
        Log.d(TAG, "Servicio BLE destruido")
        super.onDestroy()
    }

    // ── Inicializa el BleManager ──────────────────────────
    private fun initBleManager() {
        if (bleManager != null) return   // ya corriendo, no duplicar

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Log.e(TAG, "Sin usuario autenticado — no se puede iniciar BLE")
            stopSelf()
            return
        }

        bleManager = BleManager(
            context = applicationContext,
            userId = userId,
            db = FirebaseFirestore.getInstance(),
            onConnectionChange = { connected ->
                updateNotification(
                    if (connected) "Pulsera conectada" else "Buscando pulsera..."
                )
                onConnectionChange?.invoke(connected)
                // Reconexión automática: llamamos startScan() directamente
                // en lugar de pasar por initBleManager() (que hace early-return
                // si bleManager != null y nunca escanearía).
                if (!connected) bleManager?.startScan()
            },
            onBpmUpdate = { bpm ->
                onBpmUpdate?.invoke(bpm)
            },
            onAlertReceived = { active ->
                onAlertReceived?.invoke(active)
            }
        )

        if (bleManager!!.hasPermissions()) {
            bleManager!!.startScan()
        } else {
            Log.w(TAG, "Sin permisos BLE — esperando que la UI los solicite")
        }
    }

    // Llamado desde PatientHomeScreen cuando el usuario pulsa "Reconectar"
    // o cuando se conceden los permisos en runtime.
    // Siempre llama startScan() si hay permisos, sin importar si bleManager
    // ya existe — evita el early-return de initBleManager() que silenciaba la reconexión.
    fun iniciarEscaneo() {
        if (bleManager == null) {
            initBleManager()   // crea el manager Y lanza el scan
        } else if (bleManager!!.hasPermissions()) {
            bleManager!!.startScan()   // manager ya existe → solo escanear
        }
    }

    // ── Notificación persistente ──────────────────────────
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Estado de pulsera",
            NotificationManager.IMPORTANCE_LOW   // silenciosa, sin sonido
        ).apply {
            description = "Mantiene la conexión BLE con la pulsera activa"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BanDIT")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    // ── Helpers estáticos ─────────────────────────────────
    companion object {
        const val ACTION_START = "me.aleesk.bandit.BLE_START"
        const val ACTION_STOP  = "me.aleesk.bandit.BLE_STOP"

        fun startBleService(context: Context) {
            context.startForegroundService(
                Intent(context, BleService::class.java).apply { action = ACTION_START }
            )
        }

        fun stopBleService(context: Context) {
            context.startService(
                Intent(context, BleService::class.java).apply { action = ACTION_STOP }
            )
        }
    }
}
package me.aleesk.bandit

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import java.util.UUID

private const val TAG = "BleManager"

// UUIDs — deben coincidir exactamente con config.h de la pulsera
private val SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
private val CHAR_TX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E") // notify
private val CHAR_RX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // write
private val CCCD_UUID    = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB") // descriptor notify

//a
class BleManager(
    private val context: Context,
    private val userId: String,
    private val db: FirebaseFirestore,
    val onConnectionChange: (connected: Boolean) -> Unit,
    val onBpmUpdate: (bpm: Int) -> Unit,
    val onAlertReceived: (active: Boolean) -> Unit
) {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var gatt: BluetoothGatt? = null
    private var rxChar: BluetoothGattCharacteristic? = null

    // ── Estado de conexión ────────────────────────────────
    var isConnected: Boolean = false
        private set

    // ── Permisos requeridos según versión de Android ──────
    val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

    fun hasPermissions(): Boolean =
        requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    // ── Scan ──────────────────────────────────────────────
    private val scanCallback = object : ScanCallback() {
        @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!hasPermissions()) return
            stopScan()
            Log.d(TAG, "Pulsera encontrada: ${result.device.address}")
            result.device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan fallido, código: $errorCode")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        if (!hasPermissions()) {
            Log.w(TAG, "Sin permisos BLE — no se puede escanear")
            return
        }
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        adapter?.bluetoothLeScanner?.startScan(listOf(filter), settings, scanCallback)
        Log.d(TAG, "Escaneando pulsera...")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        if (!hasPermissions()) return
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    // ── GATT callbacks ────────────────────────────────────
    private val gattCallback = object : BluetoothGattCallback() {

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (!hasPermissions()) return
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Conectado — descubriendo servicios")
                    isConnected = true
                    onConnectionChange(true)
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Desconectado")
                    isConnected = false
                    rxChar = null
                    onConnectionChange(false)
                    gatt.close()
                    this@BleManager.gatt = null
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (!hasPermissions()) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "onServicesDiscovered error: $status")
                return
            }

            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                Log.e(TAG, "Servicio NUS no encontrado en la pulsera")
                return
            }

            // Guardar characteristic de escritura (RX de la pulsera)
            rxChar = service.getCharacteristic(CHAR_RX_UUID)

            // Suscribirse a notificaciones del TX de la pulsera
            val txChar = service.getCharacteristic(CHAR_TX_UUID)
            if (txChar != null) {
                gatt.setCharacteristicNotification(txChar, true)
                val descriptor = txChar.getDescriptor(CCCD_UUID)
                descriptor?.let {
                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(it)
                    Log.d(TAG, "Notificaciones TX activadas")
                }
            }

            this@BleManager.gatt = gatt
        }

        // Android ≤ 12 (API 31)
        @Deprecated("Replaced by the 3-arg override on API 33+", ReplaceWith(""))
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid != CHAR_TX_UUID) return
            val raw = characteristic.value?.toString(Charsets.UTF_8) ?: return
            handleIncoming(raw)
        }

        // Android 13+ (API 33) — el sistema llama ESTE, no el de arriba
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid != CHAR_TX_UUID) return
            val raw = value.toString(Charsets.UTF_8)
            handleIncoming(raw)
        }
    }

    // ── Parseo de mensajes entrantes ──────────────────────
    private fun handleIncoming(raw: String) {
        Log.d(TAG, "BLE RX: $raw")
        try {
            val json = JSONObject(raw)

            when {
                // {"bpm": 72}
                json.has("bpm") -> {
                    val bpm = json.getInt("bpm")
                    onBpmUpdate(bpm)
                    // Actualiza Firestore — el cuidador lo ve en tiempo real
                    db.collection("users").document(userId)
                        .update("heartRate", bpm)
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error actualizando heartRate: ${e.message}")
                        }
                }

                // {"alert": 1} = activa  |  {"alert": 0} = cancelada
                json.has("alert") -> {
                    val active = json.getInt("alert") == 1
                    onAlertReceived(active)
                    db.collection("users").document(userId).update(
                        mapOf(
                            "isCrisis" to active,
                            "lastAlertTime" to com.google.firebase.Timestamp.now()
                        )
                    ).addOnFailureListener { e ->
                        Log.e(TAG, "Error actualizando isCrisis: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "JSON inválido recibido: '$raw' — ${e.message}")
        }
    }

    // ── Envío de comandos a la pulsera (RX) ──────────────
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendCommand(cmd: String) {
        if (!hasPermissions() || !isConnected) return
        val char = rxChar ?: run {
            Log.w(TAG, "rxChar no disponible todavía")
            return
        }
        char.value = cmd.toByteArray(Charsets.UTF_8)
        gatt?.writeCharacteristic(char)
        Log.d(TAG, "BLE TX → pulsera: $cmd")
    }

    // ── Limpieza ──────────────────────────────────────────
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    fun disconnect() {
        if (!hasPermissions()) return
        stopScan()
        gatt?.disconnect()
    }
}
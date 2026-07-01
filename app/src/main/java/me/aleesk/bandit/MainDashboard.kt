package me.aleesk.bandit

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.aleesk.bandit.service.BleService
import me.aleesk.bandit.service.MessagingService
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

// ─── Navigation Tabs ─────

enum class DashboardTab { HOME, PROFILE }

// ─── Root Dashboard ──────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard(userId: String, onLogout: () -> Unit) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()

    var userName by remember { mutableStateOf("") }
    var userRole by remember { mutableStateOf("") }
    var userEmail by remember { mutableStateOf("") }
    var bleConectadoGlobal by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf(DashboardTab.HOME) }

    DisposableEffect(userId) {
        val listener = db.collection("users").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(context, "Error de conexión", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    userName = snapshot.getString("name") ?: "Usuario"
                    userRole = snapshot.getString("role") ?: "patient"
                    userEmail = snapshot.getString("email") ?: ""
                    saveFcmToken(db, userId)
                }
            }
        onDispose { listener.remove() }
    }

    Scaffold(
        containerColor = BanDITColors.NavyDeep,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "BanDIT",
                        color = BanDITColors.CyanPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        letterSpacing = 2.sp
                    )
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (bleConectadoGlobal) BanDITColors.SuccessGreenDim
                                else Color(0xFF2D1B0E)
                            )
                            .border(
                                1.dp,
                                if (bleConectadoGlobal) BanDITColors.SuccessGreen else BanDITColors.WarnOrange,
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (bleConectadoGlobal) BanDITColors.SuccessGreen
                                        else BanDITColors.WarnOrange
                                    )
                            )
                            Text(
                                if (bleConectadoGlobal) "CONECTADO" else "DESCONECTADO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = if (bleConectadoGlobal) BanDITColors.SuccessGreen else BanDITColors.WarnOrange
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onLogout) {
                        Icon(
                            Icons.Outlined.Logout,
                            contentDescription = "Salir",
                            tint = BanDITColors.TextSecond
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BanDITColors.NavySurface
                )
            )
        },
        bottomBar = {
            if (userRole.isNotEmpty()) {
                NavigationBar(
                    containerColor = BanDITColors.NavySurface,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        selected = currentTab == DashboardTab.HOME,
                        onClick = { currentTab = DashboardTab.HOME },
                        icon = { Icon(Icons.Outlined.MonitorHeart, contentDescription = null) },
                        label = { Text("Inicio") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BanDITColors.CyanPrimary,
                            selectedTextColor = BanDITColors.CyanPrimary,
                            unselectedIconColor = BanDITColors.TextMuted,
                            unselectedTextColor = BanDITColors.TextMuted,
                            indicatorColor = BanDITColors.CyanDim
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == DashboardTab.PROFILE,
                        onClick = { currentTab = DashboardTab.PROFILE },
                        icon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                        label = { Text("Perfil") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BanDITColors.CyanPrimary,
                            selectedTextColor = BanDITColors.CyanPrimary,
                            unselectedIconColor = BanDITColors.TextMuted,
                            unselectedTextColor = BanDITColors.TextMuted,
                            indicatorColor = BanDITColors.CyanDim
                        )
                    )
                }
            }
        }
    ) { padding ->
        when {
            userRole.isEmpty() -> FullScreenLoader()
            currentTab == DashboardTab.HOME && userRole == "patient" ->
                PatientHomeScreen(
                    userId = userId,
                    userName = userName,
                    db = db,
                    onBleConnectionChange = { bleConectadoGlobal = it },
                    modifier = Modifier.padding(padding)
                )
            currentTab == DashboardTab.HOME && userRole == "caregiver" ->
                CaregiverHomeScreen(
                    userId = userId,
                    userName = userName,
                    db = db,
                    onBleConnectionChange = { bleConectadoGlobal = it },
                    modifier = Modifier.padding(padding)
                )
            currentTab == DashboardTab.PROFILE ->
                ProfileScreen(
                    userId = userId,
                    userName = userName,
                    userRole = userRole,
                    userEmail = userEmail,
                    db = db,
                    modifier = Modifier.padding(padding)
                )
        }
    }
}

// ─── Patient Home ─────

private const val BACKEND_URL = "https://bandit-backend-spp3.onrender.com"

@SuppressLint("MissingPermission")
@Composable
fun PatientHomeScreen(
    userId: String,
    userName: String,
    db: FirebaseFirestore,
    onBleConnectionChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // Estado BLE
    var bleConectado by remember { mutableStateOf(false) }
    var bpmActual    by remember { mutableStateOf(0) }
    var alertaActiva by remember { mutableStateOf(false) }
    var isSendingAlert by remember { mutableStateOf(false) }

    // ID de la alerta actualmente activa (para poder resolverla por ID exacto)
    var activeAlertId by remember { mutableStateOf<String?>(null) }

    // Cliente de servicios de ubicación de Google
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Referencia al servicio bound — necesaria para llamar iniciarEscaneo()
    // desde el botón de reconexión sin hacer un bindService() duplicado.
    var boundService by remember { mutableStateOf<BleService?>(null) }

    // Conexión al ForegroundService
    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val svc = (binder as BleService.LocalBinder).service
                boundService = svc

                // Sincroniza el estado actual inmediatamente al bindear.
                // Si el servicio ya estaba conectado de antes (ej. la app se
                // reabrió mientras el foreground service seguía vivo), este
                // es el único lugar donde nos enteramos — onConnectionChange
                // solo dispara en transiciones futuras, no retroactivamente.
                bleConectado = svc.isConnected
                onBleConnectionChange(svc.isConnected)

                svc.onConnectionChange = { connected ->
                    bleConectado = connected
                    onBleConnectionChange(connected)
                    db.collection("users").document(userId)
                        .update(
                            mapOf(
                                "bleConnected" to connected,
                                "bleLastUpdate" to System.currentTimeMillis()
                            )
                        )
                }
                svc.onBpmUpdate        = { bpm -> bpmActual = bpm }
                svc.onAlertReceived    = { active ->
                    alertaActiva = active
                    if (active) {
                        val hasLocationPerm = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasLocationPerm) {
                            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                                .addOnSuccessListener { location ->
                                    val locationData = location?.let {
                                        mapOf(
                                            "latitude" to it.latitude,
                                            "longitude" to it.longitude,
                                            "alertLocationTime" to System.currentTimeMillis()
                                        )
                                    }
                                    scope.launch {
                                        activeAlertId = sendCrisisAlert(db, userId, locationData)
                                    }
                                }
                                .addOnFailureListener {
                                    scope.launch { activeAlertId = sendCrisisAlert(db, userId, null) }
                                }
                        } else {
                            scope.launch { activeAlertId = sendCrisisAlert(db, userId, null) }
                        }
                    } else {
                        activeAlertId?.let { resolveSpecificAlert(db, userId, it, userId, userName) }
                        activeAlertId = null
                    }
                }
            }
            override fun onServiceDisconnected(name: ComponentName) {
                bleConectado = false
                boundService = null
            }
        }
    }

    // Permisos requeridos por el Paciente
    val requiredPerms = remember {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            perms.add(Manifest.permission.BLUETOOTH)
            perms.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        perms.toTypedArray()
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            BleService.startBleService(context)
            context.bindService(
                Intent(context, BleService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        } else {
            Toast.makeText(
                context,
                "Se requieren permisos de ubicación y Bluetooth para operar los sistemas de seguridad.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        MessagingService.createNotificationChannel(context)

        val allGranted = requiredPerms.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            BleService.startBleService(context)
            context.bindService(
                Intent(context, BleService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        } else {
            permLauncher.launch(requiredPerms)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { context.unbindService(serviceConnection) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BanDITColors.NavyDeep)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BleStatusCard(
            connected = bleConectado,
            onReconnect = {
                val svc = boundService
                if (svc != null) {
                    svc.iniciarEscaneo()
                } else if (requiredPerms.all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }) {
                    BleService.startBleService(context)
                    context.bindService(
                        Intent(context, BleService::class.java),
                        serviceConnection,
                        Context.BIND_AUTO_CREATE
                    )
                } else {
                    permLauncher.launch(requiredPerms)
                }
            }
        )

        BpmHeroCard(heartRate = bpmActual)
        AlertHistoryCard(
            patientId = userId,
            currentUserId = userId,
            currentUserName = userName.ifBlank { "Paciente" }
        )
        Spacer(Modifier.height(4.dp))

        if (alertaActiva) {
            CrisisAlertDialog(
                patientName = "tú",
                onDismiss = {
                    alertaActiva = false
                    activeAlertId?.let { resolveSpecificAlert(db, userId, it, userId, userName) }
                    activeAlertId = null
                }
            )
        }

        Button(
            onClick = {
                if (isSendingAlert) return@Button
                isSendingAlert = true

                val hasLocationPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (hasLocationPermission) {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { location ->
                            val locationData = location?.let {
                                mapOf(
                                    "latitude" to it.latitude,
                                    "longitude" to it.longitude,
                                    "alertLocationTime" to System.currentTimeMillis()
                                )
                            }
                            scope.launch {
                                val newAlertId = sendCrisisAlert(db, userId, locationData)
                                activeAlertId = newAlertId
                                isSendingAlert = false
                                Toast.makeText(
                                    context,
                                    if (newAlertId != null) "✓ Alerta y ubicación enviadas" else "Alerta registrada sin notificación push",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        .addOnFailureListener {
                            scope.launch {
                                activeAlertId = sendCrisisAlert(db, userId, null)
                                isSendingAlert = false
                                Toast.makeText(context, "Alerta enviada (Error de GPS)", Toast.LENGTH_LONG).show()
                            }
                        }
                } else {
                    scope.launch {
                        activeAlertId = sendCrisisAlert(db, userId, null)
                        isSendingAlert = false
                        Toast.makeText(context, "Alerta enviada sin ubicación (Permiso denegado)", Toast.LENGTH_LONG).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isSendingAlert,
            colors = ButtonDefaults.buttonColors(containerColor = BanDITColors.AlertRed)
        ) {
            if (isSendingAlert) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Icon(Icons.Outlined.Warning, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Text(
                    "ACTIVAR ALERTA MANUAL",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

/** Crea la alerta en Firestore, dispara el backend y devuelve el ID de la alerta creada (o null si falló). */
private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { value -> cont.resume(value) }
    addOnFailureListener { exception -> cont.resumeWithException(exception) }
}

private suspend fun sendCrisisAlert(
    db: FirebaseFirestore,
    userId: String,
    locationData: Map<String, Any>?
): String? {
    val updateFields = mutableMapOf<String, Any>(
        "isCrisis"      to true,
        "lastAlertTime" to Timestamp.now()
    )
    if (locationData != null) {
        updateFields["location"] = locationData
    }

    return try {
        db.collection("users").document(userId).update(updateFields).awaitResult()

        val alertRef = db.collection("users").document(userId)
            .collection("alerts").document()

        val alertData = mutableMapOf<String, Any>("triggeredAt" to Timestamp.now())
        if (locationData != null) alertData["location"] = locationData

        alertRef.set(alertData).awaitResult()

        sendAlertToBackend(userId)

        alertRef.id
    } catch (e: Exception) {
        android.util.Log.e("BanDIT", "Error creando alerta de crisis: ${e.message}", e)
        null
    }
}

/** Marca una alerta puntual (por ID) como resuelta, dejando registro de quién y cuándo. */
private fun resolveSpecificAlert(
    db: FirebaseFirestore,
    patientId: String,
    alertId: String,
    attendedByUid: String,
    attendedByName: String
) {
    db.collection("users").document(patientId)
        .collection("alerts").document(alertId)
        .update(
            mapOf(
                "resolvedAt" to Timestamp.now(),
                "attendedBy" to attendedByUid,
                "attendedByName" to attendedByName.ifBlank { "Usuario" }
            )
        )
        .addOnFailureListener { e ->
            android.util.Log.e("BanDIT", "Error marcando alerta atendida: ${e.message}", e)
        }

    db.collection("users").document(patientId).update("isCrisis", false)
}

/** Envía la alerta al backend y devuelve el alertId que crea, o null si la llamada falló. */
private suspend fun sendAlertToBackend(patientId: String): String? =
    withContext(Dispatchers.IO) {
        try {
            val url = URL("$BACKEND_URL/sendAlert")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                connectTimeout = 8_000
                readTimeout = 8_000
            }
            val body = JSONObject().put("patientId", patientId).toString()
            connection.outputStream.bufferedWriter().use { it.write(body) }
            val responseCode = connection.responseCode
            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else null
            connection.disconnect()

            if (responseText != null) {
                JSONObject(responseText).optString("alertId", null.toString()).takeIf { it.isNotBlank() && it != "null" }
            } else null
        } catch (e: Exception) {
            android.util.Log.e("BanDIT", "Error enviando alerta backend: ${e.message}")
            null
        }
    }

// ─── Caregiver Home ───────────────────────────────────────────────────────────

@Composable
fun CaregiverHomeScreen(
    userId: String,
    userName: String,
    db: FirebaseFirestore,
    onBleConnectionChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var patientName      by remember { mutableStateOf<String?>(null) }
    var patientHeartRate by remember { mutableStateOf<Int?>(null) }
    var linkedPatientId  by remember { mutableStateOf<String?>(null) }
    var isLoading        by remember { mutableStateOf(true) }
    var showCrisisDialog by remember { mutableStateOf(false) }
    var isCrisisActive   by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                context,
                "Activa las notificaciones para recibir las alertas de tu paciente",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    DisposableEffect(userId) {
        val userListener = db.collection("users").document(userId)
            .addSnapshotListener { userSnap, _ ->
                if (userSnap != null && userSnap.exists()) {
                    linkedPatientId = userSnap.getString("linkedPatientId")
                    isLoading = false
                }
            }
        onDispose { userListener.remove() }
    }

    // Solo escucha isCrisis para notificar visualmente — el detalle y la
    // resolución viven en el historial de alertas (un solo lugar de verdad).
    DisposableEffect(linkedPatientId) {
        val patientListener = linkedPatientId?.let { pid ->
            db.collection("users").document(pid)
                .addSnapshotListener { snap, _ ->
                    if (snap != null && snap.exists()) {
                        patientName      = snap.getString("name")
                        patientHeartRate = (snap.getLong("heartRate") ?: 0).toInt()
                        onBleConnectionChange(snap.getBoolean("bleConnected") == true)

                        val crisisFromDb = snap.getBoolean("isCrisis") == true
                        if (crisisFromDb && !isCrisisActive) showCrisisDialog = true
                        isCrisisActive = crisisFromDb
                    }
                }
        }
        onDispose {
            patientListener?.remove()
            onBleConnectionChange(false)
        }
    }

    if (showCrisisDialog) {
        CrisisAlertDialog(
            patientName = patientName ?: "el paciente",
            onDismiss = { showCrisisDialog = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BanDITColors.NavyDeep)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when {
            isLoading           -> FullScreenLoader()
            linkedPatientId == null -> NoPatientLinkedCard()
            else -> {
                BpmHeroCard(
                    heartRate = patientHeartRate ?: 0,
                    label = "FC de ${patientName ?: "Paciente"}"
                )

                AlertHistoryCard(
                    patientId = linkedPatientId!!,
                    currentUserId = userId,
                    currentUserName = userName.ifBlank { "Cuidador" }
                )
            }
        }
    }
}

// ─── Profile Screen ───────────────────────────────────────────────────────────

@Composable
fun ProfileScreen(
    userId: String,
    userName: String,
    userRole: String,
    userEmail: String,
    db: FirebaseFirestore,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BanDITColors.NavyDeep)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            BanDITColors.CyanDim.copy(alpha = 0.4f),
                            BanDITColors.NavyCard
                        )
                    )
                )
                .border(1.dp, BanDITColors.NavyBorder, RoundedCornerShape(20.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(BanDITColors.CyanDim)
                        .border(2.dp, BanDITColors.CyanPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        userName.take(1).uppercase(),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = BanDITColors.CyanPrimary
                    )
                }
                Text(
                    userName,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = BanDITColors.TextPrimary
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(BanDITColors.CyanDim)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (userRole == "patient") "PACIENTE" else "CUIDADOR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = BanDITColors.CyanPrimary
                    )
                }
            }
        }

        if (userRole == "patient") {
            MedCard(title = "Solicitudes de cuidadores", icon = Icons.Outlined.PersonAdd) {
                PendingRequests(userId)
            }
        }

        MedCard(title = "Información de Cuenta", icon = Icons.Outlined.ManageAccounts) {
            ProfileInfoRow(label = "Nombre", value = userName)
            HorizontalDivider(
                color = BanDITColors.NavyBorder,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            ProfileInfoRow(
                label = "Rol",
                value = if (userRole == "patient") "Paciente" else "Cuidador / Familiar"
            )
            HorizontalDivider(
                color = BanDITColors.NavyBorder,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            ProfileInfoRow(label = "Correo", value = userEmail)
        }
    }
}

// ─── Componentes reutilizables ────────────────────────────────────────────────

@Composable
fun BleStatusCard(connected: Boolean, onReconnect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (connected) BanDITColors.SuccessGreenDim else Color(0xFF2D1B0E)
            )
            .border(
                1.dp,
                if (connected) BanDITColors.SuccessGreen else BanDITColors.WarnOrange,
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (connected) BanDITColors.SuccessGreen else BanDITColors.WarnOrange
                    )
            )
            Text(
                if (connected) "BanDIT conectado" else "Buscando BanDIT...",
                color = if (connected) BanDITColors.SuccessGreen else BanDITColors.WarnOrange,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (!connected) {
            TextButton(onClick = onReconnect) {
                Text(
                    "Conectar",
                    color = BanDITColors.CyanPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun BpmHeroCard(heartRate: Int, label: String = "Frecuencia Cardíaca") {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(listOf(BanDITColors.NavyCard, Color(0xFF0D1F35)))
            )
            .border(1.dp, BanDITColors.NavyBorder, RoundedCornerShape(24.dp))
            .padding(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.Favorite,
                    contentDescription = null,
                    tint = BanDITColors.AlertRed,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    label,
                    color = BanDITColors.TextSecond,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    if (heartRate > 0) "$heartRate" else "--",
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    color = BanDITColors.CyanPrimary,
                    lineHeight = 72.sp
                )
                Text(
                    "BPM",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = BanDITColors.TextSecond,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            Text(
                if (heartRate > 0) "EN TIEMPO REAL" else "SIN CONTACTO",
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                color = BanDITColors.TextMuted
            )
        }
    }
}

// ─── Historial de Alertas (fuente única de verdad) ─────────────────────────────

@Composable
fun AlertHistoryCard(patientId: String, currentUserId: String, currentUserName: String) {
    val db = FirebaseFirestore.getInstance()
    var alerts by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
    var selectedAlert by remember { mutableStateOf<DocumentSnapshot?>(null) }

    LaunchedEffect(patientId) {
        db.collection("users").document(patientId)
            .collection("alerts")
            .orderBy("triggeredAt", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("BanDIT", "Error leyendo alertas: ${error.message}", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) alerts = snapshot.documents
            }
    }

    MedCard(title = "Historial de Alertas", icon = Icons.Outlined.Notifications) {
        if (alerts.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = BanDITColors.SuccessGreen, modifier = Modifier.size(20.dp))
                Text("Sin alertas recientes", color = BanDITColors.TextSecond, fontSize = 14.sp)
            }
        } else {
            alerts.forEach { doc ->
                val date = doc.getTimestamp("triggeredAt")?.toDate()
                val resolved = doc.getTimestamp("resolvedAt") != null
                val dateStr = date?.let { SimpleDateFormat("dd/MM HH:mm", Locale("es")).format(it) } ?: "—"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedAlert = doc }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("• Alerta del $dateStr", color = BanDITColors.TextPrimary, fontSize = 13.sp)
                    Text(
                        if (resolved) "Atendida" else "Activa",
                        color = if (resolved) BanDITColors.SuccessGreen else BanDITColors.AlertRed,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    selectedAlert?.let { doc ->
        AlertDetailDialog(
            alertDoc = doc,
            patientId = patientId,
            currentUserId = currentUserId,
            currentUserName = currentUserName,
            canDelete = currentUserId == patientId,   // 👈 solo el paciente puede borrar
            onDismiss = { selectedAlert = null }
        )
    }
}

@Composable
fun AlertDetailDialog(
    alertDoc: DocumentSnapshot,
    patientId: String,
    currentUserId: String,
    currentUserName: String,
    canDelete: Boolean,           // 👈 nuevo: true solo si currentUserId == patientId
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()

    val resolved = alertDoc.getTimestamp("resolvedAt") != null
    val attendedByName = alertDoc.getString("attendedByName")
    val triggeredDate = alertDoc.getTimestamp("triggeredAt")?.toDate()
    val resolvedDate = alertDoc.getTimestamp("resolvedAt")?.toDate()
    val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es"))
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val locationMap = alertDoc.get("location") as? Map<*, *>
    val latitude = locationMap?.get("latitude") as? Double
    val longitude = locationMap?.get("longitude") as? Double

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BanDITColors.NavyCard,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                if (resolved) "Alerta Atendida" else "Alerta Activa",
                color = if (resolved) BanDITColors.SuccessGreen else BanDITColors.AlertRed,
                fontWeight = FontWeight.Bold, fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Activada: ${triggeredDate?.let { fmt.format(it) } ?: "—"}", color = BanDITColors.TextPrimary, fontSize = 14.sp)
                if (resolved) {
                    Text("Atendida: ${resolvedDate?.let { fmt.format(it) } ?: "—"}", color = BanDITColors.TextPrimary, fontSize = 14.sp)
                    Text("Por: ${attendedByName ?: "—"}", color = BanDITColors.TextPrimary, fontSize = 14.sp)
                }
                if (latitude != null && longitude != null) {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            val gmmIntentUri = android.net.Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
                            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                                setPackage("com.google.android.apps.maps")
                            }
                            context.startActivity(mapIntent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BanDITColors.CyanMuted),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Map, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Ver ubicación en Maps", color = Color.White)
                    }
                } else {
                    Text("Sin ubicación registrada", color = BanDITColors.TextMuted, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!resolved) {
                    Button(
                        onClick = {
                            resolveSpecificAlert(db, patientId, alertDoc.id, currentUserId, currentUserName)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BanDITColors.AlertRed),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("ATENDER", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                if (canDelete) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Borrar", fontSize = 13.sp)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", color = BanDITColors.CyanPrimary)
            }
        }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = BanDITColors.NavyCard,
            title = {
                Text(
                    "¿Borrar esta alerta?",
                    color = BanDITColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text("Esta acción no se puede deshacer.", color = BanDITColors.TextSecond) },
            confirmButton = {
                Button(
                    onClick = {
                        db.collection("users").document(patientId)
                            .collection("alerts").document(alertDoc.id)
                            .delete()
                            .addOnFailureListener { e ->
                                android.util.Log.e(
                                    "BanDIT",
                                    "Error borrando alerta: ${e.message}",
                                    e
                                )
                            }
                        showDeleteConfirm = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BanDITColors.AlertRed)
                ) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar", color = BanDITColors.CyanPrimary)
                }
            }
        )
    }
}

@Composable
fun MedCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BanDITColors.NavyCard)
            .border(1.dp, BanDITColors.NavyBorder, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = BanDITColors.CyanPrimary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = BanDITColors.TextSecond,
                letterSpacing = 0.5.sp
            )
        }
        content()
    }
}

@Composable
fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = BanDITColors.TextMuted, fontSize = 13.sp)
        Text(
            value,
            color = BanDITColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun NoPatientLinkedCard() {
    MedCard(title = "Sin paciente vinculado", icon = Icons.Outlined.PersonOff) {
        Text(
            "Pide al paciente su correo y regístralo en tu perfil para monitorear sus signos en tiempo real.",
            color = BanDITColors.TextSecond,
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
    }
}

@Composable
fun CrisisAlertDialog(patientName: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = BanDITColors.NavyCard,
        shape = RoundedCornerShape(20.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "ALERTA DE CRISIS",
                    color = BanDITColors.AlertRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = 2.sp
                )
                Text("Intervención requerida", color = BanDITColors.TextSecond, fontSize = 13.sp)
            }
        },
        text = {
            Text(
                "$patientName ha activado el protocolo de emergencia y necesita asistencia inmediata. Revisa el historial de alertas para ver la ubicación y marcarla como atendida.",
                color = BanDITColors.TextPrimary,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BanDITColors.AlertRed)
            ) {
                Text(
                    "ENTENDIDO",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    )
}

@Composable
fun PendingRequests(patientId: String) {
    val db = FirebaseFirestore.getInstance()
    var requests by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }

    LaunchedEffect(Unit) {
        db.collection("users").document(patientId)
            .collection("caregivers")
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) requests = snapshot.documents
            }
    }

    if (requests.isEmpty()) {
        Text("No hay solicitudes pendientes", color = BanDITColors.TextSecond)
        return
    }

    requests.forEach { doc ->
        val caregiverName = doc.getString("caregiverName") ?: "Cuidador"
        val caregiverId = doc.id
        Column {
            Text(caregiverName, color = BanDITColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    doc.reference.update(
                        mapOf("status" to "accepted", "respondedAt" to System.currentTimeMillis())
                    )
                    db.collection("users").document(caregiverId)
                        .update("linkedPatientId", patientId)
                }) { Text("Aceptar") }

                OutlinedButton(onClick = {
                    doc.reference.update(
                        mapOf("status" to "rejected", "respondedAt" to System.currentTimeMillis())
                    )
                }) { Text("Rechazar") }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun FullScreenLoader() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = BanDITColors.CyanPrimary)
    }
}

private fun saveFcmToken(db: FirebaseFirestore, userId: String) {
    FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
        db.collection("users").document(userId).update("fcmToken", token)
    }
}
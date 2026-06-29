package me.aleesk.bandit

import android.Manifest
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
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// ─── Navigation Tabs ──────────────────────────────────────────────────────────

enum class DashboardTab { HOME, PROFILE }

// ─── Root Dashboard ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard(userId: String, onLogout: () -> Unit) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()

    var userName by remember { mutableStateOf("") }
    var userRole by remember { mutableStateOf("") }
    var userEmail by remember { mutableStateOf("") }
    var isConnected by remember { mutableStateOf(false) }
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
                    isConnected = !snapshot.metadata.isFromCache
                    saveFcmToken(db, userId)
                }
            }
        onDispose { listener.remove() }
    }

    LaunchedEffect(userId) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    userName = doc.getString("name") ?: "Usuario"
                    userRole = doc.getString("role") ?: "patient"
                    userEmail = doc.getString("email").toString()
                    saveFcmToken(db, userId)
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Error al cargar datos", Toast.LENGTH_SHORT).show()
            }
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
                                if (!isConnected) BanDITColors.SuccessGreenDim
                                else Color(0xFF2D1B0E)
                            )
                            .border(
                                1.dp,
                                if (!isConnected) BanDITColors.SuccessGreen
                                else BanDITColors.WarnOrange,
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
                                        if (!isConnected) BanDITColors.SuccessGreen
                                        else BanDITColors.WarnOrange
                                    )
                            )
                            Text(
                                if (!isConnected) "CONECTADO" else "DESCONECTADO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = if (!isConnected) BanDITColors.SuccessGreen
                                else BanDITColors.WarnOrange
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
                    db = db,
                    modifier = Modifier.padding(padding)
                )
            currentTab == DashboardTab.HOME && userRole == "caregiver" ->
                CaregiverHomeScreen(
                    userId = userId,
                    db = db,
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

// ─── Patient Home ─────────────────────────────────────────────────────────────

private const val BACKEND_URL = "http://192.168.1.81:3000"

@Composable
fun PatientHomeScreen(userId: String, db: FirebaseFirestore, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // ── Estado BLE ───────────────────────────────────────
    var bleConectado by remember { mutableStateOf(false) }
    var bpmActual    by remember { mutableStateOf(0) }
    var alertaActiva by remember { mutableStateOf(false) }

    var isSendingAlert by remember { mutableStateOf(false) }

    // ── Conexión al ForegroundService ────────────────────
    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val svc = (binder as BleService.LocalBinder).service
                svc.onConnectionChange = { connected -> bleConectado = connected }
                svc.onBpmUpdate        = { bpm -> bpmActual = bpm }
                svc.onAlertReceived    = { active ->
                    alertaActiva = active
                    // Si la pulsera activa la alerta, también lo marcamos en Firestore
                    // para que el cuidador reciba la notificación
                    if (active) {
                        db.collection("users").document(userId).update(
                            mapOf(
                                "isCrisis"      to true,
                                "lastAlertTime" to Timestamp.now()
                            )
                        )
                        scope.launch { sendAlertToBackend(userId) }
                    } else {
                        db.collection("users").document(userId).update("isCrisis", false)
                    }
                }
            }
            override fun onServiceDisconnected(name: ComponentName) {
                bleConectado = false
            }
        }
    }

    // ── Permisos BLE ─────────────────────────────────────
    val blePerms = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            BleService.startBleService(context)
            context.bindService(
                Intent(context, BleService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        } else {
            Toast.makeText(
                context,
                "Se necesitan permisos Bluetooth para conectar la pulsera",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ── Arranque ──────────────────────────────────────────
    LaunchedEffect(Unit) {
        BanDITMessagingService.createNotificationChannel(context)

        // Permiso notificaciones (ya existía)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }

        // Permisos BLE — si ya los tiene, arranca el servicio directo
        val allGranted = blePerms.all {
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
            permLauncher.launch(blePerms)
        }
    }

    // Desvincula al salir (el servicio sigue corriendo en background)
    DisposableEffect(Unit) {
        onDispose {
            runCatching { context.unbindService(serviceConnection) }
        }
    }

    // ── UI ────────────────────────────────────────────────
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BanDITColors.NavyDeep)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Indicador de conexión BLE con botón para reconectar manualmente
        BleStatusCard(
            connected = bleConectado,
            onReconnect = {
                if (blePerms.all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }) {
                    BleService.startBleService(context)
                    context.bindService(
                        Intent(context, BleService::class.java),
                        serviceConnection,
                        Context.BIND_AUTO_CREATE
                    )
                } else {
                    permLauncher.launch(blePerms)
                }
            }
        )

        BpmHeroCard(heartRate = bpmActual)
        AlertHistoryCard(alerts = listOf())
        Spacer(Modifier.height(4.dp))

        // Alerta activa desde la pulsera
        if (alertaActiva) {
            CrisisAlertDialog(
                patientName = "tú",
                onDismiss = {
                    alertaActiva = false
                    db.collection("users").document(userId).update("isCrisis", false)
                }
            )
        }

        // Botón alerta manual (sin cambios respecto al original)
        Button(
            onClick = {
                if (isSendingAlert) return@Button
                isSendingAlert = true
                scope.launch {
                    db.collection("users").document(userId).update(
                        mapOf(
                            "isCrisis"      to true,
                            "lastAlertTime" to Timestamp.now()
                        )
                    )
                    val result = sendAlertToBackend(userId)
                    isSendingAlert = false
                    Toast.makeText(
                        context,
                        if (result) "✓ Alerta enviada al cuidador"
                        else "Alerta registrada (no se pudo notificar al cuidador)",
                        Toast.LENGTH_LONG
                    ).show()
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

private suspend fun sendAlertToBackend(patientId: String): Boolean =
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
            connection.disconnect()
            responseCode in 200..299
        } catch (e: Exception) {
            android.util.Log.e("BanDIT", "Error enviando alerta: ${e.message}")
            false
        }
    }

// ─── Caregiver Home ───────────────────────────────────────────────────────────

@Composable
fun CaregiverHomeScreen(userId: String, db: FirebaseFirestore, modifier: Modifier = Modifier) {
    var patientName      by remember { mutableStateOf<String?>(null) }
    var patientHeartRate by remember { mutableStateOf<Int?>(null) }
    var linkedPatientId  by remember { mutableStateOf<String?>(null) }
    var isLoading        by remember { mutableStateOf(true) }
    var showCrisisDialog by remember { mutableStateOf(false) }

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

    DisposableEffect(linkedPatientId) {
        val patientListener = linkedPatientId?.let { pid ->
            db.collection("users").document(pid)
                .addSnapshotListener { snap, _ ->
                    if (snap != null && snap.exists()) {
                        patientName      = snap.getString("name")
                        patientHeartRate = (snap.getLong("heartRate") ?: 0).toInt()
                        if (snap.getBoolean("isCrisis") == true) showCrisisDialog = true
                    }
                }
        }
        onDispose { patientListener?.remove() }
    }

    LaunchedEffect(userId) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                linkedPatientId = doc.getString("linkedPatientId")
                isLoading = false
                linkedPatientId?.let { pid ->
                    db.collection("users").document(pid).addSnapshotListener { snap, _ ->
                        if (snap != null && snap.exists()) {
                            patientName      = snap.getString("name")
                            patientHeartRate = (snap.getLong("heartRate") ?: 0).toInt()
                            if (snap.getBoolean("isCrisis") == true) showCrisisDialog = true
                        }
                    }
                }
            }
            .addOnFailureListener { isLoading = false }
    }

    if (showCrisisDialog) {
        CrisisAlertDialog(
            patientName = patientName ?: "el paciente",
            onDismiss = {
                showCrisisDialog = false
                linkedPatientId?.let {
                    db.collection("users").document(it).update("isCrisis", false)
                }
            }
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
                AlertHistoryCard(alerts = emptyList())
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
    val context = LocalContext.current

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
                if (connected) "Pulsera conectada" else "Buscando pulsera...",
                color = if (connected) BanDITColors.SuccessGreen else BanDITColors.WarnOrange,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        // Botón de reconexión manual — visible solo cuando está desconectada
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

@Composable
fun AlertHistoryCard(alerts: List<String>) {
    MedCard(title = "Historial de Alertas", icon = Icons.Outlined.Notifications) {
        if (alerts.isEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = BanDITColors.SuccessGreen,
                    modifier = Modifier.size(20.dp)
                )
                Text("Sin alertas recientes", color = BanDITColors.TextSecond, fontSize = 14.sp)
            }
        } else {
            alerts.forEach { alert ->
                Text(
                    "• $alert",
                    color = BanDITColors.TextPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
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
                "$patientName ha activado el protocolo de emergencia y necesita asistencia inmediata.",
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
                    "ATENDER / APAGAR ALARMA",
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
    var requests by remember {
        mutableStateOf<List<com.google.firebase.firestore.DocumentSnapshot>>(emptyList())
    }

    LaunchedEffect(Unit) {
        db.collection("caregiver_requests")
            .whereEqualTo("patientId", patientId)
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
        Column {
            Text(caregiverName, color = BanDITColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val caregiverId = doc.getString("caregiverId") ?: return@Button
                    db.collection("patient_caregivers").add(
                        mapOf(
                            "patientId"   to patientId,
                            "caregiverId" to caregiverId,
                            "createdAt"   to System.currentTimeMillis()
                        )
                    )
                    doc.reference.update("status", "accepted")
                    db.collection("users").document(caregiverId)
                        .update("linkedPatientId", patientId)
                }) { Text("Aceptar") }

                OutlinedButton(onClick = {
                    doc.reference.update("status", "rejected")
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
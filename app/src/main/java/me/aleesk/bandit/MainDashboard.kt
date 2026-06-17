package me.aleesk.bandit

import android.util.Log
import android.widget.Toast
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

// ─── Navigation Tabs ──────────────────────────────────────────────────────────

enum class DashboardTab { HOME, PROFILE }

// ─── Root Dashboard ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard(userId: String, onLogout: () -> Unit) {
    val context = LocalContext.current
    val db      = FirebaseFirestore.getInstance()

    var userName   by remember { mutableStateOf("") }
    var userRole   by remember { mutableStateOf("") }
    var isConnected by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf(DashboardTab.HOME) }

    // Load user profile once
    LaunchedEffect(userId) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    userName = doc.getString("name") ?: "Usuario"
                    userRole = doc.getString("role") ?: "patient"
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
                    // Connection pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (!isConnected) BanDITColors.SuccessGreenDim else Color(0xFF2D1B0E))
                            .border(1.dp, if (!isConnected) BanDITColors.SuccessGreen else BanDITColors.WarnOrange, RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier.size(7.dp).clip(CircleShape)
                                    .background(if (!isConnected) BanDITColors.SuccessGreen else BanDITColors.WarnOrange)
                            )
                            Text(
                                if (!isConnected) "CONECTADO" else "DESCONECTADO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = if (!isConnected) BanDITColors.SuccessGreen else BanDITColors.WarnOrange
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Outlined.Logout, contentDescription = "Salir", tint = BanDITColors.TextSecond)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BanDITColors.NavySurface)
            )
        },
        bottomBar = {
            if (userRole.isNotEmpty()) {
                NavigationBar(containerColor = BanDITColors.NavySurface, tonalElevation = 0.dp) {
                    NavigationBarItem(
                        selected = currentTab == DashboardTab.HOME,
                        onClick  = { currentTab = DashboardTab.HOME },
                        icon     = { Icon(Icons.Outlined.MonitorHeart, contentDescription = null) },
                        label    = { Text("Inicio") },
                        colors   = NavigationBarItemDefaults.colors(
                            selectedIconColor      = BanDITColors.CyanPrimary,
                            selectedTextColor      = BanDITColors.CyanPrimary,
                            unselectedIconColor    = BanDITColors.TextMuted,
                            unselectedTextColor    = BanDITColors.TextMuted,
                            indicatorColor         = BanDITColors.CyanDim
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == DashboardTab.PROFILE,
                        onClick  = { currentTab = DashboardTab.PROFILE },
                        icon     = { Icon(Icons.Outlined.Person, contentDescription = null) },
                        label    = { Text("Perfil") },
                        colors   = NavigationBarItemDefaults.colors(
                            selectedIconColor      = BanDITColors.CyanPrimary,
                            selectedTextColor      = BanDITColors.CyanPrimary,
                            unselectedIconColor    = BanDITColors.TextMuted,
                            unselectedTextColor    = BanDITColors.TextMuted,
                            indicatorColor         = BanDITColors.CyanDim
                        )
                    )
                }
            }
        }
    ) { padding ->
        when {
            userRole.isEmpty() -> FullScreenLoader()
            currentTab == DashboardTab.HOME && userRole == "patient" ->
                PatientHomeScreen(userId = userId, db = db, modifier = Modifier.padding(padding))
            currentTab == DashboardTab.HOME && userRole == "caregiver" ->
                CaregiverHomeScreen(userId = userId, db = db, modifier = Modifier.padding(padding))
            currentTab == DashboardTab.PROFILE ->
                ProfileScreen(userId = userId, userName = userName, userRole = userRole, db = db, modifier = Modifier.padding(padding))
        }
    }
}

// ─── Patient Home ─────────────────────────────────────────────────────────────

@Composable
fun PatientHomeScreen(userId: String, db: FirebaseFirestore, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val heartRate by remember { mutableStateOf(72) }
    val alertHistory = remember { listOf<String>() } // TODO: pull from Firestore

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BanDITColors.NavyDeep)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // BPM Hero Card
        BpmHeroCard(heartRate = heartRate)

        // Alert History
        AlertHistoryCard(alerts = alertHistory)

        Spacer(Modifier.height(4.dp))

        // Manual Alert Button
        Button(
            onClick = {
                Toast.makeText(context, "Transmitiendo alerta...", Toast.LENGTH_SHORT).show()
                db.collection("users").document(userId).update(
                    mapOf(
                        "isCrisis"       to true,
                        "lastAlertTime"  to com.google.firebase.Timestamp.now()
                    )
                ).addOnSuccessListener {
                    Toast.makeText(context, "Alerta enviada al cuidador", Toast.LENGTH_LONG).show()
                }.addOnFailureListener {
                    Toast.makeText(context, "Error de red al alertar", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape  = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BanDITColors.AlertRed)
        ) {
            Icon(Icons.Outlined.Warning, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(10.dp))
            Text("ACTIVAR ALERTA MANUAL", fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
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

    LaunchedEffect(userId) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                linkedPatientId = doc.getString("linkedPatientId")
                isLoading = false
                linkedPatientId?.let { pid ->
                    db.collection("users").document(pid).addSnapshotListener { snap, _ ->
                        if (snap != null && snap.exists()) {
                            patientName      = snap.getString("name")
                            patientHeartRate = (snap.getLong("heartRate") ?: 78).toInt()
                            if (snap.getBoolean("isCrisis") == true) showCrisisDialog = true
                        }
                    }
                }
            }.addOnFailureListener { isLoading = false }
    }

    // Crisis Dialog
    if (showCrisisDialog) {
        CrisisAlertDialog(
            patientName = patientName ?: "el paciente",
            onDismiss   = {
                showCrisisDialog = false
                linkedPatientId?.let { db.collection("users").document(it).update("isCrisis", false) }
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
            isLoading            -> FullScreenLoader()
            linkedPatientId == null -> NoPatientLinkedCard()
            else -> {
                BpmHeroCard(heartRate = patientHeartRate ?: 0, label = "FC de ${patientName ?: "Paciente"}")
                AlertHistoryCard(alerts = emptyList())
            }
        }
    }
}

// ── Profile Screen ──

@Composable
fun ProfileScreen(
    userId: String,
    userName: String,
    userRole: String,
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
        // Avatar + Name header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(BanDITColors.CyanDim.copy(alpha = 0.4f), BanDITColors.NavyCard)
                    )
                )
                .border(1.dp, BanDITColors.NavyBorder, RoundedCornerShape(20.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape)
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
                Text(userName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = BanDITColors.TextPrimary)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(BanDITColors.CyanDim)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (userRole == "patient") "PACIENTE" else "CUIDADOR",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp, color = BanDITColors.CyanPrimary
                    )
                }
            }
        }

        // Patient code (only for patients)
        if (userRole == "patient") {
            MedCard(title = "Código de Paciente", icon = Icons.Outlined.QrCode) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Comparte este código con tu cuidador", color = BanDITColors.TextSecond, fontSize = 13.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(BanDITColors.NavyDeep)
                            .border(1.dp, BanDITColors.CyanMuted, RoundedCornerShape(10.dp))
                            .clickable {
                                val cb = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                cb.setPrimaryClip(android.content.ClipData.newPlainText("userId", userId))
                                Toast.makeText(context, "Código copiado", Toast.LENGTH_SHORT).show()
                            }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            userId,
                            color = BanDITColors.CyanPrimary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copiar", tint = BanDITColors.TextSecond, modifier = Modifier.size(18.dp))
                    }
                }
            }
            MedCard(
                title = "Solicitudes de cuidadores",
                icon = Icons.Outlined.PersonAdd
            ) {
                PendingRequests(userId)
            }
        }

        // Info items
        MedCard(title = "Información de Cuenta", icon = Icons.Outlined.ManageAccounts) {
            ProfileInfoRow(label = "Nombre", value = userName)
            HorizontalDivider(color = BanDITColors.NavyBorder, modifier = Modifier.padding(vertical = 8.dp))
            ProfileInfoRow(label = "Rol", value = if (userRole == "patient") "Paciente" else "Cuidador / Familiar")
            HorizontalDivider(color = BanDITColors.NavyBorder, modifier = Modifier.padding(vertical = 8.dp))
            ProfileInfoRow(label = "ID de Usuario", value = userId.take(8) + "…")
        }
    }
}

// ─── Reusable Components ──────────────────────────────────────────────────────

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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Favorite, contentDescription = null, tint = BanDITColors.AlertRed, modifier = Modifier.size(18.dp))
                Text(label, color = BanDITColors.TextSecond, fontSize = 13.sp, letterSpacing = 0.5.sp)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "$heartRate",
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    color = BanDITColors.CyanPrimary,
                    lineHeight = 72.sp
                )
                Text("BPM", fontSize = 22.sp, fontWeight = FontWeight.Medium, color = BanDITColors.TextSecond, modifier = Modifier.padding(bottom = 12.dp))
            }
            Text("EN TIEMPO REAL", fontSize = 10.sp, letterSpacing = 2.sp, color = BanDITColors.TextMuted)
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
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = BanDITColors.SuccessGreen, modifier = Modifier.size(20.dp))
                Text("Sin alertas recientes", color = BanDITColors.TextSecond, fontSize = 14.sp)
            }
        } else {
            alerts.forEach { alert ->
                Text("• $alert", color = BanDITColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = BanDITColors.CyanPrimary, modifier = Modifier.size(18.dp))
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BanDITColors.TextSecond, letterSpacing = 0.5.sp)
        }
        content()
    }
}

@Composable
fun ProfileInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = BanDITColors.TextMuted, fontSize = 13.sp)
        Text(value, color = BanDITColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun NoPatientLinkedCard() {
    MedCard(title = "Sin paciente vinculado", icon = Icons.Outlined.PersonOff) {
        Text("Pide al paciente su código de 28 caracteres y regístralo en tu perfil para monitorear sus signos en tiempo real.", color = BanDITColors.TextSecond, fontSize = 14.sp, lineHeight = 22.sp)
    }
}

@Composable
fun CrisisAlertDialog(patientName: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        containerColor   = BanDITColors.NavyCard,
        shape            = RoundedCornerShape(20.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ALERTA DE CRISIS", color = BanDITColors.AlertRed, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 2.sp)
                Text("Intervención requerida", color = BanDITColors.TextSecond, fontSize = 13.sp)
            }
        },
        text = {
            Text(
                "$patientName ha activado el protocolo de emergencia y necesita asistencia inmediata.",
                color = BanDITColors.TextPrimary, fontSize = 15.sp, lineHeight = 22.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape  = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BanDITColors.AlertRed)
            ) {
                Text("ATENDER / APAGAR ALARMA", fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.5.sp)
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

                if (snapshot != null) {
                    requests = snapshot.documents
                }
            }
    }

    if (requests.isEmpty()) {

        Text(
            "No hay solicitudes pendientes",
            color = BanDITColors.TextSecond
        )

        return
    }

    requests.forEach { doc ->

        val caregiverName =
            doc.getString("caregiverName") ?: "Cuidador"

        Column {

            Text(
                caregiverName,
                color = BanDITColors.TextPrimary
            )

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                Button(
                    onClick = {

                        val caregiverId =
                            doc.getString("caregiverId") ?: return@Button

                        db.collection("patient_caregivers")
                            .add(
                                mapOf(
                                    "patientId" to patientId,
                                    "caregiverId" to caregiverId,
                                    "createdAt" to System.currentTimeMillis()
                                )
                            )

                        doc.reference.update(
                            "status",
                            "accepted"
                        )
                    }
                ) {
                    Text("Aceptar")
                }

                OutlinedButton(
                    onClick = {
                        doc.reference.update(
                            "status",
                            "rejected"
                        )
                    }
                ) {
                    Text("Rechazar")
                }
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

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun saveFcmToken(
    db: FirebaseFirestore,
    userId: String
) {
    FirebaseMessaging.getInstance().token
        .addOnSuccessListener { token ->
            db.collection("users")
                .document(userId)
                .update("fcmToken", token)
        }
}
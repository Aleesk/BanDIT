package me.aleesk.bandit

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard(userId: String, onLogout: () -> Unit) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()

    var userName by remember { mutableStateOf("Cargando...") }
    var userRole by remember { mutableStateOf("") }
    val heartRate by remember { mutableStateOf(78) }
    val isConnected by remember { mutableStateOf(false) }

    LaunchedEffect(userId) {
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    userName = document.getString("name") ?: "Usuario"
                    userRole = document.getString("role") ?: "patient"

                    if (userRole == "caregiver") {
                        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                            .addOnSuccessListener { token ->
                                db.collection("users").document(userId)
                                    .update("fcmToken", token)
                                    .addOnSuccessListener {
                                        Log.d("FCM_AUTOMATION", "Token del cuidador guardado con éxito en Firestore")
                                    }
                            }
                            .addOnFailureListener { e ->
                                Log.e("FCM_AUTOMATION", "Error al obtener el Token FCM", e)
                            }
                    }
                }
            }
            .addOnFailureListener {
                userName = "Usuario"
                Toast.makeText(context, "Error al cargar datos", Toast.LENGTH_SHORT).show()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BanDIT") },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("Cerrar Sesión")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "Bienvenido,", fontSize = 22.sp)
            Text(text = userName, fontSize = 26.sp, fontWeight = FontWeight.Bold)

            // Estado del BanDIt
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isConnected) Color(0xFF4CAF50) else Color(0xFFFF9800)
                )
            ) {
                Text(
                    text = if (isConnected) "✅ BanDIT Conectado" else "❌ BanDIT Desconectado",
                    modifier = Modifier.padding(16.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            when (userRole) {
                "patient" -> PatientContent(
                    userId = userId,
                    heartRate = heartRate,
                    db = db
                )

                "caregiver" -> CaregiverContent(
                    userId = userId,
                    db = db
                )

                "" -> {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

// ==========================================
// PANTALLA DEL PACIENTE
// ==========================================

@Composable
fun PatientContent(
    userId: String,
    heartRate: Int,
    db: FirebaseFirestore
) {
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // Frecuencia cardíaca
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Frecuencia Cardíaca", fontSize = 18.sp)
                Text(
                    "$heartRate BPM",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2)
                )
            }
        }

        // Código de Paciente (Copiable)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Código de Paciente", color = Color.Blue, fontWeight = FontWeight.Bold)
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = userId,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF1976D2),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("userId", userId)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Código copiado 📋", Toast.LENGTH_SHORT).show()
                            }
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Última Alerta", color = Color.Red, fontWeight = FontWeight.Bold)
                Text("Sin alertas recientes", fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                Toast.makeText(context, "🚨 Transmitiendo alerta de crisis...", Toast.LENGTH_SHORT).show()

                val alertUpdates = mapOf(
                    "isCrisis" to true,
                    "lastAlertTime" to com.google.firebase.Timestamp.now()
                )

                db.collection("users").document(userId)
                    .update(alertUpdates)
                    .addOnSuccessListener {
                        Toast.makeText(context, "🚨 Alerta enviada a tu cuidador", Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "❌ Error de red al alertar", Toast.LENGTH_SHORT).show()
                    }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
        ) {
            Text("🚨 ACTIVAR ALERTA MANUAL", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ==========================================
// PANTALLA DEL CUIDADOR / FAMILIAR
// ==========================================

@Composable
fun CaregiverContent(userId: String, db: FirebaseFirestore) {
    var patientName by remember { mutableStateOf<String?>(null) }
    var patientHeartRate by remember { mutableStateOf<Int?>(null) }
    var linkedPatientId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Control del diálogo emergente de alerta
    var showCrisisDialog by remember { mutableStateOf(false) }

    LaunchedEffect(userId) {
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { doc ->
                linkedPatientId = doc.getString("linkedPatientId")
                isLoading = false

                if (linkedPatientId != null) {
                    // Escucha activa en tiempo real al paciente vinculado
                    db.collection("users").document(linkedPatientId!!)
                        .addSnapshotListener { snapshot, _ ->
                            if (snapshot != null && snapshot.exists()) {
                                patientName = snapshot.getString("name")
                                patientHeartRate = (snapshot.getLong("heartRate") ?: 78).toInt()

                                // REVISIÓN DE CRISIS: Si cambia a true, activa el Pop-up de inmediato
                                val isCrisis = snapshot.getBoolean("isCrisis") ?: false
                                if (isCrisis) {
                                    showCrisisDialog = true
                                }
                            }
                        }
                }
            }
            .addOnFailureListener {
                isLoading = false
            }
    }

    if (showCrisisDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    text = "🚨 ¡ALERTA DE CRISIS!",
                    color = Color.Red,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
            },
            text = {
                Text(
                    text = "El paciente $patientName necesita asistencia inmediata. Ha activado el protocolo de emergencia.",
                    fontSize = 16.sp
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    onClick = {
                        showCrisisDialog = false
                        linkedPatientId?.let { pid ->
                            db.collection("users").document(pid).update("isCrisis", false)
                        }
                    }
                ) {
                    Text("ATENDER / APAGAR ALARMA", color = Color.White)
                }
            }
        )
    }

    // UI Estándar del Cuidador
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            linkedPatientId == null -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Sin paciente vinculado",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Pide al paciente su código y regístralo para poder monitorear sus signos.",
                            color = Color.Gray
                        )
                    }
                }
            }

            else -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Monitoreando a:",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.Gray
                        )
                        Text(
                            patientName ?: "Paciente",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Frecuencia Cardíaca", fontSize = 16.sp)
                        Text(
                            "${patientHeartRate ?: "--"} BPM",
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1976D2)
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Red.copy(alpha = 0.1f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Última Alerta",
                            color = Color.Red,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Sin alertas recientes", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
package me.aleesk.bandit

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
    var heartRate by remember { mutableStateOf(78) }
    var isConnected by remember { mutableStateOf(false) }

    LaunchedEffect(userId) {
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    userName = document.getString("name") ?: "Usuario"
                    userRole = document.getString("role") ?: "patient"
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
                    text = if (isConnected) "✅ BanDIt Conectado" else "❌ BanDIt Desconectado",
                    modifier = Modifier.padding(16.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            // Contenido según rol (espera a que userRole cargue)
            when (userRole) {
                "patient" -> PatientContent(
                    userId = userId,
                    heartRate = heartRate,
                    isConnected = isConnected,
                    onSimulate = { heartRate = (60..115).random() },
                    onManualAlert = {
                        Toast.makeText(context, "🚨 Alerta manual activada", Toast.LENGTH_LONG).show()
                    }
                )
                "caregiver" -> CaregiverContent(
                    userId = userId,
                    db = db
                )
                "" -> {
                    // Todavía cargando el rol, mostrar indicador
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

// Pantalla del Paciente

@Composable
fun PatientContent(
    userId: String,
    heartRate: Int,
    isConnected: Boolean,
    onSimulate: () -> Unit,
    onManualAlert: () -> Unit
) {
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

        Card(modifier = Modifier.fillMaxWidth()) {
            val context = LocalContext.current

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp) // Añadimos padding para que no pegue a los bordes
            ) {
                Text(
                    text = userId,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF1976D2),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { // <-- La lógica de copiar ahora va aquí
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager

                            val clip = android.content.ClipData.newPlainText("userId", userId)
                            clipboard.setPrimaryClip(clip)

                            Toast.makeText(context, "Código copiado 📋", Toast.LENGTH_SHORT).show()
                        }
                )
            }
        }

        // Última alerta
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
            onClick = onManualAlert,
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
        ) {
            Text("🚨 ACTIVAR ALERTA MANUAL", fontSize = 16.sp)
        }

        Button(
            onClick = onSimulate,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Simular Pulso")
        }

        Button(
            onClick = { /* próximamente */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("👥 Gestionar Contactos de Emergencia")
        }
    }
}

// Pantalla del Cuidador/Familiar

@Composable
fun CaregiverContent(userId: String, db: FirebaseFirestore) {
    var patientName by remember { mutableStateOf<String?>(null) }
    var patientHeartRate by remember { mutableStateOf<Int?>(null) }
    var linkedPatientId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Primero carga el ID del paciente vinculado, luego escucha sus datos en tiempo real
    LaunchedEffect(userId) {
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { doc ->
                linkedPatientId = doc.getString("linkedPatientId")
                isLoading = false

                if (linkedPatientId != null) {
                    db.collection("users").document(linkedPatientId!!)
                        .addSnapshotListener { snapshot, _ ->
                            if (snapshot != null && snapshot.exists()) {
                                patientName = snapshot.getString("name")
                                patientHeartRate = (snapshot.getLong("heartRate") ?: 78).toInt()
                            }
                        }
                }
            }
            .addOnFailureListener {
                isLoading = false
            }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            linkedPatientId == null -> {
                // No tiene paciente vinculado
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Sin paciente vinculado",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Pide al paciente su código y crea una cuenta nueva ingresándolo en el campo 'Código del paciente'.",
                            color = Color.Gray
                        )
                    }
                }
            }

            else -> {
                // Tiene paciente vinculado, muestra sus datos
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
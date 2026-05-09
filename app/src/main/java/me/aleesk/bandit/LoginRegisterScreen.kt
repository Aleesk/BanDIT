package me.aleesk.bandit

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun LoginRegisterScreen(onLoginSuccess: (String) -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var isLogin by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("patient") }
    // IMPORTANTE: patientCode debe estar aquí arriba, no dentro del if
    var patientCode by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "BanDIT", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = if (isLogin) "Iniciar Sesión" else "Crear Cuenta",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (!isLogin) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre completo") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text("¿Cómo usarás la app?", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedRole == "patient",
                    onClick = { selectedRole = "patient" },
                    label = { Text("Soy paciente") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedRole == "caregiver",
                    onClick = { selectedRole = "caregiver" },
                    label = { Text("Soy familiar") },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (selectedRole == "caregiver") {
                OutlinedTextField(
                    value = patientCode,
                    onValueChange = { patientCode = it },
                    label = { Text("Código del paciente (opcional)") },
                    supportingText = { Text("El paciente lo encuentra en su perfil") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Correo electrónico") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña (mín. 6 caracteres)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (email.isBlank() || password.length < 6 || (!isLogin && name.isBlank())) {
                    errorMessage = "Completa todos los campos correctamente"
                    return@Button
                }

                isLoading = true
                errorMessage = ""

                if (isLogin) {
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            isLoading = false
                            if (task.isSuccessful) {
                                val uid = auth.currentUser?.uid
                                if (!uid.isNullOrBlank()) {
                                    onLoginSuccess(uid)
                                } else {
                                    errorMessage = "Error al obtener usuario"
                                }
                            } else {
                                errorMessage = task.exception?.message ?: "Error al iniciar sesión"
                            }
                        }
                } else {
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener { result ->
                            val uid = result.user?.uid
                            if (uid.isNullOrBlank()) {
                                isLoading = false
                                errorMessage = "Error: no se pudo obtener el usuario"
                                return@addOnSuccessListener
                            }

                            val userData = hashMapOf<String, Any>(
                                "name" to name,
                                "email" to email,
                                "role" to selectedRole,
                                "createdAt" to System.currentTimeMillis()
                            )

                            if (selectedRole == "caregiver" && patientCode.isNotBlank()) {
                                userData["linkedPatientId"] = patientCode
                            }

                            db.collection("users").document(uid).set(userData)
                                .addOnSuccessListener {
                                    isLoading = false
                                    Toast.makeText(context, "¡Cuenta creada!", Toast.LENGTH_LONG).show()
                                    onLoginSuccess(uid)
                                }
                                .addOnFailureListener { e ->
                                    isLoading = false
                                    errorMessage = e.message ?: "Error al guardar datos"
                                }
                        }
                        .addOnFailureListener { e ->
                            isLoading = false
                            errorMessage = e.message ?: "Error al crear cuenta"
                        }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text(if (isLogin) "Iniciar Sesión" else "Registrarse")
            }
        }

        TextButton(onClick = {
            isLogin = !isLogin
            errorMessage = ""
        }) {
            Text(if (isLogin) "¿No tienes cuenta? Regístrate" else "¿Ya tienes cuenta? Inicia sesión")
        }
    }
}
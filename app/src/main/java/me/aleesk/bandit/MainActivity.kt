package me.aleesk.bandit

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        setContent {
            BanDITTheme {
                BanDITApp(auth)
            }
        }
    }
}

sealed class AuthState {
    object Loading   : AuthState()
    object LoggedOut : AuthState()
    data class LoggedIn(val uid: String) : AuthState()
}

@Composable
fun BanDITApp(auth: FirebaseAuth) {
    var authState by remember { mutableStateOf<AuthState>(AuthState.Loading) }

    // Escuchador del estado de autenticación simplificado (Sin condiciones de carrera)
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { fa ->
            val user = fa.currentUser
            if (user == null) {
                authState = AuthState.LoggedOut
            } else {
                // Si ya existe un usuario al abrir la app, cargamos directo.
                // Si se acaba de crear, dejamos que la pantalla de registro termine su trabajo.
                if (authState is AuthState.Loading) {
                    authState = AuthState.LoggedIn(user.uid)
                }
            }
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    // Efecto secundario: Sincronizar el token
    if (authState is AuthState.LoggedIn) {
        val uid = (authState as AuthState.LoggedIn).uid

        LaunchedEffect(uid) {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    val userRef = FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(uid)

                    userRef.update("fcmToken", token)
                        .addOnSuccessListener { Log.d("FCM", "Token actualizado") }
                        .addOnFailureListener { e -> Log.e("FCM", "Error al actualizar token", e) }
                }
                .addOnFailureListener { e -> Log.e("FCM", "Error FCM", e) }
        }
    }

    // Navegación de pantallas
    when (val s = authState) {
        is AuthState.Loading   -> SplashScreen()
        is AuthState.LoggedOut -> LoginRegisterScreen { uid ->
            // Cuando LoginRegisterScreen avise que tuvo éxito, cambiamos el estado
            authState = AuthState.LoggedIn(uid)
        }
        is AuthState.LoggedIn  -> MainDashboard(userId = s.uid, onLogout = {
            auth.signOut()
            authState = AuthState.LoggedOut
        })
    }
}
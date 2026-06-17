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

    // Escuchador del estado de autenticación
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { fa ->

            val user = fa.currentUser

            if (user == null) {

                authState = AuthState.LoggedOut
                return@AuthStateListener
            }

            FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.uid)
                .get()
                .addOnSuccessListener { document ->

                    if (document.exists()) {

                        authState = AuthState.LoggedIn(user.uid)

                    } else {

                        FirebaseAuth.getInstance().signOut()

                        authState = AuthState.LoggedOut
                    }
                }
                .addOnFailureListener {

                    FirebaseAuth.getInstance().signOut()

                    authState = AuthState.LoggedOut
                }
        }

        auth.addAuthStateListener(listener)

        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    // Efecto secundario: Sincronizar el token sin destruir el registro de la pantalla
    if (authState is AuthState.LoggedIn) {
        val uid = (authState as AuthState.LoggedIn).uid

        LaunchedEffect(uid) {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    val userRef = FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(uid)

                    userRef.update("fcmToken", token)
                        .addOnSuccessListener {
                            Log.d("FCM", "Token actualizado")
                        }
                        .addOnFailureListener { e ->
                            Log.e(
                                "FCM",
                                "Documento de usuario inexistente",
                                e
                            )
                        }
                }
                .addOnFailureListener { e ->
                    Log.e("FCM", "Error al obtener el token de Firebase Messaging", e)
                }
        }
    }

    // Navegación de pantallas
    when (val s = authState) {
        is AuthState.Loading   -> SplashScreen()
        is AuthState.LoggedOut -> LoginRegisterScreen { /* El AuthStateListener se encarga de cambiar la pantalla */ }
        is AuthState.LoggedIn  -> MainDashboard(userId = s.uid, onLogout = auth::signOut)
    }
}
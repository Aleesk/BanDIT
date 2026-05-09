package me.aleesk.bandit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        setContent {
            BanDITApp(auth)
        }
    }
}

@Composable
fun BanDITApp(auth: FirebaseAuth) {

    var authState by remember { mutableStateOf<AuthState>(AuthState.Loading) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            authState = if (user != null) {
                AuthState.LoggedIn(user.uid)
            } else {
                AuthState.LoggedOut
            }
        }

        auth.addAuthStateListener(listener)

        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    when (val state = authState) {
        is AuthState.Loading -> {
            CircularProgressIndicator()
        }

        is AuthState.LoggedIn -> {
            MainDashboard(
                userId = state.uid,
                onLogout = {
                    auth.signOut()
                }
            )
        }

        is AuthState.LoggedOut -> {
            LoginRegisterScreen(
                onLoginSuccess = { /* ya no necesitas hacer nada aquí */ }
            )
        }
    }
}

sealed class AuthState {
    object Loading : AuthState()
    data class LoggedIn(val uid: String) : AuthState()
    object LoggedOut : AuthState()
}
package me.aleesk.bandit

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Permite usar `await()` sobre un Task de Firebase dentro de una función suspend,
 * sin necesidad de añadir la dependencia kotlinx-coroutines-play-services.
 */
private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { value -> cont.resume(value) }
    addOnFailureListener { exception -> cont.resumeWithException(exception) }
}

/**
 * Resultado de una operación de autenticación.
 * (Se llama AuthOutcome y no AuthResult para no chocar con
 * com.google.firebase.auth.AuthResult)
 */
sealed class AuthOutcome {
    data class Success(val uid: String) : AuthOutcome()
    data class Failure(val message: String) : AuthOutcome()
}

object AuthRepository {

    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()

    suspend fun login(email: String, password: String): AuthOutcome {
        return try {
            val signInResult = auth.signInWithEmailAndPassword(email, password).awaitResult()
            val uid = signInResult.user?.uid
                ?: return AuthOutcome.Failure("No se pudo obtener el usuario")

            val profile = db.collection("users").document(uid).get().awaitResult()
            if (!profile.exists()) {
                auth.signOut()
                return AuthOutcome.Failure("Usuario incompleto. Regístralo nuevamente.")
            }

            AuthOutcome.Success(uid)
        } catch (e: Exception) {
            // Si quedó una sesión a medio verificar, no la dejamos colgada.
            if (auth.currentUser != null) auth.signOut()
            AuthOutcome.Failure(mapAuthError(e))
        }
    }

    suspend fun register(
        email: String,
        password: String,
        name: String,
        role: String,
        patientEmail: String
    ): AuthOutcome {
        var createdAccount = false
        return try {
            val signUpResult = auth.createUserWithEmailAndPassword(email, password).awaitResult()
            val uid = signUpResult.user?.uid
                ?: return AuthOutcome.Failure("Error creando el usuario")
            createdAccount = true

            val userData = hashMapOf<String, Any>(
                "name" to name,
                "email" to email,
                "role" to role,
                "createdAt" to System.currentTimeMillis()
            )
            db.collection("users").document(uid).set(userData).awaitResult()

            if (role == "caregiver" && patientEmail.isNotBlank()) {
                createCaregiverRequest(
                    patientEmail = patientEmail,
                    caregiverId = uid,
                    caregiverName = name,
                    caregiverEmail = email
                )
            }

            AuthOutcome.Success(uid)
        } catch (e: Exception) {
            // Si la cuenta de Auth llegó a crearse pero algo después falló (p. ej.
            // la escritura en Firestore), la eliminamos para no dejar cuentas
            // huérfanas sin documento de perfil — esto es lo que antes te dejaba
            // "atascado" con un correo que Firebase ya consideraba en uso.
            if (createdAccount) {
                auth.currentUser?.delete()
            }
            AuthOutcome.Failure(mapAuthError(e))
        }
    }

    /**
     * Se llama una sola vez al iniciar la app (no en cada evento del
     * AuthStateListener) para evitar la condición de carrera que existía
     * entre el registro y la verificación del documento de Firestore.
     */
    suspend fun verifyUserDocumentExists(uid: String): Boolean {
        return try {
            db.collection("users").document(uid).get().awaitResult().exists()
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun createCaregiverRequest(
        patientEmail: String,
        caregiverId: String,
        caregiverName: String,
        caregiverEmail: String
    ) {
        try {
            val matches = db.collection("users")
                .whereEqualTo("email", patientEmail)
                .whereEqualTo("role", "patient")
                .get()
                .awaitResult()

            if (matches.isEmpty) return

            val patientDoc = matches.documents.first()
            val request = hashMapOf(
                "caregiverId" to caregiverId,
                "caregiverName" to caregiverName,
                "caregiverEmail" to caregiverEmail,
                "patientId" to patientDoc.id,
                "patientEmail" to patientEmail,
                "status" to "pending",
                "createdAt" to System.currentTimeMillis()
            )
            db.collection("caregiver_requests").add(request).awaitResult()
        } catch (e: Exception) {
            // No bloqueamos el registro si esto falla; el vínculo se puede
            // reintentar luego desde el perfil del cuidador.
        }
    }

    private fun mapAuthError(e: Exception): String = when (e) {
        is FirebaseAuthWeakPasswordException -> "La contraseña es demasiado débil"
        is FirebaseAuthInvalidCredentialsException -> "Correo o contraseña inválidos"
        is FirebaseAuthUserCollisionException -> "Ya existe una cuenta con este correo"
        is FirebaseAuthInvalidUserException -> "No existe una cuenta con este correo"
        else -> e.message ?: "Ocurrió un error inesperado"
    }
}
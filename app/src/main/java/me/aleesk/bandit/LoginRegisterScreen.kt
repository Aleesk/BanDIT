package me.aleesk.bandit

import android.util.Patterns
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun LoginRegisterScreen(onLoginSuccess: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var email        by remember { mutableStateOf("") }
    var password     by remember { mutableStateOf("") }
    var name         by remember { mutableStateOf("") }
    var isLogin      by remember { mutableStateOf(true) }
    var isLoading    by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("patient") }
    var patientEmail by remember { mutableStateOf("") }

    fun validationError(): String? {
        val trimmedEmail = email.trim()
        return when {
            trimmedEmail.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches() ->
                "Ingresa un correo electrónico válido"
            password.length < 6 ->
                "La contraseña debe tener al menos 6 caracteres"
            !isLogin && name.isBlank() ->
                "Ingresa tu nombre completo"
            !isLogin && selectedRole == "caregiver" && patientEmail.isBlank() ->
                "Ingresa el correo del paciente"
            !isLogin && selectedRole == "caregiver" &&
                    !Patterns.EMAIL_ADDRESS.matcher(patientEmail.trim()).matches() ->
                "El correo del paciente no es válido"
            else -> null
        }
    }

    fun submit() {
        validationError()?.let {
            errorMessage = it
            return
        }

        isLoading = true
        errorMessage = ""

        scope.launch {
            val outcome = if (isLogin) {
                AuthRepository.login(email.trim(), password)
            } else {
                AuthRepository.register(
                    email = email.trim(),
                    password = password,
                    name = name.trim(),
                    role = selectedRole,
                    patientEmail = patientEmail.trim()
                )
            }

            isLoading = false

            when (outcome) {
                is AuthOutcome.Success -> {
                    if (!isLogin) {
                        Toast.makeText(context, "Cuenta creada", Toast.LENGTH_LONG).show()
                    }
                    onLoginSuccess(outcome.uid)
                }
                is AuthOutcome.Failure -> {
                    errorMessage = outcome.message
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(BanDITColors.NavyDeep),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Logo
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(BanDITColors.CyanDim)
                    .border(1.dp, BanDITColors.CyanPrimary.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("B", fontSize = 32.sp, fontWeight = FontWeight.Black, color = BanDITColors.CyanPrimary)
            }

            Spacer(Modifier.height(16.dp))
            Text("BanDIT", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = BanDITColors.TextPrimary, letterSpacing = 3.sp)
            Text("Monitor Médico Inteligente", fontSize = 13.sp, color = BanDITColors.TextMuted, letterSpacing = 1.sp)
            Spacer(Modifier.height(36.dp))

            // Form card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(BanDITColors.NavyCard)
                    .border(1.dp, BanDITColors.NavyBorder, RoundedCornerShape(20.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    if (isLogin) "Iniciar Sesión" else "Crear Cuenta",
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BanDITColors.TextPrimary
                )

                if (!isLogin) {
                    MedTextField(
                        value = name, onValueChange = { name = it },
                        label = "Nombre completo", icon = Icons.Outlined.Person,
                        enabled = !isLoading
                    )

                    Text("Tipo de usuario", fontSize = 12.sp, color = BanDITColors.TextSecond, letterSpacing = 0.5.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RoleChip("Paciente", "patient", selectedRole) { selectedRole = it }
                        RoleChip("Cuidador", "caregiver", selectedRole) { selectedRole = it }
                    }

                    if (selectedRole == "caregiver") {
                        MedTextField(
                            value = patientEmail,
                            onValueChange = { patientEmail = it },
                            label = "Correo del paciente",
                            icon = Icons.Outlined.Person,
                            supportText = "El paciente lo encuentra en su perfil",
                            enabled = !isLoading
                        )
                    }
                }

                MedTextField(
                    value = email, onValueChange = { email = it },
                    label = "Correo electrónico", icon = Icons.Outlined.Mail,
                    enabled = !isLoading
                )
                MedTextField(
                    value = password, onValueChange = { password = it },
                    label = "Contraseña (mín. 6 caracteres)", icon = Icons.Outlined.Lock,
                    isPassword = true, enabled = !isLoading
                )

                if (errorMessage.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(BanDITColors.AlertRedDim)
                            .border(1.dp, BanDITColors.AlertRed.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(errorMessage, color = BanDITColors.AlertRed, fontSize = 13.sp)
                    }
                }

                Button(
                    onClick = { submit() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    enabled  = !isLoading,
                    colors   = ButtonDefaults.buttonColors(containerColor = BanDITColors.CyanMuted)
                ) {
                    if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                    else Text(if (isLogin) "Iniciar Sesión" else "Registrarse", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { isLogin = !isLogin; errorMessage = "" }, enabled = !isLoading) {
                Text(
                    if (isLogin) "¿No tienes cuenta? Regístrate" else "¿Ya tienes cuenta? Inicia sesión",
                    color = BanDITColors.CyanPrimary, fontSize = 14.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedTextField(
    value: String, onValueChange: (String) -> Unit,
    label: String, icon: ImageVector,
    isPassword: Boolean = false, supportText: String? = null,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label            = { Text(label, fontSize = 13.sp) },
        leadingIcon      = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        supportingText   = supportText?.let { { Text(it, fontSize = 11.sp) } },
        enabled          = enabled,
        singleLine       = true,
        modifier         = Modifier.fillMaxWidth(),
        shape            = RoundedCornerShape(12.dp),
        colors           = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = BanDITColors.CyanPrimary,
            unfocusedBorderColor = BanDITColors.NavyBorder,
            focusedLabelColor    = BanDITColors.CyanPrimary,
            unfocusedLabelColor  = BanDITColors.TextMuted,
            focusedLeadingIconColor   = BanDITColors.CyanPrimary,
            unfocusedLeadingIconColor = BanDITColors.TextMuted,
            cursorColor          = BanDITColors.CyanPrimary,
            focusedTextColor     = BanDITColors.TextPrimary,
            unfocusedTextColor   = BanDITColors.TextPrimary,
            unfocusedContainerColor = BanDITColors.NavyDeep,
            focusedContainerColor   = BanDITColors.NavyDeep
        )
    )
}

@Composable
fun RowScope.RoleChip(label: String, role: String, selected: String, onClick: (String) -> Unit) {
    val isSelected = selected == role
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) BanDITColors.CyanDim else BanDITColors.NavyDeep)
            .border(1.dp, if (isSelected) BanDITColors.CyanPrimary else BanDITColors.NavyBorder, RoundedCornerShape(12.dp))
            .clickable { onClick(role) }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) BanDITColors.CyanPrimary else BanDITColors.TextMuted
        )
    }
}
package com.example.upaos.ui.login

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.upaos.data.api.RetrofitClient
import com.example.upaos.data.local.CuentaGuardada
import com.example.upaos.data.local.TokenManager
import com.example.upaos.data.model.LoginRequest
import com.example.upaos.service.FcmTokenHelper
import com.example.upaos.ui.components.AppCard
import com.example.upaos.ui.components.EmptyState
import com.example.upaos.ui.components.PrimaryButton
import com.example.upaos.ui.components.ReusableDialog
import com.example.upaos.ui.components.cursoColor
import kotlinx.coroutines.launch

@Composable
fun EligeCuentaScreen(
    onLoginSuccess: (String) -> Unit,
    onGoToLogin: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }
    val scope = rememberCoroutineScope()

    var cuentas by remember { mutableStateOf(tokenManager.getCuentas()) }
    var autenticando by remember { mutableStateOf<String?>(null) }
    var eliminarCuenta by remember { mutableStateOf<CuentaGuardada?>(null) }

    fun iniciarSesionCon(cuenta: CuentaGuardada) {
        if (autenticando != null) return
        autenticando = cuenta.usuario
        scope.launch {
            try {
                val res = RetrofitClient.apiService.login(LoginRequest(cuenta.usuario, cuenta.password))
                val body = res.body()
                if (res.isSuccessful && body?.success == true && body.token != null) {
                    tokenManager.saveToken(body.token)
                    tokenManager.setKeepLoggedIn(true)
                    tokenManager.saveUserId(cuenta.usuario)
                    tokenManager.saveCuenta(cuenta.usuario, cuenta.password, cuenta.nombre)
                    FcmTokenHelper.register(context)
                    autenticando = null
                    onLoginSuccess(body.token)
                } else {
                    autenticando = null
                    Toast.makeText(
                        context,
                        "No se pudo iniciar sesión con la cuenta guardada. Reingresa tu contraseña.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                autenticando = null
                Toast.makeText(context, "Error de conexión: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Elige una cuenta",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Cuentas guardadas en este dispositivo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (cuentas.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Person,
                    title = "No hay cuentas guardadas",
                    subtitle = "Guarda tu contraseña al iniciar sesión para verla aquí."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cuentas, key = { it.usuario }) { cuenta ->
                        AppCard(
                            onClick = { iniciarSesionCon(cuenta) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            corner = 14.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(cursoColor(cuenta.nombre ?: cuenta.usuario)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = cuenta.nombre?.trim()?.firstOrNull()?.uppercase()
                                            ?: cuenta.usuario.firstOrNull()?.uppercase()
                                            ?: "?",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cuenta.nombre?.takeIf { it.isNotBlank() } ?: "Usuario UPAO",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = cuenta.usuario,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (autenticando == cuenta.usuario) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(12.dp),
                                                strokeWidth = 1.8.dp
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Iniciando sesión...",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { eliminarCuenta = cuenta },
                                    enabled = autenticando == null
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Eliminar cuenta guardada",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            PrimaryButton(
                text = "Agregar otra cuenta",
                onClick = onGoToLogin,
                icon = Icons.Filled.PersonAdd,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                height = 44.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    eliminarCuenta?.let { cuenta ->
        ReusableDialog(
            title = "¿Eliminar esta cuenta guardada?",
            text = "Se quitará la cuenta ${cuenta.usuario} de las cuentas guardadas de este dispositivo.",
            confirmLabel = "Eliminar",
            dismissLabel = "Cancelar",
            onConfirm = {
                tokenManager.removeCuenta(cuenta.usuario)
                cuentas = tokenManager.getCuentas()
                eliminarCuenta = null
            },
            onDismiss = { eliminarCuenta = null }
        )
    }
}

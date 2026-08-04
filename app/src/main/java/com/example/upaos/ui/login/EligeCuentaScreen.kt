package com.example.upaos.ui.login

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.upaos.data.api.RetrofitClient
import com.example.upaos.data.local.CuentaGuardada
import com.example.upaos.data.local.TokenManager
import com.example.upaos.data.model.LoginRequest
import com.example.upaos.service.FcmTokenHelper
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Elige una cuenta", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "Cuentas guardadas en este dispositivo",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (cuentas.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No hay cuentas guardadas",
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Guarda tu contraseña al iniciar sesión para verla aquí.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(cuentas, key = { it.usuario }) { cuenta ->
                        Card(
                            onClick = { iniciarSesionCon(cuenta) },
                            enabled = autenticando == null,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cuenta.usuario,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (!cuenta.nombre.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = cuenta.nombre,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (autenticando == cuenta.usuario) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Iniciando sesión...",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                IconButton(onClick = { eliminarCuenta = cuenta }) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Eliminar cuenta guardada",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onGoToLogin,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Agregar otra cuenta", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    eliminarCuenta?.let { cuenta ->
        AlertDialog(
            onDismissRequest = { eliminarCuenta = null },
            title = { Text("¿Eliminar esta cuenta guardada?") },
            text = { Text("Se quitará la cuenta ${cuenta.usuario} de las cuentas guardadas de este dispositivo.") },
            confirmButton = {
                TextButton(onClick = {
                    tokenManager.removeCuenta(cuenta.usuario)
                    cuentas = tokenManager.getCuentas()
                    eliminarCuenta = null
                }) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { eliminarCuenta = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

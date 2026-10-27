package com.example.upaos.ui.login

import android.widget.Toast
import androidx.activity.compose.BackHandler
import com.example.upaos.data.profile.StudentProfile
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
import com.example.upaos.data.local.CuentaGuardada
import com.example.upaos.data.local.TokenManager
import com.example.upaos.data.upao.UpaoAuth
import com.example.upaos.data.upao.UpaoInvalidCredentialsException
import com.example.upaos.data.upao.UpaoNetworkException
import com.example.upaos.data.upao.UpaoServiceUnavailableException
import com.example.upaos.ui.components.AppCard
import com.example.upaos.ui.components.EmptyState
import com.example.upaos.ui.components.PrimaryButton
import com.example.upaos.ui.components.ReusableDialog
import com.example.upaos.ui.components.cursoColor
import kotlinx.coroutines.CancellationException
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
    val initialNombres = remember(cuentas) {
        cuentas.associate { cuenta ->
            val cachedName = try { tokenManager.getProfileJson(cuenta.usuario)?.let { StudentProfile.parse(it).shortName } }
            catch (_: Exception) { null }
            cuenta.usuario to (cachedName ?: cuenta.nombreCorto ?: StudentProfile.shortNameFromFullName(cuenta.nombre))
        }
    }
    var nombres by remember { mutableStateOf(initialNombres) }
    LaunchedEffect(cuentas) {
        nombres = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            cuentas.associate { cuenta ->
                val cachedName = try { tokenManager.getProfileJson(cuenta.usuario)?.let { StudentProfile.parse(it).shortName } }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { null }
                cuenta.usuario to (cachedName ?: cuenta.nombreCorto ?: StudentProfile.shortNameFromFullName(cuenta.nombre))
            }
        }
    }
    BackHandler { if (autenticando == null) onBack() }

    fun iniciarSesionCon(cuenta: CuentaGuardada) {
        if (autenticando != null) return
        autenticando = cuenta.usuario
        scope.launch {
            try {
                val result = UpaoAuth.login(cuenta.usuario, cuenta.password)
                if (result.success) {
                    val nombreFinal = result.studentName ?: cuenta.nombre
                    tokenManager.saveLogin(cuenta.usuario, cuenta.password, true, nombreFinal)
                    autenticando = null
                    onLoginSuccess(cuenta.usuario)
                } else {
                    autenticando = null
                    Toast.makeText(context, result.errorMessage ?: "No se pudo iniciar sesión", Toast.LENGTH_LONG).show()
                }
            } catch (e: UpaoInvalidCredentialsException) {
                autenticando = null
                Toast.makeText(context, "ID o contraseña incorrectos. Revisa tus datos.", Toast.LENGTH_LONG).show()
            } catch (e: UpaoServiceUnavailableException) {
                autenticando = null
                Toast.makeText(context, "El portal de la UPAO no está disponible, intenta más tarde.", Toast.LENGTH_LONG).show()
            } catch (e: UpaoNetworkException) {
                autenticando = null
                Toast.makeText(context, "No se pudo conectar. Revisa tu conexión a internet.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                autenticando = null
                Toast.makeText(context, e.localizedMessage ?: "No se pudo iniciar sesión.", Toast.LENGTH_LONG).show()
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
                IconButton(onClick = onBack, enabled = autenticando == null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Elige una cuenta",
                    style = MaterialTheme.typography.titleLarge,
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
                        val nombreMostrar = nombres[cuenta.usuario] ?: cuenta.nombreCorto
                            ?: StudentProfile.shortNameFromFullName(cuenta.nombre) ?: "Cuenta guardada"
                        AppCard(
                            onClick = { iniciarSesionCon(cuenta) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            corner = 20.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = nombreMostrar.firstOrNull()?.uppercase() ?: "?",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = nombreMostrar,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2
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

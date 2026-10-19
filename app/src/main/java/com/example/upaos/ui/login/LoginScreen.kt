package com.example.upaos.ui.login

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.upaos.data.local.TokenManager
import com.example.upaos.data.upao.UpaoAuth
import com.example.upaos.data.upao.UpaoInvalidCredentialsException
import com.example.upaos.data.upao.UpaoNetworkException
import com.example.upaos.data.upao.UpaoServiceUnavailableException
import com.example.upaos.ui.components.AppCard
import com.example.upaos.ui.components.ModernTextField
import com.example.upaos.ui.components.PrimaryButton
import com.example.upaos.ui.components.UpaoLogo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }
    val scope = rememberCoroutineScope()

    var usuario by remember { mutableStateOf(tokenManager.getSavedUser() ?: "") }
    var password by remember { mutableStateOf(tokenManager.getSavedPass() ?: "") }
    var passwordVisible by remember { mutableStateOf(false) }

    var guardarPassword by remember { mutableStateOf(tokenManager.getSavedPass() != null) }
    var mantenerSesion by remember { mutableStateOf(true) }

    var isLoading by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }

    val isUserValid = usuario.length == 9 && usuario.all { it.isDigit() }

    // Micro-animación sutil de entrada para el logo
    val infiniteTransition = rememberInfiniteTransition(label = "logoPulse")
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoScale"
    )

    fun procesarLogin(studentName: String?) {
        tokenManager.saveLogin(usuario, password.takeIf { guardarPassword }, mantenerSesion, studentName)
        onLoginSuccess(usuario)
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
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 16 },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    shadowElevation = 8.dp,
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.scale(logoScale)
                ) {
                    UpaoLogo(size = 80.dp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Campus Virtual UPAO",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Ingresa tu ID de 9 dígitos y contraseña de Banner",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                AppCard(
                    contentPadding = PaddingValues(16.dp),
                    corner = 18.dp
                ) {
                    ModernTextField(
                        value = usuario,
                        onValueChange = { if (!isLoading && it.length <= 9 && it.all { char -> char.isDigit() }) { usuario = it; loginError = null } },
                        label = "ID de usuario",
                        leadingIcon = Icons.Filled.Person,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = usuario.isNotEmpty() && !isUserValid,
                        supportingText = if (usuario.isNotEmpty() && !isUserValid) "Debe tener exactamente 9 dígitos" else null,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    ModernTextField(
                        value = password,
                        onValueChange = { if (!isLoading) { password = it; loginError = null } },
                        label = "Contraseña",
                        leadingIcon = Icons.Filled.Lock,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = "Mostrar u ocultar contraseña",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = guardarPassword,
                            onCheckedChange = { guardarPassword = it }
                        )
                        Text(
                            text = "Guardar contraseña cifrada",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = mantenerSesion,
                            onCheckedChange = { mantenerSesion = it }
                        )
                        Text(
                            text = "Mantener sesión iniciada",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    PrimaryButton(
                        text = "Iniciar Sesión",
                        onClick = {
                            if (!isUserValid || password.isEmpty()) {
                                Toast.makeText(context, "Complete usuario y contraseña válidos", Toast.LENGTH_SHORT).show()
                                return@PrimaryButton
                            }

                            if (isLoading) return@PrimaryButton
                            loginError = null
                            isLoading = true
                            scope.launch {
                                try {
                                    val result = UpaoAuth.login(usuario, password)
                                    isLoading = false
                                    if (result.success) {
                                        procesarLogin(result.studentName)
                                    } else {
                                        loginError = result.errorMessage ?: "No se pudo iniciar sesión."
                                    }
                                } catch (e: UpaoInvalidCredentialsException) {
                                    isLoading = false
                                    loginError = e.message ?: "ID o contraseña incorrectos. Revisa tus datos."
                                } catch (e: UpaoServiceUnavailableException) {
                                    isLoading = false
                                    loginError = e.message ?: "El portal de la UPAO no está disponible, intenta más tarde."
                                } catch (e: UpaoNetworkException) {
                                    isLoading = false
                                    loginError = "No se pudo conectar. Revisa tu conexión a internet e inténtalo nuevamente."
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    isLoading = false
                                    loginError = e.localizedMessage ?: "No se pudo iniciar sesión. Inténtalo nuevamente."
                                }
                            }
                        },
                        enabled = isUserValid && password.isNotEmpty(),
                        loading = isLoading,
                        icon = Icons.AutoMirrored.Filled.Login,
                        height = 46.dp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    loginError?.let { mensaje ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = mensaje,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "App independiente · Campus Virtual UPAO",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

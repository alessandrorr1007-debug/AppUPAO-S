package com.example.upaos.ui.login

import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.upaos.data.api.RetrofitClient
import com.example.upaos.data.local.TokenManager
import com.example.upaos.data.model.LoginRequest
import com.example.upaos.data.model.ManualCaptchaRequest
import com.example.upaos.service.FcmTokenHelper
import com.example.upaos.ui.components.AppCard
import com.example.upaos.ui.components.ModernTextField
import com.example.upaos.ui.components.PrimaryButton
import com.example.upaos.ui.components.UpaoLogo
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
    var showCaptchaDialog by remember { mutableStateOf(false) }
    var captchaBase64 by remember { mutableStateOf("") }
    var manualCaptchaCode by remember { mutableStateOf("") }

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

    fun procesarLogin(token: String?) {
        if (token != null) {
            tokenManager.saveToken(token)
            tokenManager.setKeepLoggedIn(true)
            tokenManager.saveUserId(usuario)
            tokenManager.saveCredentials(usuario, password)
            tokenManager.saveCuenta(usuario, password)

            FcmTokenHelper.register(context)
            onLoginSuccess(token)
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
                        onValueChange = { if (it.length <= 9 && it.all { char -> char.isDigit() }) usuario = it },
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
                        onValueChange = { password = it },
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

                            isLoading = true
                            scope.launch {
                                try {
                                    val response = RetrofitClient.apiService.login(LoginRequest(usuario, password))
                                    isLoading = false
                                    val body = response.body()
                                    if (response.isSuccessful && body != null) {
                                        if (body.success && body.token != null) {
                                            procesarLogin(body.token)
                                        } else if (body.necesitaCaptcha && body.imagenBase64 != null) {
                                            captchaBase64 = body.imagenBase64
                                            showCaptchaDialog = true
                                        } else {
                                            Toast.makeText(context, body.message ?: "Error en login", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Error del servidor: ${response.code()}", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    isLoading = false
                                    Toast.makeText(context, "Error de conexión: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = isUserValid && password.isNotEmpty(),
                        loading = isLoading,
                        icon = Icons.AutoMirrored.Filled.Login,
                        height = 46.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
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

    // Diálogo Fallback de Captcha Manual
    if (showCaptchaDialog) {
        AlertDialog(
            onDismissRequest = { showCaptchaDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Código de Verificación",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "El OCR no pudo reconocer el captcha automáticamente. Ingréselo manualmente:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (captchaBase64.isNotEmpty()) {
                        val cleanB64 = if (captchaBase64.contains(",")) captchaBase64.substringAfter(",") else captchaBase64
                        var isDecodeError by remember { mutableStateOf(false) }
                        var errorMessage by remember { mutableStateOf("") }

                        val bitmap = remember(cleanB64) {
                            try {
                                val imageBytes = Base64.decode(cleanB64, Base64.DEFAULT)
                                val bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                if (bmp == null) {
                                    isDecodeError = true
                                    errorMessage = "BitmapFactory devolvió null para ${imageBytes.size} bytes."
                                }
                                bmp
                            } catch (e: Exception) {
                                isDecodeError = true
                                errorMessage = "Error Base64: ${e.localizedMessage}"
                                null
                            }
                        }

                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Captcha Image",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(4.dp)
                            )
                        } else if (isDecodeError) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp
                            )
                        }
                    } else {
                        Text(
                            text = "Error: La imagen base64 vino vacía del servidor.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    ModernTextField(
                        value = manualCaptchaCode,
                        onValueChange = { if (it.length <= 6) manualCaptchaCode = it.uppercase() },
                        label = "Código (6 caracteres)",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCaptchaDialog = false
                        isLoading = true
                        scope.launch {
                            try {
                                val res = RetrofitClient.apiService.loginConfirmarCaptcha(
                                    ManualCaptchaRequest(usuario, password, manualCaptchaCode)
                                )
                                isLoading = false
                                val body = res.body()
                                if (res.isSuccessful && body?.success == true && body.token != null) {
                                    procesarLogin(body.token)
                                } else {
                                    Toast.makeText(context, body?.message ?: "Captcha manual incorrecto", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                isLoading = false
                                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = manualCaptchaCode.length == 6
                ) {
                    Text("Confirmar", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCaptchaDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

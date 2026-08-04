package com.example.upaos.ui.login

import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.upaos.ui.components.UpaoLogo
import com.example.upaos.ui.theme.UpaoOrange
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
    var mantenerSesion by remember { mutableStateOf(tokenManager.shouldKeepLoggedIn()) }
    
    var isLoading by remember { mutableStateOf(false) }
    var showCaptchaDialog by remember { mutableStateOf(false) }
    var captchaBase64 by remember { mutableStateOf("") }
    var manualCaptchaCode by remember { mutableStateOf("") }

    val isUserValid = usuario.length == 9 && usuario.all { it.isDigit() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        UpaoLogo(size = 88.dp)

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Campus Virtual UPAO",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Ingresa con tu ID y contraseña de Banner",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = usuario,
            onValueChange = { if (it.length <= 9 && it.all { char -> char.isDigit() }) usuario = it },
            label = { Text("ID de Usuario (9 dígitos)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = usuario.isNotEmpty() && !isUserValid,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        if (usuario.isNotEmpty() && !isUserValid) {
            Text(
                text = "El usuario debe tener exactamente 9 dígitos",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Start)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = "Mostrar u ocultar contraseña"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = guardarPassword,
                onCheckedChange = { guardarPassword = it }
            )
            Text(text = "Guardar contraseña cifrada")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = mantenerSesion,
                onCheckedChange = { mantenerSesion = it }
            )
            Text(text = "Mantener sesión iniciada")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (!isUserValid || password.isEmpty()) {
                    Toast.makeText(context, "Complete usuario y contraseña válidos", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                isLoading = true
                scope.launch {
                    try {
                        val response = RetrofitClient.apiService.login(LoginRequest(usuario, password))
                        isLoading = false
                        val body = response.body()
                        if (response.isSuccessful && body != null) {
                            if (body.success && body.token != null) {
                                tokenManager.saveToken(body.token)
                                tokenManager.setKeepLoggedIn(mantenerSesion)
                                tokenManager.saveUserId(usuario)
                                if (guardarPassword) {
                                    tokenManager.saveCredentials(usuario, password)
                                    tokenManager.saveCuenta(usuario, password)
                                } else {
                                    tokenManager.removeCuenta(usuario)
                                }
                                FcmTokenHelper.register(context)
                                onLoginSuccess(body.token)
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
            enabled = !isLoading && isUserValid && password.isNotEmpty(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = UpaoOrange,
                contentColor = androidx.compose.ui.graphics.Color(0xFF141414),
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = androidx.compose.ui.graphics.Color(0xFF141414),
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp
                )
            } else {
                Text("Iniciar Sesión", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    // Diálogo Fallback de Captcha Manual
    if (showCaptchaDialog) {
        AlertDialog(
            onDismissRequest = { showCaptchaDialog = false },
            title = { Text("Código de Verificación") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("El OCR no pudo reconocer el captcha automáticamente. Ingréselo manualmente:")
                    Spacer(modifier = Modifier.height(12.dp))
                    
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
                                    .height(90.dp)
                            )
                        } else if (isDecodeError) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        Text(
                            text = "Error: La imagen base64 vino vacía del servidor.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = manualCaptchaCode,
                        onValueChange = { if (it.length <= 6) manualCaptchaCode = it.uppercase() },
                        label = { Text("Código (6 caracteres)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
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
                                    tokenManager.saveToken(body.token)
                                    tokenManager.setKeepLoggedIn(mantenerSesion)
                                    tokenManager.saveUserId(usuario)
                                    if (guardarPassword) {
                                        tokenManager.saveCredentials(usuario, password)
                                        tokenManager.saveCuenta(usuario, password)
                                    } else {
                                        tokenManager.removeCuenta(usuario)
                                    }
                                    FcmTokenHelper.register(context)
                                    onLoginSuccess(body.token)
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
                    Text("Confirmar")
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

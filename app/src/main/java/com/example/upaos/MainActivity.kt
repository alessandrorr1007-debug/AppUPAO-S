package com.example.upaos

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.upaos.data.local.ThemePreferences
import com.example.upaos.data.local.TokenManager
import com.example.upaos.service.FcmTokenHelper
import com.example.upaos.ui.calculadora.CalculadoraScreen
import com.example.upaos.ui.components.UpaoLogo
import com.example.upaos.ui.home.HomeScreen
import com.example.upaos.ui.login.EligeCuentaScreen
import com.example.upaos.ui.login.LoginScreen
import com.example.upaos.ui.notificaciones.NotificacionesScreen
import com.example.upaos.ui.ranking.RankingScreen
import com.example.upaos.ui.settings.SettingsScreen
import com.example.upaos.ui.sugerencias.SugerenciasScreen
import com.example.upaos.ui.theme.UPAOSTheme
import com.example.upaos.ui.theme.UpaoBlueDark
import com.example.upaos.ui.theme.UpaoOrangeBright
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13+: pedir permiso de notificaciones para mostrar los avisos de notas.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val context = this
            val tokenManager = remember { TokenManager(context) }
            val themePreferences = remember { ThemePreferences(context) }
            val darkTheme by themePreferences.isDarkTheme.collectAsState(initial = true)
            val scope = rememberCoroutineScope()

            // Registrar el token FCM del dispositivo con el usuario (si hay sesión guardada).
            LaunchedEffect(Unit) {
                FcmTokenHelper.register(context)
            }

            UPAOSTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val savedToken = tokenManager.getToken()
                    val keepLoggedIn = tokenManager.shouldKeepLoggedIn()

                    val targetDestination = if (keepLoggedIn && savedToken != null) {
                        "home/$savedToken"
                    } else {
                        "login"
                    }

                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "splash"
                    ) {
                        composable("splash") {
                            SplashScreen(
                                onTimeout = {
                                    navController.navigate(targetDestination) {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("login") {
                            LoginScreen(
                                onLoginSuccess = { token ->
                                    navController.navigate("home/$token") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("elige-cuenta") {
                            EligeCuentaScreen(
                                onLoginSuccess = { token ->
                                    navController.navigate("home/$token") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                },
                                onGoToLogin = {
                                    navController.navigate("login") {
                                        popUpTo("elige-cuenta") { inclusive = true }
                                    }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = "home/{token}",
                            arguments = listOf(navArgument("token") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val token = backStackEntry.arguments?.getString("token") ?: ""
                            HomeScreen(
                                token = token,
                                isDarkTheme = darkTheme,
                                onToggleTheme = {
                                    scope.launch { themePreferences.setDarkTheme(!darkTheme) }
                                },
                                onLogout = {
                                    navController.navigate("elige-cuenta") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                },
                                onOpenCalculator = {
                                    navController.navigate("calculadora")
                                },
                                onOpenSettings = {
                                    navController.navigate("settings")
                                },
                                onOpenNotifications = {
                                    navController.navigate("notificaciones")
                                }
                            )
                        }

                        composable("notificaciones") {
                            NotificacionesScreen(
                                usuario = tokenManager.getSavedUser(),
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                usuario = tokenManager.getSavedUser(),
                                onBack = { navController.popBackStack() },
                                onOpenSugerencias = { navController.navigate("sugerencias") },
                                onOpenRanking = { navController.navigate("ranking") }
                            )
                        }

                        composable("sugerencias") {
                            SugerenciasScreen(
                                usuario = tokenManager.getSavedUser(),
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("ranking") {
                            RankingScreen(
                                usuario = tokenManager.getSavedUser(),
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("calculadora") {
                            CalculadoraScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1200)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(UpaoBlueDark, Color(0xFF071D4D)))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            UpaoLogo(size = 104.dp)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "UPAO MÓVIL",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Notas y Campus Virtual",
                color = UpaoOrangeBright,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

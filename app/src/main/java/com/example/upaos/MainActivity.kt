package com.example.upaos

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.upaos.data.local.ThemePreferences
import com.example.upaos.data.local.TokenManager
import com.example.upaos.service.FcmTokenHelper
import com.example.upaos.ui.admin.AdminScreen
import com.example.upaos.ui.calculadora.CalculadoraScreen
import com.example.upaos.ui.home.HomeScreen
import com.example.upaos.ui.login.EligeCuentaScreen
import com.example.upaos.ui.login.LoginScreen
import com.example.upaos.ui.notificaciones.NotificacionesScreen
import com.example.upaos.ui.ranking.RankingScreen
import com.example.upaos.ui.settings.SettingsScreen
import com.example.upaos.ui.sugerencias.SugerenciasScreen
import com.example.upaos.ui.theme.UPAOSTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                FcmTokenHelper.register(this)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                                onOpenRanking = { navController.navigate("ranking") },
                                onOpenAdmin = { navController.navigate("admin") }
                            )
                        }

                        composable("admin") {
                            AdminScreen(
                                usuario = tokenManager.getSavedUser(),
                                onBack = { navController.popBackStack() }
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
    var startAnim by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0.85f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "splashScale"
    )

    LaunchedEffect(Unit) {
        startAnim = true
        delay(1200)
        onTimeout()
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
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.scale(scale)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(100.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                        contentDescription = "Logo UPAO",
                        modifier = Modifier.size(80.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "UPAO Móvil",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Notas, Horario y Asistencia",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

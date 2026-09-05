package com.example.upaos

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
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
import com.example.upaos.data.api.RetrofitClient
import com.example.upaos.data.local.ThemePreferences
import com.example.upaos.data.local.TokenManager
import com.example.upaos.service.FcmTokenHelper
import com.example.upaos.ui.calculadora.CalculadoraScreen
import com.example.upaos.ui.grades.CourseDetailScreen
import com.example.upaos.ui.home.HomeScreen
import com.example.upaos.ui.login.EligeCuentaScreen
import com.example.upaos.ui.login.LoginScreen
import com.example.upaos.ui.notificaciones.NotificacionesScreen
import com.example.upaos.ui.settings.SettingsScreen
import com.example.upaos.ui.sugerencias.SugerenciasScreen
import com.example.upaos.ui.theme.UPAOSTheme
import com.example.upaos.widget.ProximoCursoWidget
import com.example.upaos.widget.ResumenNotasWidget
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
        com.example.upaos.data.api.RetrofitClient.init(this)

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

            var updateInfo by remember { mutableStateOf<com.example.upaos.util.UpdateInfo?>(null) }
            var isDownloadingUpdate by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                FcmTokenHelper.register(context)
                val notifPrefs = com.example.upaos.data.local.NotificationPreferences(context)
                if (notifPrefs.checkAsistenciaEnabled) {
                    com.example.upaos.service.AsistenciaWorker.schedule(context, notifPrefs.intervaloMinutos.toLong())
                }

                // Comprobar si hay una nueva versión en GitHub
                launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val info = com.example.upaos.util.AppUpdater.checkForUpdates(context)
                        if (info.hasUpdate) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                updateInfo = info
                            }
                        }
                    } catch (_: Exception) {}
                }

                // Actualiza los widgets de la home screen tras cargar la app
                delay(1500)
                try {
                    ResumenNotasWidget.updateAll(context)
                    ProximoCursoWidget.updateAll(context)
                } catch (e: Exception) {
                    // Silencioso: los widgets no están instalados necesariamente
                }
            }

            UPAOSTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val savedToken = tokenManager.getToken()
                    val savedUser = tokenManager.getSavedUser()
                    val navController = rememberNavController()

                    fun navegarPorTipoCuenta(token: String) {
                        val activeToken = token.ifBlank { tokenManager.getToken() ?: "" }
                        navController.navigate("home/$activeToken") {
                            popUpTo(0) { inclusive = true }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = "splash"
                    ) {
                        composable("splash") {
                            SplashScreen(
                                onTimeout = {
                                    val currentToken = tokenManager.getToken()
                                    val currentUser = tokenManager.getSavedUser()
                                    if (!currentUser.isNullOrBlank() || !currentToken.isNullOrBlank()) {
                                        navegarPorTipoCuenta(currentToken ?: "")
                                    } else {
                                        navController.navigate("login") {
                                            popUpTo("splash") { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }

                        composable(
                            "login",
                            enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 3 } },
                            exitTransition = { fadeOut(tween(200)) },
                            popEnterTransition = { fadeIn(tween(300)) },
                            popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(300)) { it / 4 } }
                        ) {
                            LoginScreen(
                                onLoginSuccess = { token ->
                                    navegarPorTipoCuenta(token)
                                }
                            )
                        }

                        composable(
                            "elige-cuenta",
                            enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 3 } },
                            exitTransition = { fadeOut(tween(200)) }
                        ) {
                            EligeCuentaScreen(
                                onLoginSuccess = { token ->
                                    navegarPorTipoCuenta(token)
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
                            arguments = listOf(navArgument("token") { type = NavType.StringType }),
                            enterTransition = { fadeIn(tween(350)) },
                            exitTransition = { fadeOut(tween(200)) }
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
                                onSwitchAccount = {
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
                                },
                                onOpenCourse = { periodo, carrera, crn, nombre ->
                                    navController.navigate(
                                        "course-detail/${Uri.encode(periodo)}/${Uri.encode(carrera)}/${Uri.encode(crn)}?nombre=${Uri.encode(nombre)}"
                                    )
                                }
                            )
                        }

                        composable(
                            route = "course-detail/{periodo}/{carrera}/{crn}?nombre={nombre}",
                            arguments = listOf(
                                navArgument("periodo") { type = NavType.StringType },
                                navArgument("carrera") { type = NavType.StringType },
                                navArgument("crn") { type = NavType.StringType },
                                navArgument("nombre") { type = NavType.StringType; defaultValue = "" }
                            ),
                            enterTransition = { slideInHorizontally(tween(280)) { it } },
                            exitTransition = { fadeOut(tween(180)) },
                            popEnterTransition = { fadeIn(tween(220)) },
                            popExitTransition = { slideOutHorizontally(tween(280)) { it } }
                        ) { backStackEntry ->
                            val args = backStackEntry.arguments
                            val periodo = args?.getString("periodo") ?: "202610"
                            val carrera = args?.getString("carrera") ?: "UG"
                            val crn = args?.getString("crn") ?: ""
                            val nombre = args?.getString("nombre") ?: ""
                            CourseDetailScreen(
                                token = savedToken ?: "",
                                periodo = periodo,
                                carrera = carrera,
                                crn = crn,
                                courseName = nombre,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            "notificaciones",
                            enterTransition = { slideInHorizontally(tween(280)) { it } },
                            exitTransition = { fadeOut(tween(180)) },
                            popEnterTransition = { fadeIn(tween(220)) },
                            popExitTransition = { slideOutHorizontally(tween(280)) { it } }
                        ) {
                            NotificacionesScreen(
                                usuario = tokenManager.getSavedUser(),
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            "settings",
                            enterTransition = { slideInHorizontally(tween(280)) { it } },
                            exitTransition = { fadeOut(tween(180)) },
                            popEnterTransition = { fadeIn(tween(220)) },
                            popExitTransition = { slideOutHorizontally(tween(280)) { it } }
                        ) {
                            SettingsScreen(
                                usuario = tokenManager.getSavedUser(),
                                onBack = { navController.popBackStack() },
                                onOpenSugerencias = { navController.navigate("sugerencias") }
                            )
                        }

                        composable(
                            "sugerencias",
                            enterTransition = { slideInHorizontally(tween(280)) { it } },
                            exitTransition = { fadeOut(tween(180)) },
                            popEnterTransition = { fadeIn(tween(220)) },
                            popExitTransition = { slideOutHorizontally(tween(280)) { it } }
                        ) {
                            SugerenciasScreen(
                                usuario = tokenManager.getSavedUser(),
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            "calculadora",
                            enterTransition = { slideInHorizontally(tween(280)) { it } },
                            exitTransition = { fadeOut(tween(180)) },
                            popEnterTransition = { fadeIn(tween(220)) },
                            popExitTransition = { slideOutHorizontally(tween(280)) { it } }
                        ) {
                            CalculadoraScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }

                    // Diálogo de actualización disponible
                    updateInfo?.let { info ->
                        com.example.upaos.ui.components.UpdateDialog(
                            updateInfo = info,
                            isDownloading = isDownloadingUpdate,
                            onDismiss = { updateInfo = null },
                            onConfirmUpdate = {
                                if (info.downloadUrl != null) {
                                    isDownloadingUpdate = true
                                    com.example.upaos.util.AppUpdater.downloadAndInstall(
                                        context = context,
                                        downloadUrl = info.downloadUrl,
                                        versionName = info.latestVersion
                                    )
                                    android.widget.Toast.makeText(
                                        context,
                                        "Descargando actualización en segundo plano...",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                    updateInfo = null
                                    isDownloadingUpdate = false
                                } else {
                                    val browserIntent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(info.releasePageUrl)
                                    )
                                    context.startActivity(browserIntent)
                                    updateInfo = null
                                }
                            }
                        )
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
            Spacer(modifier = Modifier.height(28.dp))
            LinearProgressIndicator(
                modifier = Modifier
                    .width(140.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

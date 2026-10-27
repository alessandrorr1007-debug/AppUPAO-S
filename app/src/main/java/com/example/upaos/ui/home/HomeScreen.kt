package com.example.upaos.ui.home
import com.example.upaos.ui.components.CampusTopBar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.upaos.data.local.TokenManager
import com.example.upaos.ui.asistencia.AsistenciaContent
import com.example.upaos.ui.canvas.CanvasContent
import com.example.upaos.ui.grades.GradesContent
import com.example.upaos.ui.horario.HorarioContent
import androidx.compose.material.icons.automirrored.filled.Assignment
import kotlinx.coroutines.launch

@Composable
private fun CampusNavigationIcon(selected: Boolean, active: ImageVector, inactive: ImageVector) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(dampingRatio = .7f, stiffness = 500f),
        label = "navigationSelection"
    )
    Icon(
        imageVector = if (selected) active else inactive,
        contentDescription = null,
        modifier = Modifier.size(22.dp).graphicsLayer { scaleX = scale; scaleY = scale }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    token: String,
    initialTab: String? = null,
    onTabHandled: () -> Unit = {},
    openAgenda: Boolean = false,
    onAgendaOpened: () -> Unit = {},
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit,
    onSwitchAccount: () -> Unit = onLogout,
    onOpenCalculator: () -> Unit,
    onOpenSettings: () -> Unit,
    profileName: String? = null,
    profileFirstName: String? = null,
    onOpenProfile: () -> Unit = {},
    onOpenNotifications: () -> Unit,
    onOpenCourse: (String, String, String, String) -> Unit = { _, _, _, _ -> }
) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }
    val coroutineScope = rememberCoroutineScope()
    val initialPageIndex = remember {
        when (initialTab) {
            "notas" -> 1
            "horario" -> 2
            "asistencia" -> 3
            else -> 0
        }
    }
    val pagerState = rememberPagerState(initialPage = initialPageIndex, pageCount = { 5 })
    LaunchedEffect(openAgenda) { if (openAgenda) pagerState.scrollToPage(0) }
    LaunchedEffect(initialTab) {
        if (initialTab != null) {
            when (initialTab) {
                "notas" -> pagerState.scrollToPage(1)
                "horario" -> pagerState.scrollToPage(2)
                "asistencia" -> pagerState.scrollToPage(3)
            }
            onTabHandled()
        }
    }

    val usuario = tokenManager.getSavedUser()
    val savedAccount = remember(usuario) { tokenManager.getCuentas().firstOrNull { it.usuario == usuario } }
    val cachedProfile = remember(usuario) {
        usuario?.let { u ->
            try {
                tokenManager.getProfileJson(u)?.let { com.example.upaos.data.profile.StudentProfile.parse(it) }
            } catch (_: Exception) { null }
        }
    }
    val greetingName = profileFirstName
        ?: cachedProfile?.firstName
        ?: com.example.upaos.data.profile.StudentProfile.givenName(
            profileName ?: cachedProfile?.fullName ?: savedAccount?.nombreCorto ?: savedAccount?.nombre
        )

    fun cerrarSesion() {
        com.example.upaos.data.local.AccountPrivacy.logout(context)
        onLogout()
    }

    val dashboard: DashboardViewModel = viewModel(
        key = "dashboard_${usuario}_${token.hashCode()}",
        factory = DashboardViewModel.factory(context.applicationContext as android.app.Application, token, usuario ?: "anonimo")
    )
    val shared by dashboard.state.collectAsState()
    val unreadCount = shared.notices?.noLeidas ?: 0
    val semanaEtiqueta = shared.week?.takeIf { it.configurada && it.fueraDeCiclo != true }?.let { it.etiqueta + if (it.fechaEstimada) " · estimada" else "" }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(dashboard, lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            dashboard.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        }
        lifecycle.addObserver(observer)
        dashboard.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose { lifecycle.removeObserver(observer); dashboard.setForeground(false) }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var showAboutDialog by remember { mutableStateOf(false) }
    val appVersion = remember { com.example.upaos.util.AppUpdater.getCurrentVersionName(context) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    profileName?.takeIf { it.isNotBlank() }?.let { name ->
                        Text(name, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = usuario ?: "Estudiante UPAO",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "UPAO S",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))



                NavigationDrawerItem(
                    label = { Text("Mi perfil", fontWeight = FontWeight.Medium) },
                    icon = { Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(22.dp)) },
                    selected = false,
                    onClick = { coroutineScope.launch { drawerState.close(); onOpenProfile() } },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                NavigationDrawerItem(
                    label = { Text("Cambiar de cuenta", fontWeight = FontWeight.Medium) },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        onSwitchAccount()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                NavigationDrawerItem(
                    label = { Text("Acerca de", fontWeight = FontWeight.Medium) },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        showAboutDialog = true
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                NavigationDrawerItem(
                    label = {
                        Text(
                            "Cerrar sesión",
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        cerrarSesion()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "v.$appVersion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 24.dp, bottom = 20.dp)
                )
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CampusTopBar(
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = "Menú principal",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    title = {
                        Text(
                            text = when (pagerState.currentPage) {
                                0 -> "Hoy"
                                1 -> "Mis Cursos"
                                2 -> "Horario"
                                3 -> "Asistencia"
                                else -> "Canvas LMS"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    actions = {
                        IconButton(onClick = onToggleTheme) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = if (isDarkTheme) "Modo claro" else "Modo oscuro",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = onOpenCalculator) {
                            Icon(
                                imageVector = Icons.Filled.Calculate,
                                contentDescription = "Calculadora",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        BadgedBox(
                            badge = {
                                if (unreadCount > 0) {
                                    Badge {
                                        Text(if (unreadCount > 9) "9+" else "$unreadCount")
                                    }
                                }
                            }
                        ) {
                            IconButton(onClick = onOpenNotifications) {
                                Icon(
                                    imageVector = Icons.Filled.Notifications,
                                    contentDescription = "Notificaciones",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Ajustes",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                )
            },
            bottomBar = {
                val navBarColors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)),
                    shadowElevation = 6.dp
                ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0, 0, 0, 0)
                ) {
                    NavigationBarItem(
                        selected = pagerState.currentPage == 0,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                        icon = { CampusNavigationIcon(pagerState.currentPage == 0, Icons.Filled.EventAvailable, Icons.Outlined.EventAvailable) },
                        label = { Text("Hoy", fontSize = 10.sp, maxLines = 1, softWrap = false) },
                        colors = navBarColors
                    )
                    NavigationBarItem(
                        selected = pagerState.currentPage == 1,
                        onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(1) }
                        },
                        icon = { CampusNavigationIcon(pagerState.currentPage == 1, Icons.Filled.School, Icons.Outlined.School) },
                        label = { Text("Cursos", fontSize = 10.sp, maxLines = 1, softWrap = false) },
                        colors = navBarColors
                    )
                    NavigationBarItem(
                        selected = pagerState.currentPage == 2,
                        onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(2) }
                        },
                        icon = { CampusNavigationIcon(pagerState.currentPage == 2, Icons.Filled.Schedule, Icons.Outlined.Schedule) },
                        label = { Text("Horario", fontSize = 10.sp, maxLines = 1, softWrap = false) },
                        colors = navBarColors
                    )
                    NavigationBarItem(
                        selected = pagerState.currentPage == 3,
                        onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(3) }
                        },
                        icon = { CampusNavigationIcon(pagerState.currentPage == 3, Icons.Filled.FactCheck, Icons.Outlined.FactCheck) },
                        label = { Text("Asistencia", fontSize = 10.sp, maxLines = 1, softWrap = false) },
                        colors = navBarColors
                    )
                    NavigationBarItem(
                        selected = pagerState.currentPage == 4,
                        onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(4) }
                        },
                        icon = { CampusNavigationIcon(pagerState.currentPage == 4, Icons.AutoMirrored.Filled.Assignment, Icons.Outlined.Assignment) },
                        label = { Text("Canvas", fontSize = 10.sp, maxLines = 1, softWrap = false) },
                        colors = navBarColors
                    )
                }
                }
            }
        ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (semanaEtiqueta != null && pagerState.currentPage == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF102C67), Color(0xFF245BE0), Color(0xFF197DAD))
                            )
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.EventNote,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = semanaEtiqueta!!,
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 2,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) { page ->
                when (page) {
                    0 -> TodayContent(token, dashboard, usuario,
                        openAgenda = openAgenda, onAgendaOpened = onAgendaOpened,
                        onSchedule = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                        onCanvas = { coroutineScope.launch { pagerState.animateScrollToPage(4) } },
                        onNotifications = onOpenNotifications)
                    1 -> GradesContent(
                        token = token,
                        dashboard = dashboard,
                        usuario = usuario,
                        onOpenCourse = { periodo, carrera, crn, nombre ->
                            onOpenCourse(periodo, carrera, crn, nombre)
                        }
                    )
                    2 -> HorarioContent(
                        token = token,
                        dashboard = dashboard,
                        usuario = usuario
                    )
                    3 -> AsistenciaContent(
                        token = token,
                        dashboard = dashboard,
                        usuario = usuario
                    )
                    else -> CanvasContent(
                        token = token,
                        usuario = usuario
                    )
                }
            }
        }
    }
}

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            },
            title = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "UPAO S",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "v.$appVersion",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Aplicación estudiantil no oficial diseñada para optimizar el acceso a cursos, horarios, notas y asistencias UPAO.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Desarrollado para la comunidad universitaria UPAO.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}


package com.akiestoy.beacons

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.akiestoy.beacons.data.AppDatabase
import com.akiestoy.beacons.data.UserRepository
import com.akiestoy.beacons.ui.BeaconScreen
import com.akiestoy.beacons.ui.BeaconViewModel
import com.akiestoy.beacons.ui.components.UserRegistrationDialog
import com.akiestoy.beacons.ui.navigation.NavDestination
import com.akiestoy.beacons.ui.screens.AddBeaconsScreen
import com.akiestoy.beacons.ui.screens.ConfigurationScreen
import com.akiestoy.beacons.ui.screens.ConnectManualScreen
import com.akiestoy.beacons.ui.screens.FavoritesScreen
import com.akiestoy.beacons.ui.screens.HomeScreen
import com.akiestoy.beacons.ui.screens.LinkedBeaconsScreen
import com.akiestoy.beacons.ui.screens.PacketsScreen
import com.akiestoy.beacons.ui.screens.ProximityScreen
import com.akiestoy.beacons.ui.screens.SettingsScreen
import com.akiestoy.beacons.ui.screens.UpdateWorkerScreen
import com.akiestoy.beacons.ui.theme.AkiEstoyTheme
import com.akiestoy.beacons.viewmodel.RegistrationState
import com.akiestoy.beacons.viewmodel.SuperAdminViewModel
import com.akiestoy.beacons.viewmodel.UserRegistrationViewModel
import com.akiestoy.beacons.viewmodel.UserRegistrationViewModelFactory
import com.akiestoy.beacons.service.ProximityForegroundService

class MainActivity : ComponentActivity() {

    private val viewModel: BeaconViewModel by viewModels()

    // ViewModel de registro de usuario
    private lateinit var userRegistrationViewModel: UserRegistrationViewModel
    private lateinit var zoneViewModel: com.akiestoy.beacons.viewmodel.ZoneViewModel

    // ViewModel de Super Admin
    private val superAdminViewModel: SuperAdminViewModel by viewModels()

    private var hasPermissions by mutableStateOf(false)

    // Registro de permiso de background location (debe solicitarse por separado en Android 10+)
    private val backgroundLocationLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                android.util.Log.i("MainActivity", "ACCESS_BACKGROUND_LOCATION granted: $granted")
            }

    // Registro de permisos
    private val permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    permissions ->
                android.util.Log.i("MainActivity", "🔐 Permission result received")
                permissions.forEach { (permission, granted) ->
                    android.util.Log.d("MainActivity", "   └─ $permission: $granted")
                }

                val allGranted = permissions.values.all { it }
                hasPermissions = allGranted
                android.util.Log.i("MainActivity", "All permissions granted: $allGranted")

                // Iniciar servicio en segundo plano si se otorgaron los permisos
                if (allGranted) {
                    android.util.Log.i("MainActivity", "Starting background service from permission callback...")
                    // Solicitar ACCESS_BACKGROUND_LOCATION por separado (Android 10+ lo requiere)
                    requestBackgroundLocationPermission()
                    startBackgroundService()
                } else {
                    android.util.Log.w("MainActivity", "Not all permissions granted, service not started")
                }
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.i("MainActivity", "📱 onCreate called")

        // Inicializar base de datos y repositorio
        val database = AppDatabase.getDatabase(applicationContext)
        val favoritesRepository = com.akiestoy.beacons.data.FavoritesRepository(applicationContext)
        val userRepository = UserRepository(
            database.userDao(),
            database.zoneDao(),
            database.registeredBeaconDao(),
            database.pendingEventDao(),
            database.pendingZoneEventDao(),
            com.akiestoy.beacons.api.ApiClient.authApi,
            com.akiestoy.beacons.api.ApiClient.zonesApi,
            favoritesRepository
        )

        // Inicializar ViewModel de registro
        userRegistrationViewModel =
                ViewModelProvider(this, UserRegistrationViewModelFactory(userRepository))[
                        UserRegistrationViewModel::class.java]

        // Inicializar ViewModel de zonas
        zoneViewModel =
                ViewModelProvider(this, com.akiestoy.beacons.viewmodel.ZoneViewModelFactory(database.zoneDao()))[
                        com.akiestoy.beacons.viewmodel.ZoneViewModel::class.java]

        // Verificar permisos iniciales
        hasPermissions = checkPermissions()
        android.util.Log.i("MainActivity", "🔐 Permissions check: hasPermissions = $hasPermissions")

        setContent {
            AkiEstoyTheme {
                MainScreen(
                        viewModel = viewModel,
                        userRegistrationViewModel = userRegistrationViewModel,
                        superAdminViewModel = superAdminViewModel,
                        zoneViewModel = zoneViewModel,
                        hasPermissions = hasPermissions,
                        onRequestPermissions = { requestPermissions() }
                )
            }
        }

        // Solicitar permisos automáticamente al inicio si no los tiene
        if (!hasPermissions) {
            android.util.Log.w("MainActivity", "⚠️ Permissions not granted, requesting...")
            requestPermissions()
        } else {
            android.util.Log.i("MainActivity", "✅ Permissions already granted, starting service...")
            // Si ya tiene permisos, iniciar servicio inmediatamente
            startBackgroundService()
        }

        // Solicitar exclusión de optimización de batería
        requestBatteryOptimizationExemption()
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.i("MainActivity", "📱 onResume called")

        // Verificar y reiniciar el servicio si está detenido pero debería estar activo
        try {
            if (hasPermissions) {
                val isServiceRunning = ProximityForegroundService.isServiceRunning(this)
                android.util.Log.i("MainActivity", "🔍 Service running status: $isServiceRunning")

                if (!isServiceRunning) {
                    android.util.Log.w("MainActivity", "⚠️ Service not running! Restarting...")
                    startBackgroundService()
                } else {
                    android.util.Log.i("MainActivity", "✅ Service already running")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ Error en onResume verificando servicio", e)
        }
    }

    /**
     * Fuerza una verificación de salud del servicio
     * Si el servicio está corriendo pero el scanner está muerto, lo reinicia
     */
    private fun forceServiceHealthCheck() {
        // Detener y reiniciar el servicio para forzar un reinicio limpio del scanner
        // Esto es más agresivo pero garantiza que el scanner se reinicie
        try {
            ProximityForegroundService.stopService(this)
            // Pequeño delay antes de reiniciar
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                ProximityForegroundService.startService(this)
                android.util.Log.i("MainActivity", "✅ Service restarted for health check")
            }, 500)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error during health check restart", e)
            // Si falla, intentar solo iniciar
            startBackgroundService()
        }
    }

    /**
     * Solicita al usuario que excluya la app de optimización de batería
     * para asegurar que el servicio no sea matado por el sistema
     */
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = android.content.Intent()
            val packageName = packageName
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager

            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                android.util.Log.w("MainActivity", "⚠️ App is being battery optimized! Requesting exemption...")
                intent.action = android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = android.net.Uri.parse("package:$packageName")
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "❌ Error requesting battery optimization exemption", e)
                }
            } else {
                android.util.Log.i("MainActivity", "✅ App already excluded from battery optimization")
            }
        }
    }

    /**
     * Inicia el servicio en segundo plano para escaneo continuo de beacons
     */
    private fun startBackgroundService() {
        try {
            // Verificar que el Bluetooth está disponible antes de iniciar
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            if (bluetoothManager?.adapter == null) {
                android.util.Log.w("MainActivity", "⚠️ Bluetooth not available, skipping service start")
                return
            }

            ProximityForegroundService.startService(this)
            android.util.Log.i("MainActivity", "🚀 ProximityForegroundService started automatically")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ Error starting ProximityForegroundService", e)
        }
    }

    /** Verifica si todos los permisos necesarios están otorgados */
    private fun checkPermissions(): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all {
            checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    /** Solicita los permisos necesarios */
    private fun requestPermissions() {
        permissionLauncher.launch(getRequiredPermissions())
    }

    /**
     * Solicita ACCESS_BACKGROUND_LOCATION por separado.
     * Android 10+ requiere que se pida después de ACCESS_FINE_LOCATION.
     */
    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasBgLocation = checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasBgLocation) {
                android.util.Log.i("MainActivity", "Requesting ACCESS_BACKGROUND_LOCATION")
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
    }

    /** Obtiene la lista de permisos necesarios según la versión de Android */
    private fun getRequiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13+ (API 33+)
                arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.POST_NOTIFICATIONS
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                // Android 12 (API 31-32)
                arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // Android 10-11 (API 29-30)
                arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                )
            }
            else -> {
                // Android 9 y anteriores (API 28-)
                arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
        viewModel: BeaconViewModel,
        userRegistrationViewModel: UserRegistrationViewModel,
        superAdminViewModel: SuperAdminViewModel,
        zoneViewModel: com.akiestoy.beacons.viewmodel.ZoneViewModel,
        hasPermissions: Boolean,
        onRequestPermissions: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Observar el estado del usuario
    val currentUser by userRegistrationViewModel.currentUser.collectAsState()
    val rutInput by userRegistrationViewModel.rutInput.collectAsState()
    val rutError by userRegistrationViewModel.rutError.collectAsState()
    val rutEmpresaInput by userRegistrationViewModel.rutEmpresaInput.collectAsState()
    val rutEmpresaError by userRegistrationViewModel.rutEmpresaError.collectAsState()
    val registrationState by userRegistrationViewModel.registrationState.collectAsState()

    // Observar el estado de autenticación del super admin
    val isSuperAdminAuthenticated by superAdminViewModel.isAuthenticated.collectAsState()

    // Mostrar dialog de registro si no hay usuario
    if (currentUser == null) {
        UserRegistrationDialog(
                rutInput = rutInput,
                rutError = rutError,
                rutEmpresaInput = rutEmpresaInput,
                rutEmpresaError = rutEmpresaError,
                registrationState = registrationState,
                onRutChange = { userRegistrationViewModel.updateRutInput(it) },
                onRutEmpresaChange = { userRegistrationViewModel.updateRutEmpresaInput(it) },
                onRegisterClick = { userRegistrationViewModel.validateAndRegister() }
        )
    }

    // Resetear el estado de registro cuando sea exitoso
    LaunchedEffect(registrationState) {
        if (registrationState is RegistrationState.Success) {
            userRegistrationViewModel.resetRegistrationState()
        }
    }

    // Estado para forzar refresh cuando la app vuelve al primer plano
    var appResumeCounter by remember { mutableStateOf(0) }

    // Observar el ciclo de vida para detectar cuando la app vuelve al primer plano
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                android.util.Log.i("MainActivity", "🔄 App resumed, triggering beacon sync...")
                appResumeCounter++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Refrescar zonas cada vez que se abre la app y hay sesión iniciada
    // Se ejecuta al login inicial Y cada vez que la app vuelve al primer plano
    LaunchedEffect(currentUser, appResumeCounter) {
        if (currentUser != null) {
            android.util.Log.i("MainActivity", "🔄 Sincronizando beacons del servidor (user=${currentUser?.name}, resume=$appResumeCounter)...")
            userRegistrationViewModel.refreshZones()
        }
    }

    Scaffold(
            bottomBar = {
                // Solo mostrar el bottom navigation bar cuando el super admin esté autenticado
                if (isSuperAdminAuthenticated) {
                    NavigationBar {
                        // Obtener items dinámicamente según autenticación de super admin
                        val navItems = remember(isSuperAdminAuthenticated) {
                            NavDestination.getItems(isSuperAdminAuthenticated)
                        }

                        navItems.forEach { destination ->
                            NavigationBarItem(
                                    icon = {
                                        Icon(
                                                imageVector = destination.icon,
                                                contentDescription = destination.title
                                        )
                                    },
                                    label = { Text(destination.title) },
                                    selected = currentRoute == destination.route,
                                    onClick = {
                                        navController.navigate(destination.route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                            )
                        }
                    }
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        NavHost(
                navController = navController,
                startDestination = NavDestination.Home.route,
                modifier = Modifier.padding(paddingValues)
        ) {
            composable(NavDestination.Home.route) {
                HomeScreen(
                        userViewModel = userRegistrationViewModel,
                        beaconViewModel = viewModel,
                        superAdminViewModel = superAdminViewModel,
                        onRequestPermissions = onRequestPermissions
                )
            }
            composable(NavDestination.Scanner.route) {
                BeaconScreen(
                        viewModel = viewModel,
                        onRequestPermissions = onRequestPermissions,
                        hasPermissions = hasPermissions
                )
            }
            composable(NavDestination.Favorites.route) { FavoritesScreen(viewModel = viewModel) }
            composable(NavDestination.Packets.route) { PacketsScreen(viewModel = viewModel) }
            composable(NavDestination.Proximity.route) { ProximityScreen() }
            composable(NavDestination.Settings.route) { SettingsScreen() }
            composable(NavDestination.Configuration.route) {
                ConfigurationScreen(
                        superAdminViewModel = superAdminViewModel,
                        beaconViewModel = viewModel,
                        onNavigateToLinkedBeacons = {
                            navController.navigate(NavDestination.LinkedBeacons.route)
                        },
                        onNavigateToAddBeacons = {
                            navController.navigate(NavDestination.AddBeacons.route)
                        },
                        onNavigateToConnectManual = {
                            navController.navigate(NavDestination.ConnectManual.route)
                        },
                        onNavigateToUpdateWorker = {
                            navController.navigate(NavDestination.UpdateWorker.route)
                        },
                        onNavigateToHome = {
                            navController.navigate(NavDestination.Home.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    inclusive = false
                                }
                                launchSingleTop = true
                            }
                        }
                )
            }
            composable(NavDestination.LinkedBeacons.route) {
                LinkedBeaconsScreen(
                        beaconViewModel = viewModel,
                        zoneViewModel = zoneViewModel,
                        onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(NavDestination.AddBeacons.route) {
                AddBeaconsScreen(
                        beaconViewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(NavDestination.ConnectManual.route) {
                ConnectManualScreen(
                        beaconViewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(NavDestination.UpdateWorker.route) {
                UpdateWorkerScreen(
                        userViewModel = userRegistrationViewModel,
                        onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

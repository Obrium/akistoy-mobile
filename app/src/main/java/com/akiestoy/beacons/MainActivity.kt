package com.akiestoy.beacons

import android.Manifest
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

class MainActivity : ComponentActivity() {

    private val viewModel: BeaconViewModel by viewModels()

    // ViewModel de registro de usuario
    private lateinit var userRegistrationViewModel: UserRegistrationViewModel

    // ViewModel de Super Admin
    private val superAdminViewModel: SuperAdminViewModel by viewModels()

    private var hasPermissions by mutableStateOf(false)

    // Registro de permisos
    private val permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    permissions ->
                val allGranted = permissions.values.all { it }
                hasPermissions = allGranted
                // No iniciar automáticamente - dejar que el usuario presione el botón
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar base de datos y repositorio
        val database = AppDatabase.getDatabase(applicationContext)
        val userRepository = UserRepository(database.userDao())

        // Inicializar ViewModel de registro
        userRegistrationViewModel =
                ViewModelProvider(this, UserRegistrationViewModelFactory(userRepository))[
                        UserRegistrationViewModel::class.java]

        // Verificar permisos iniciales
        hasPermissions = checkPermissions()

        setContent {
            AkiEstoyTheme {
                MainScreen(
                        viewModel = viewModel,
                        userRegistrationViewModel = userRegistrationViewModel,
                        superAdminViewModel = superAdminViewModel,
                        hasPermissions = hasPermissions,
                        onRequestPermissions = { requestPermissions() }
                )
            }
        }

        // Solicitar permisos automáticamente al inicio si no los tiene
        if (!hasPermissions) {
            requestPermissions()
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

    /** Obtiene la lista de permisos necesarios según la versión de Android */
    private fun getRequiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                // Android 12+ (API 31+)
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
    val registrationState by userRegistrationViewModel.registrationState.collectAsState()

    // Observar el estado de autenticación del super admin
    val isSuperAdminAuthenticated by superAdminViewModel.isAuthenticated.collectAsState()

    // Mostrar dialog de registro si no hay usuario
    if (currentUser == null) {
        UserRegistrationDialog(
                rutInput = rutInput,
                rutError = rutError,
                registrationState = registrationState,
                onRutChange = { userRegistrationViewModel.updateRutInput(it) },
                onRegisterClick = { userRegistrationViewModel.validateAndRegister() }
        )
    }

    // Resetear el estado de registro cuando sea exitoso
    LaunchedEffect(registrationState) {
        if (registrationState is RegistrationState.Success) {
            userRegistrationViewModel.resetRegistrationState()
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
                        superAdminViewModel = superAdminViewModel
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

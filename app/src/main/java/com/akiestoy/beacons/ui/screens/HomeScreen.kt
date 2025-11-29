package com.akiestoy.beacons.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.akiestoy.beacons.data.AppDatabase
import com.akiestoy.beacons.model.BLEScanLog
import com.akiestoy.beacons.model.RegisteredBeacon
import com.akiestoy.beacons.state.AppState
import com.akiestoy.beacons.state.ZoneInfo
import com.akiestoy.beacons.ui.BeaconViewModel
import com.akiestoy.beacons.ui.components.SuperAdminDialog
import com.akiestoy.beacons.utils.RutValidator
import com.akiestoy.beacons.viewmodel.SuperAdminViewModel
import com.akiestoy.beacons.viewmodel.UserRegistrationViewModel
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext

/** Pantalla principal de Home Muestra el estado de conexión con beacons favoritos */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
        userViewModel: UserRegistrationViewModel,
        beaconViewModel: BeaconViewModel,
        superAdminViewModel: SuperAdminViewModel
) {
    val context = LocalContext.current
    val currentUser by userViewModel.currentUser.collectAsState()
    val favoriteBeacons by beaconViewModel.favoriteBeacons.collectAsState()

    // Observar el estado de autenticación del super admin
    val isSuperAdminAuthenticated by superAdminViewModel.isAuthenticated.collectAsState()

    // Estado para mostrar el dialog de super admin
    var showSuperAdminDialog by remember { mutableStateOf(false) }

    // Estado para pull-to-refresh
    var isRefreshing by remember { mutableStateOf(false) }

    // Estado para forzar recomposición cada 2 segundos
    var updateTrigger by remember { mutableStateOf(0) }

    // Observar el beacon seleccionado manualmente
    val manuallySelectedBeaconMac by AppState.manuallySelectedBeaconMac.collectAsState()

    // Estado para beacons registrados (para obtener zona)
    var registeredBeacons by remember { mutableStateOf<List<RegisteredBeacon>>(emptyList()) }

    // Cargar beacons registrados al inicio (solo los que tienen MAC)
    LaunchedEffect(Unit) {
        try {
            val db = AppDatabase.getDatabase(context)
            val allBeacons = db.registeredBeaconDao().getAllActiveBeaconsOnce()
            // Filtrar solo beacons que tienen MAC registrada
            registeredBeacons = allBeacons.filter { !it.mac.isNullOrEmpty() }
        } catch (e: Exception) {
            android.util.Log.e("HomeScreen", "Error cargando beacons registrados", e)
        }
    }

    // Sincronizar beacons del servidor periódicamente (cada 5 minutos)
    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            while (true) {
                // Refrescar zonas y beacons del servidor
                android.util.Log.i("HomeScreen", "🔄 Sincronizando beacons del servidor...")
                userViewModel.refreshZones()

                // Recargar beacons locales después de la sincronización
                try {
                    val db = AppDatabase.getDatabase(context)
                    registeredBeacons = db.registeredBeaconDao().getAllActiveBeaconsOnce()
                    android.util.Log.i("HomeScreen", "✅ Beacons actualizados: ${registeredBeacons.size}")
                } catch (e: Exception) {
                    android.util.Log.e("HomeScreen", "Error recargando beacons", e)
                }

                // Esperar 5 minutos antes del siguiente refresh
                delay(5 * 60 * 1000)
            }
        }
    }

    // Calcular el estado de conexión basado en el beacon más cercano o el seleccionado manualmente
    // Se recalcula cuando cambian los beacons, la selección manual o cada 2 segundos (updateTrigger)
    val connectionState =
            remember(favoriteBeacons, manuallySelectedBeaconMac, updateTrigger, registeredBeacons) {
                calculateConnectionState(favoriteBeacons, manuallySelectedBeaconMac, registeredBeacons)
            }

    // Observar la zona actual desde el estado global
    val currentZone by AppState.currentZone.collectAsState()

    // Iniciar escaneo automático DESPUÉS de que el usuario esté autenticado
    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            // Pequeño delay para asegurar que la UI esté lista
            delay(500)
            // Iniciar el escaneo automático de beacons registrados
            // Se conectará automáticamente al beacon más cercano
            beaconViewModel.startAutoScanning()
        }
    }

    // Loop separado para actualizar la UI cada 2 segundos (sin reiniciar el escaneo)
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000) // 2 segundos
            updateTrigger++ // Incrementar trigger para forzar recálculo de la UI
        }
    }

    // Mostrar dialog de super admin si está activo
    if (showSuperAdminDialog) {
        SuperAdminDialog(
                onDismiss = { showSuperAdminDialog = false },
                onLogin = { username, password ->
                    val success = superAdminViewModel.login(username, password)
                    if (success) {
                        showSuperAdminDialog = false
                    }
                    success // Retornar el resultado
                }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { isRefreshing = true },
                modifier = Modifier.fillMaxSize()
        ) {
            // Efecto para manejar la actualización manual (solo refresca UI, no reinicia escaneo)
            LaunchedEffect(isRefreshing) {
                if (isRefreshing) {
                    // Esperar 1 segundo para mostrar el spinner
                    delay(1000)
                    updateTrigger++ // Forzar recálculo de la UI
                    isRefreshing = false
                }
            }

            Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top,
                    modifier =
                            Modifier.fillMaxWidth()
                                    .padding(24.dp)
                                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(40.dp))

                // Título "Bienvenido" + nombre
                Text(
                        text = "Bienvenido",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Nombre del usuario
                Text(
                        text = currentUser?.name ?: "",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Texto "ESTADO:"
                Text(
                        text = "ESTADO:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Rectángulo con el estado
                Card(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                if (connectionState.isActive) {
                                                    Color(0xFF90EE90) // Verde claro
                                                } else {
                                                    Color.Red
                                                }
                                )
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                                text = if (connectionState.isActive) "ACTIVO" else "INACTIVO",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color =
                                        if (connectionState.isActive) {
                                            Color.Black
                                        } else {
                                            Color.White
                                        }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Zona actual y beacon conectado (si está activo)
                if (connectionState.isActive && connectionState.activeBeaconName != null) {
                    // Tarjeta de ZONA
                    Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.primaryContainer
                                    )
                    ) {
                        Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                    text = "ZONA ACTUAL",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                    text = connectionState.zoneName ?: connectionState.activeBeaconName,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    textAlign = TextAlign.Center
                            )

                            // Mostrar tiempo desde última señal
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Última señal: ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = if (connectionState.lastSeenSeconds <= 0) "ahora"
                                           else "hace ${connectionState.lastSeenSeconds}s",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            // RSSI
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Señal: ${connectionState.rssi ?: "?"} dBm",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Tenant ID del usuario
                currentUser?.let { user ->
                    Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.tertiaryContainer
                                    )
                    ) {
                        Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                    text = "Tenant ID:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                    text = user.tenantId,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        }

        // Botón en la esquina superior derecha
        IconButton(
                onClick = {
                    if (isSuperAdminAuthenticated) {
                        // Si está autenticado, cerrar sesión
                        superAdminViewModel.logout()
                    } else {
                        // Si no está autenticado, mostrar dialog de login
                        showSuperAdminDialog = true
                    }
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(
                    imageVector = if (isSuperAdminAuthenticated) {
                        Icons.AutoMirrored.Filled.Logout
                    } else {
                        Icons.Default.Settings
                    },
                    contentDescription = if (isSuperAdminAuthenticated) {
                        "Cerrar sesión"
                    } else {
                        "Super Admin"
                    },
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
            )
        }
    }
}

/** Estado de conexión con beacons */
data class ConnectionState(
        val isActive: Boolean,
        val activeBeaconName: String? = null,
        val zoneName: String? = null,
        val rssi: Int? = null,
        val lastSeenSeconds: Int = 0
)

/**
 * Calcula el estado de conexión basado en el beacon seleccionado manualmente o el más cercano
 * También actualiza el estado global de la zona actual
 */
private fun calculateConnectionState(
    favoriteBeacons: List<BLEScanLog>,
    manuallySelectedBeaconMac: String?,
    registeredBeacons: List<RegisteredBeacon> = emptyList()
): ConnectionState {
    if (favoriteBeacons.isEmpty()) {
        AppState.clearCurrentZone()
        return ConnectionState(isActive = false)
    }

    // Considerar solo beacons vistos en los últimos 15 segundos
    // (tolerancia para beacons E9 que envían señal cada ~3 segundos)
    val currentTime = System.currentTimeMillis()
    val fifteenSecondsAgo = currentTime - 15_000
    val recentBeacons = favoriteBeacons.filter { it.timestamp > fifteenSecondsAgo }

    if (recentBeacons.isEmpty()) {
        AppState.clearCurrentZone()
        return ConnectionState(isActive = false)
    }

    // Si hay un beacon seleccionado manualmente, usarlo (si está disponible en los recientes)
    val selectedBeacon = if (manuallySelectedBeaconMac != null) {
        recentBeacons.find { it.macAddress == manuallySelectedBeaconMac }
    } else {
        null
    }

    // Si no hay beacon manual o no está disponible, encontrar el más cercano
    val activeBeacon = selectedBeacon ?: recentBeacons.maxByOrNull { it.rssi }

    return if (activeBeacon != null) {
        // Buscar el beacon registrado para obtener el nombre de la zona
        // PRIORIDAD 1: Match por MAC address (más confiable)
        // PRIORIDAD 2: Match por UUID + major + minor
        val matchingRegisteredBeacon = registeredBeacons.find { registered ->
            !registered.mac.isNullOrEmpty() &&
            activeBeacon.macAddress.equals(registered.mac, ignoreCase = true)
        } ?: run {
            // Si no hay match por MAC, intentar por UUID + major + minor
            val iBeaconData = activeBeacon.iBeaconData
            if (iBeaconData != null) {
                registeredBeacons.find { registered ->
                    registered.advUuid.equals(iBeaconData.uuid, ignoreCase = true) &&
                    registered.major == iBeaconData.major &&
                    registered.minor == iBeaconData.minor
                }
            } else {
                null
            }
        }

        val zoneName = matchingRegisteredBeacon?.zoneName
        val beaconName = matchingRegisteredBeacon?.zoneName
            ?: activeBeacon.deviceName.takeIf { it.isNotEmpty() }
            ?: activeBeacon.macAddress

        // Calcular hace cuántos segundos fue la última señal
        val lastSeenSeconds = ((currentTime - activeBeacon.timestamp) / 1000).toInt()

        AppState.updateCurrentZone(
                ZoneInfo(
                        beaconName = beaconName,
                        beaconMac = activeBeacon.macAddress,
                        rssi = activeBeacon.rssi,
                        timestamp = activeBeacon.timestamp
                )
        )

        ConnectionState(
            isActive = true,
            activeBeaconName = beaconName,
            zoneName = zoneName,
            rssi = activeBeacon.rssi,
            lastSeenSeconds = lastSeenSeconds
        )
    } else {
        AppState.clearCurrentZone()
        ConnectionState(isActive = false)
    }
}

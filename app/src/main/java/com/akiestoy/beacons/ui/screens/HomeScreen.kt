package com.akiestoy.beacons.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.akiestoy.beacons.model.BLEScanLog
import com.akiestoy.beacons.state.AppState
import com.akiestoy.beacons.state.ZoneInfo
import com.akiestoy.beacons.ui.BeaconViewModel
import com.akiestoy.beacons.ui.components.SuperAdminDialog
import com.akiestoy.beacons.utils.RutValidator
import com.akiestoy.beacons.viewmodel.SuperAdminViewModel
import com.akiestoy.beacons.viewmodel.UserRegistrationViewModel
import kotlinx.coroutines.delay

/** Pantalla principal de Home Muestra el estado de conexión con beacons favoritos */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
        userViewModel: UserRegistrationViewModel,
        beaconViewModel: BeaconViewModel,
        superAdminViewModel: SuperAdminViewModel
) {
    val currentUser by userViewModel.currentUser.collectAsState()
    val favoriteBeacons by beaconViewModel.favoriteBeacons.collectAsState()

    // Estado para mostrar el dialog de super admin
    var showSuperAdminDialog by remember { mutableStateOf(false) }

    // Estado para pull-to-refresh
    var isRefreshing by remember { mutableStateOf(false) }

    // Estado para forzar recomposición cada 2 segundos
    var updateTrigger by remember { mutableStateOf(0) }

    // Calcular el estado de conexión basado en el beacon más cercano
    // Se recalcula cuando cambian los beacons o cada 2 segundos (updateTrigger)
    val connectionState =
            remember(favoriteBeacons, updateTrigger) { calculateConnectionState(favoriteBeacons) }

    // Observar la zona actual desde el estado global
    val currentZone by AppState.currentZone.collectAsState()

    // Iniciar escaneo al montar la pantalla (UNA SOLA VEZ)
    LaunchedEffect(Unit) {
        // Iniciar el escaneo una sola vez - se mantendrá activo continuamente
        beaconViewModel.startScanning()
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

                // Nombre del beacon (si está conectado)
                if (connectionState.isActive && connectionState.activeBeaconName != null) {
                    Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.secondaryContainer
                                    )
                    ) {
                        Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                    text = "Beacon conectado:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                    text = connectionState.activeBeaconName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // RUT del usuario
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
                                    text = "RUT:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                    text = RutValidator.formatRut(user.id),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        }

        // Botón de tuerca en la esquina superior derecha
        IconButton(
                onClick = { showSuperAdminDialog = true },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Super Admin",
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
        val rssi: Int? = null
)

/**
 * Calcula el estado de conexión basado en el beacon más cercano (mejor RSSI) También actualiza el
 * estado global de la zona actual
 */
private fun calculateConnectionState(favoriteBeacons: List<BLEScanLog>): ConnectionState {
    if (favoriteBeacons.isEmpty()) {
        AppState.clearCurrentZone()
        return ConnectionState(isActive = false)
    }

    // Considerar solo beacons vistos en los últimos 10 segundos
    val currentTime = System.currentTimeMillis()
    val tenSecondsAgo = currentTime - 10_000
    val recentBeacons = favoriteBeacons.filter { it.timestamp > tenSecondsAgo }

    if (recentBeacons.isEmpty()) {
        AppState.clearCurrentZone()
        return ConnectionState(isActive = false)
    }

    // Encontrar el beacon más cercano (mejor RSSI - más cercano a 0)
    // El RSSI es negativo, así que el más grande (menos negativo) es el más cercano
    val closestBeacon = recentBeacons.maxByOrNull { it.rssi }

    return if (closestBeacon != null) {
        // Actualizar el estado global con la zona actual
        val beaconName =
                closestBeacon.deviceName.takeIf { it.isNotEmpty() } ?: closestBeacon.macAddress

        AppState.updateCurrentZone(
                ZoneInfo(
                        beaconName = beaconName,
                        beaconMac = closestBeacon.macAddress,
                        rssi = closestBeacon.rssi,
                        timestamp = closestBeacon.timestamp
                )
        )

        ConnectionState(isActive = true, activeBeaconName = beaconName, rssi = closestBeacon.rssi)
    } else {
        AppState.clearCurrentZone()
        ConnectionState(isActive = false)
    }
}

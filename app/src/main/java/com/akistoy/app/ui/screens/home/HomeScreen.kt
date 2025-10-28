package com.akistoy.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.akistoy.app.ui.components.BeaconStatusIndicator
import com.akistoy.app.ui.components.DetectionList
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.periodUntil

@Composable
fun HomeRoute(
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // Auto-iniciar el servicio de escaneo al cargar la pantalla
    LaunchedEffect(Unit) {
        viewModel.ensureServiceStarted()
    }

    HomeScreen(
        state = state,
        onOpenSettings = onOpenSettings,
        onScanDevices = viewModel::scanAllDevices,
        onDismissDeviceList = viewModel::dismissDeviceListDialog,
        onAddTrustedBeacon = viewModel::addToTrustedBeacons,
        onShowTrustedBeacons = viewModel::showTrustedBeaconsDialog,
        onDismissTrustedBeacons = viewModel::dismissTrustedBeaconsDialog,
        onRemoveBeacon = viewModel::removeBeaconFromTrusted,
        onToggleBeaconEnabled = viewModel::toggleBeaconEnabled
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpenSettings: () -> Unit,
    onScanDevices: () -> Unit,
    onDismissDeviceList: () -> Unit,
    onAddTrustedBeacon: (BleDeviceInfo) -> Unit,
    onShowTrustedBeacons: () -> Unit,
    onDismissTrustedBeacons: () -> Unit,
    onRemoveBeacon: (String) -> Unit,
    onToggleBeaconEnabled: (String, Boolean) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.bluetoothEnabled) {
        if (!state.bluetoothEnabled) {
            snackbarHostState.showSnackbar("Bluetooth desactivado. Actívalo para continuar")
        }
    }

    val scrollState = rememberScrollState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Tarjeta destacada para mostrar el beacon conectado
            if (state.activeBeaconId != null && state.isServiceRunning) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ZONA DETECTADA",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF616161)  // Gris oscuro
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Mostrar el nombre de la zona de forma destacada
                        Text(
                            text = state.activeZoneName ?: "Zona Desconocida",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF212121)  // Negro casi puro
                        )

                        // Mostrar el Beacon ID de forma secundaria (más pequeño)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "ID: ${state.activeBeaconId?.takeLast(12)?.uppercase() ?: "N/A"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF9E9E9E)  // Gris medio
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Distancia",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF757575)  // Gris
                                )
                                Text(
                                    text = state.distanceMeters?.let { "%.2f m".format(it) } ?: "-- m",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF424242)  // Gris oscuro
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Señal",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF757575)  // Gris
                                )
                                Text(
                                    text = "${state.lastRssi ?: 0} dBm",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF424242)  // Gris oscuro
                                )
                            }
                        }
                    }
                }
            }

            BeaconStatusIndicator(
                isDetecting = state.isServiceRunning && state.detections.isNotEmpty(),
                lastRssi = state.lastRssi,
                beaconId = state.activeBeaconId,
                proximity = state.activeBeaconProximity,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Total detecciones: ${state.detectionCount}")
                Text(text = "Última: ${state.lastDetectionTime.toReadableTime()}")
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Indicador de estado del servicio (siempre activo)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (state.isServiceRunning) "✓ Escaneo activo" else "⚠ Iniciando escaneo...",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (state.isServiceRunning)
                            Color(0xFF4CAF50)  // Verde Material
                        else
                            Color(0xFFF44336)  // Rojo Material
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onScanDevices, modifier = Modifier.fillMaxWidth()) {
                Text(text = "🔍 Buscar beacons cercanos")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onShowTrustedBeacons, modifier = Modifier.fillMaxWidth()) {
                Text(text = "⭐ Gestionar beacons confiables")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text(text = "⚙️ Configuración")
            }
            Spacer(modifier = Modifier.height(16.dp))
            DetectionList(
                detections = state.detections,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Diálogo para mostrar dispositivos BLE detectados
    if (state.showDeviceListDialog) {
        AlertDialog(
            onDismissRequest = onDismissDeviceList,
            title = {
                Column {
                    Text(text = "Dispositivos BLE Cercanos")
                    Text(
                        text = "Todos los dispositivos Bluetooth. Busca 'KBPro' en el nombre.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF757575)
                    )
                }
            },
            text = {
                if (state.scannedDevices.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "🔍 Escaneando dispositivos BLE...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Busca 'KBPro_275805' o dispositivos con UUID CABACAB0...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF757575)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(state.scannedDevices.take(10)) { device ->  // Limitar a 10 más cercanos
                            val isClosest = state.scannedDevices.firstOrNull() == device
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isClosest)
                                        Color(0xFFE3F2FD)  // Azul claro para el más cercano
                                    else
                                        Color(0xFFF5F5F5)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    // Indicador de más cercano
                                    if (isClosest) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "⭐ MÁS CERCANO",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1976D2)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }

                                    // Nombre del dispositivo (GRANDE Y DESTACADO)
                                    val isKBPro = device.name?.contains("KBPro", ignoreCase = true) == true ||
                                                  device.name?.contains("275805", ignoreCase = true) == true

                                    Text(
                                        text = device.name ?: "Sin nombre",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isKBPro) Color(0xFFFF6F00) else Color(0xFF212121)
                                    )

                                    if (isKBPro) {
                                        Text(
                                            text = "🎯 ¡ESTE PODRÍA SER TU BEACON!",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFF6F00)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // UUID (LO MÁS IMPORTANTE PARA IDENTIFICAR)
                                    if (device.serviceUuids.isNotEmpty()) {
                                        val isTargetUUID = device.serviceUuids.any {
                                            it.contains("cabacab0", ignoreCase = true)
                                        }

                                        Text(
                                            text = "🔷 UUID:",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isTargetUUID) Color(0xFFFF6F00) else Color(0xFF1976D2)
                                        )
                                        device.serviceUuids.forEach { uuid ->
                                            Text(
                                                text = uuid.uppercase(),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isTargetUUID) Color(0xFFFF6F00) else Color(0xFF0D47A1)
                                            )
                                        }
                                        if (isTargetUUID) {
                                            Text(
                                                text = "🎯 ¡ESTE ES TU BEACON!",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFF6F00)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    } else {
                                        Text(
                                            text = "⚠️ Sin UUID (no es beacon estándar)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFFF9800)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    Divider(color = Color(0xFFE0E0E0))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Información técnica
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = "📍 Distancia",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF757575)
                                            )
                                            val distance = when {
                                                device.rssi >= -50 -> "< 0.5m"
                                                device.rssi >= -60 -> "0.5-1m"
                                                device.rssi >= -70 -> "1-3m"
                                                device.rssi >= -80 -> "3-6m"
                                                device.rssi >= -90 -> "6-10m"
                                                else -> "> 10m"
                                            }
                                            Text(
                                                text = distance,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2196F3)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "📶 Señal",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF757575)
                                            )
                                            Text(
                                                text = "${device.rssi} dBm",
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF616161)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Información de debug
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color(0xFFFFF8E1)
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text(
                                                text = "📊 Info Técnica",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "MAC: ${device.address}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                            )
                                            Text(
                                                text = "RSSI: ${device.rssi} dBm",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (device.rssi >= -70) Color(0xFF4CAF50) else Color(0xFFFF9800)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            onAddTrustedBeacon(device)
                                            onDismissDeviceList()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("✓ Agregar a beacons confiables")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissDeviceList) {
                    Text("Cerrar")
                }
            }
        )
    }

    // Diálogo para gestionar beacons confiables
    if (state.showTrustedBeaconsDialog) {
        AlertDialog(
            onDismissRequest = onDismissTrustedBeacons,
            title = {
                Text(text = "Beacons Confiables")
            },
            text = {
                if (state.trustedBeacons.isEmpty()) {
                    Text(
                        "No tienes beacons en tu lista confiable.\n\nUsa 'Buscar beacons cercanos' para agregar uno.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(state.trustedBeacons) { beacon ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (beacon.isEnabled)
                                        Color(0xFFE8F5E9)  // Verde claro si está activo
                                    else
                                        Color(0xFFEEEEEE)  // Gris si está deshabilitado
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = beacon.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        androidx.compose.material3.Switch(
                                            checked = beacon.isEnabled,
                                            onCheckedChange = { enabled ->
                                                onToggleBeaconEnabled(beacon.beaconId, enabled)
                                            }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "MAC: ${beacon.beaconId}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF616161)
                                    )

                                    if (beacon.uuid != null) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "UUID: ${beacon.uuid}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF616161)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(
                                        onClick = {
                                            onRemoveBeacon(beacon.beaconId)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "🗑️ Eliminar de la lista",
                                            color = Color(0xFFD32F2F)  // Rojo
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissTrustedBeacons) {
                    Text("Cerrar")
                }
            }
        )
    }
}

private fun Instant?.toReadableTime(): String = this?.let {
    val period = it.periodUntil(Clock.System.now(), TimeZone.UTC)
    if (period.minutes == 0 && period.seconds == 0) "Ahora" else "hace ${period.minutes}m ${period.seconds}s"
} ?: "N/A"

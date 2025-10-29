package com.akiestoy.beacons.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akiestoy.beacons.model.BeaconDetection
import com.akiestoy.beacons.model.ProximityZone

@Composable
fun BeaconScreen(
    viewModel: BeaconViewModel,
    onRequestPermissions: () -> Unit,
    hasPermissions: Boolean
) {
    val uiState by viewModel.uiState.collectAsState()
    val detections by viewModel.detections.collectAsState()
    val filteredScanLogs by viewModel.filteredScanLogs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
            // Estado y controles
            StatusCard(
                uiState = uiState,
                hasPermissions = hasPermissions,
                onStartScanning = {
                    if (hasPermissions) {
                        viewModel.startScanning()
                    } else {
                        onRequestPermissions()
                    }
                },
                onStopScanning = { viewModel.stopScanning() },
                onClearLogs = { viewModel.clearLogs() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Logs de escaneo BLE (lo más importante ahora)
            if (filteredScanLogs.isNotEmpty() || searchQuery.isNotEmpty()) {
                ScanLogsView(
                    scanLogs = filteredScanLogs,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                    viewModel = viewModel
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lista de beacons detectados (filtrados)
            if (detections.isNotEmpty()) {
                BeaconList(detections = detections)
            }
    }
}

@Composable
fun StatusCard(
    uiState: BeaconUiState,
    hasPermissions: Boolean,
    onStartScanning: () -> Unit,
    onStopScanning: () -> Unit,
    onClearLogs: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Indicador de estado
            when (uiState) {
                is BeaconUiState.Idle -> {
                    Text(
                        text = "Listo para escanear",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is BeaconUiState.Scanning -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "🔍 Escaneando...",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Iniciando búsqueda de dispositivos BLE",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is BeaconUiState.ScanningWithDevices -> {
                    Text(
                        text = "📡 ${uiState.deviceCount} dispositivo(s) BLE detectado(s)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Mostrando todos los dispositivos Bluetooth",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is BeaconUiState.DetectingBeacons -> {
                    Text(
                        text = "🎯 ${uiState.count} beacon(s) iBeacon detectado(s)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is BeaconUiState.Error -> {
                    Text(
                        text = "Error: ${uiState.message}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botones de control
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (uiState is BeaconUiState.Idle) {
                            onStartScanning()
                        } else {
                            onStopScanning()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (uiState is BeaconUiState.Idle)
                            Icons.Default.PlayArrow
                        else
                            Icons.Default.Stop,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState is BeaconUiState.Idle)
                            "Iniciar"
                        else
                            "Detener"
                    )
                }

                OutlinedButton(
                    onClick = onClearLogs,
                    modifier = Modifier.weight(0.5f),
                    enabled = uiState !is BeaconUiState.Idle
                ) {
                    Text("Limpiar")
                }
            }

            if (!hasPermissions) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⚠️ Necesitas otorgar permisos de ubicación y Bluetooth",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun BeaconList(detections: List<BeaconDetection>) {
    AnimatedVisibility(
        visible = detections.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Card(
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Beacons Detectados",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(detections, key = { "${it.major}-${it.minor}" }) { detection ->
                    BeaconCard(detection = detection)
                }
            }
        }
    }
}

@Composable
fun BeaconCard(detection: BeaconDetection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Información del beacon
            Column(modifier = Modifier.weight(1f)) {
                // Ubicación
                Text(
                    text = detection.location.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Distancia
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = detection.proximity.emoji,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = detection.getDistanceDescription(),
                        style = MaterialTheme.typography.titleMedium,
                        color = when (detection.proximity) {
                            ProximityZone.IMMEDIATE -> MaterialTheme.colorScheme.error
                            ProximityZone.NEAR -> MaterialTheme.colorScheme.tertiary
                            ProximityZone.FAR -> MaterialTheme.colorScheme.primary
                            ProximityZone.UNKNOWN -> MaterialTheme.colorScheme.outline
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Detalles técnicos
                Text(
                    text = "Major: ${detection.major} | Minor: ${detection.minor}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "RSSI: ${detection.rssi} dBm | TX Power: ${detection.txPower} dBm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                detection.macAddress?.let {
                    Text(
                        text = "MAC: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Indicador de calidad de señal
            SignalQualityIndicator(
                quality = detection.getSignalQuality(),
                proximity = detection.proximity
            )
        }
    }
}

@Composable
fun SignalQualityIndicator(
    quality: Int,
    proximity: ProximityZone
) {
    val color = when (proximity) {
        ProximityZone.IMMEDIATE -> MaterialTheme.colorScheme.error
        ProximityZone.NEAR -> MaterialTheme.colorScheme.tertiary
        ProximityZone.FAR -> MaterialTheme.colorScheme.primary
        ProximityZone.UNKNOWN -> MaterialTheme.colorScheme.outline
    }

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$quality%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = "señal",
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
        }
    }
}

@Composable
fun ScanLogsView(
    scanLogs: List<com.akiestoy.beacons.model.BLEScanLog>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    viewModel: BeaconViewModel
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "🎯 iBeacons Detectados",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "${scanLogs.size} beacon${if (scanLogs.size != 1) "s" else ""}${if (searchQuery.isNotEmpty()) " (filtrados)" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Campo de búsqueda
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                placeholder = { Text("Buscar beacons por nombre, MAC, UUID, Major, Minor...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar"
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Limpiar búsqueda"
                            )
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            // Lista de logs con scroll
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(scanLogs, key = { it.macAddress }) { log ->
                    val isFavorite = viewModel.isFavorite(log.macAddress)
                    ScanLogCard(
                        log = log,
                        isFavorite = isFavorite,
                        onToggleFavorite = { viewModel.toggleFavorite(log.macAddress) }
                    )
                }
            }
        }
    }
}

@Composable
fun ScanLogCard(
    log: com.akiestoy.beacons.model.BLEScanLog,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (log.iBeaconData != null) 
                MaterialTheme.colorScheme.tertiaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Timestamp y nombre
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = log.deviceName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (log.iBeaconData != null) 
                            MaterialTheme.colorScheme.onTertiaryContainer 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "🕐 ${log.getFormattedTimestamp()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // MAC y RSSI
                Text(
                    text = "📍 ${log.macAddress}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "📶 RSSI: ${log.rssi} dBm${log.txPower?.let { " | ⚡ TX: $it dBm" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall
                )

                // Manufacturer Data
                if (log.manufacturerData.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "📦 Manufacturer Data:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    log.manufacturerData.forEach { (id, data) ->
                        Text(
                            text = "  0x${id.toString(16).uppercase()}: $data",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Service UUIDs
                if (log.serviceUuids.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "🔗 Services:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    log.serviceUuids.forEach { uuid ->
                        Text(
                            text = "  $uuid",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // iBeacon Data (destacado)
                log.iBeaconData?.let { ibeacon ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "✅ iBeacon Detectado!",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "UUID: ${ibeacon.uuid}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Major: ${ibeacon.major} | Minor: ${ibeacon.minor}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "TX Power: ${ibeacon.txPower} dBm",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            
            // Botón de favorito
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFavorite) "Quitar de favoritos" else "Agregar a favoritos",
                    tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

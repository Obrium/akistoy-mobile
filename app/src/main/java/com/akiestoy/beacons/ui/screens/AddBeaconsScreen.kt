package com.akiestoy.beacons.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SignalWifiStatusbarNull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.akiestoy.beacons.ui.BeaconViewModel
import com.akiestoy.beacons.ui.ConfigurationResult
import com.akiestoy.beacons.ui.components.ConfigureBeaconDialog

/** Pantalla para agregar beacons detectados a favoritos */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBeaconsScreen(
    beaconViewModel: BeaconViewModel,
    onNavigateBack: () -> Unit
) {
    // Observar beacons únicos detectados
    val uniqueDevices by beaconViewModel.uniqueDevices.collectAsState()

    // Estado del filtro de búsqueda
    var searchQuery by remember { mutableStateOf("") }

    // Estados para configuración de beacon
    val beaconToConfigure by beaconViewModel.beaconToConfigure.collectAsState()
    val zones by beaconViewModel.zones.collectAsState()
    val isConfiguringBeacon by beaconViewModel.isConfiguringBeacon.collectAsState()
    val isCreatingZone by beaconViewModel.isCreatingZone.collectAsState()
    val configurationResult by beaconViewModel.configurationResult.collectAsState()

    // Snackbar para mostrar resultados
    val snackbarHostState = remember { SnackbarHostState() }

    // Mostrar snackbar cuando hay resultado de configuración
    LaunchedEffect(configurationResult) {
        configurationResult?.let { result ->
            val message = when (result) {
                is ConfigurationResult.Success -> result.message
                is ConfigurationResult.Error -> result.message
            }
            snackbarHostState.showSnackbar(message)
            beaconViewModel.clearConfigurationResult()
        }
    }

    // Filtrar beacons por nombre (mostrar todos, priorizando iBeacons)
    val filteredBeacons = remember(uniqueDevices, searchQuery) {
        // Ordenar: primero iBeacons, luego otros dispositivos
        val sortedDevices = uniqueDevices.sortedByDescending { it.iBeaconData != null }
        if (searchQuery.isBlank()) {
            sortedDevices
        } else {
            sortedDevices.filter { beacon ->
                val beaconName = beacon.deviceName.ifEmpty {
                    "Beacon ${beacon.macAddress.takeLast(4)}"
                }
                beaconName.contains(searchQuery, ignoreCase = true) ||
                beacon.macAddress.contains(searchQuery, ignoreCase = true) ||
                beacon.iBeaconData?.uuid?.contains(searchQuery, ignoreCase = true) == true
            }
        }
    }

    // Iniciar escaneo al entrar a la pantalla
    LaunchedEffect(Unit) {
        beaconViewModel.startScanning()
    }

    // Detener escaneo al salir de la pantalla
    DisposableEffect(Unit) {
        onDispose {
            beaconViewModel.stopScanning()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Agregar Beacons",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver"
                            )
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Barra de búsqueda
                if (uniqueDevices.isNotEmpty()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Buscar por nombre, MAC o UUID...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Buscar"
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Limpiar"
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                if (uniqueDevices.isEmpty()) {
                    // Estado vacío - escaneando
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Icon(
                            imageVector = Icons.Default.SignalWifiStatusbarNull,
                            contentDescription = "Escaneando",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Buscando beacons cercanos...",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Asegúrate de tener Bluetooth activado",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (filteredBeacons.isEmpty()) {
                    // Estado: sin resultados de búsqueda
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Sin resultados",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No se encontraron beacons",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Intenta con otra búsqueda",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Lista de beacons detectados (filtrados)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredBeacons, key = { it.macAddress }) { beacon ->
                            val isFavorite = beaconViewModel.isFavorite(beacon.macAddress)
                            NearbyBeaconCard(
                                beacon = beacon,
                                isFavorite = isFavorite,
                                onHeartClick = {
                                    android.util.Log.i("AddBeaconsScreen", "❤️ CORAZÓN TOCADO! MAC: ${beacon.macAddress}, isFavorite: $isFavorite")
                                    if (isFavorite) {
                                        // Si ya es favorito, quitarlo de favoritos
                                        android.util.Log.i("AddBeaconsScreen", "🔄 Quitando de favoritos...")
                                        val identifier = com.akiestoy.beacons.model.BeaconIdentifier.fromScanLog(beacon)
                                        if (identifier != null) {
                                            beaconViewModel.toggleFavorite(identifier)
                                        } else {
                                            beaconViewModel.toggleFavorite(beacon.macAddress)
                                        }
                                    } else {
                                        // Si no es favorito, abrir diálogo de configuración
                                        android.util.Log.i("AddBeaconsScreen", "🔧 Abriendo diálogo de configuración...")
                                        beaconViewModel.startBeaconConfiguration(beacon)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Diálogo de configuración de beacon
        beaconToConfigure?.let { scanLog ->
            ConfigureBeaconDialog(
                scanLog = scanLog,
                zones = zones,
                isLoading = isConfiguringBeacon,
                isCreatingZone = isCreatingZone,
                onDismiss = { beaconViewModel.cancelBeaconConfiguration() },
                onConfirm = { beaconName, zoneName ->
                    beaconViewModel.confirmBeaconConfiguration(beaconName, zoneName)
                },
                onCreateZone = { zoneName ->
                    beaconViewModel.createZone(zoneName)
                }
            )
        }
    }
}

/** Card para mostrar un beacon detectado cercano */
@Composable
private fun NearbyBeaconCard(
    beacon: com.akiestoy.beacons.model.BLEScanLog,
    isFavorite: Boolean,
    onHeartClick: () -> Unit
) {
    val iBeacon = beacon.iBeaconData

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
        shape = RoundedCornerShape(12.dp)
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
                // Nombre
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Nombre:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = beacon.deviceName.ifEmpty {
                            "Beacon ${beacon.macAddress.takeLast(4)}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // MAC Address
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "MAC:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = beacon.macAddress,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black
                    )
                }

                // Mostrar info de iBeacon si está disponible
                if (iBeacon != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Major/Minor:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${iBeacon.major}/${iBeacon.minor}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Distancia (calculada a partir del RSSI)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Distancia:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = calculateDistance(beacon.rssi),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black
                    )
                }
            }

            // Botón de favorito/configurar
            IconButton(onClick = onHeartClick) {
                Icon(
                    imageVector = if (isFavorite) {
                        Icons.Default.Favorite
                    } else {
                        Icons.Default.FavoriteBorder
                    },
                    contentDescription = if (isFavorite) {
                        "Quitar de favoritos"
                    } else {
                        "Configurar beacon"
                    },
                    tint = if (isFavorite) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/**
 * Calcula la distancia aproximada basada en el RSSI
 * @param rssi Valor del RSSI en dBm
 * @return String con la distancia estimada
 */
private fun calculateDistance(rssi: Int): String {
    return when {
        rssi >= -60 -> "< 5m"
        rssi >= -70 -> "5-10m"
        rssi >= -80 -> "10-15m"
        else -> "> 15m"
    }
}

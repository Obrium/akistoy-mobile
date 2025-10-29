package com.akiestoy.beacons.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akiestoy.beacons.model.BLEScanLog
import com.akiestoy.beacons.ui.BeaconViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacketsScreen(viewModel: BeaconViewModel) {
    val scanLogs by viewModel.scanLogs.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    var showOnlyFavorites by remember { mutableStateOf(false) }

    // Filtrar logs según la opción seleccionada
    val filteredLogs = if (showOnlyFavorites) {
        scanLogs.filter { favorites.contains(it.macAddress) }
    } else {
        scanLogs
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header con título y filtro
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "📦 Paquetes BLE",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (showOnlyFavorites) 
                        "${filteredLogs.size} favorito${if (filteredLogs.size != 1) "s" else ""}"
                    else 
                        "${filteredLogs.size} dispositivo${if (filteredLogs.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            
            // Toggle para filtrar favoritos
            FilterChip(
                selected = showOnlyFavorites,
                onClick = { showOnlyFavorites = !showOnlyFavorites },
                label = { Text(if (showOnlyFavorites) "Solo Favoritos" else "Todos") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filtrar",
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (filteredLogs.isEmpty()) {
            // Estado vacío
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (showOnlyFavorites) "No hay paquetes de favoritos" else "No hay paquetes detectados",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (showOnlyFavorites) 
                        "Marca algunos beacons como favoritos en el scanner"
                    else 
                        "Inicia un escaneo para ver paquetes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            // Lista de paquetes
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredLogs, key = { it.macAddress }) { log ->
                    PacketCard(
                        log = log,
                        isFavorite = favorites.contains(log.macAddress)
                    )
                }
            }
        }
    }
}

@Composable
fun PacketCard(
    log: BLEScanLog,
    isFavorite: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isFavorite) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Nombre y favorito
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = log.deviceName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isFavorite)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                if (isFavorite) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "⭐ Favorito",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Información básica
            PacketInfoSection(
                title = "📱 Información Básica",
                content = {
                    PacketInfoRow("MAC Address", log.macAddress)
                    PacketInfoRow("RSSI", "${log.rssi} dBm")
                    log.txPower?.let { PacketInfoRow("TX Power", "$it dBm") }
                    PacketInfoRow("Timestamp", log.getFormattedTimestamp())
                }
            )

            // Manufacturer Data
            if (log.manufacturerData.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                PacketInfoSection(
                    title = "🏭 Manufacturer Data",
                    content = {
                        log.manufacturerData.forEach { (id, data) ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                PacketInfoRow(
                                    "Company ID", 
                                    "0x${id.toString(16).uppercase().padStart(4, '0')}"
                                )
                                Text(
                                    text = "Data: $data",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                                )
                            }
                        }
                    }
                )
            }

            // Service UUIDs
            if (log.serviceUuids.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                PacketInfoSection(
                    title = "🔗 Service UUIDs",
                    content = {
                        log.serviceUuids.forEach { uuid ->
                            Text(
                                text = uuid,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                )
            }

            // iBeacon Data
            log.iBeaconData?.let { ibeacon ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "✅ iBeacon Data",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        PacketInfoRow("UUID", ibeacon.uuid, monospace = true)
                        PacketInfoRow("Major", ibeacon.major.toString())
                        PacketInfoRow("Minor", ibeacon.minor.toString())
                        PacketInfoRow("TX Power", "${ibeacon.txPower} dBm")
                    }
                }
            }
        }
    }
}

@Composable
fun PacketInfoSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun PacketInfoRow(
    label: String,
    value: String,
    monospace: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.weight(0.6f)
        )
    }
}


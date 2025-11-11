package com.akiestoy.beacons.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.akiestoy.beacons.ui.BeaconViewModel

/** Pantalla completa para mostrar los beacons vinculados (favoritos y zonas) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkedBeaconsScreen(
    beaconViewModel: BeaconViewModel,
    zoneViewModel: com.akiestoy.beacons.viewmodel.ZoneViewModel,
    onNavigateBack: () -> Unit
) {
    val favoriteBeacons by beaconViewModel.favoriteBeacons.collectAsState()
    val zones by zoneViewModel.zones.collectAsState()
    
    // Observar todos los beacons detectados en tiempo real
    val allDetectedBeacons by beaconViewModel.scanLogs.collectAsState()

    Scaffold(
            topBar = {
                TopAppBar(
                        title = {
                            Text(
                                    text = "Beacons Vinculados",
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
            }
    ) { paddingValues ->
        if (zones.isEmpty()) {
            // Estado vacío
            Column(
                    modifier =
                            Modifier.fillMaxSize()
                                    .padding(paddingValues)
                                    .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
            ) {
                Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = "Sin zonas",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                        text = "No hay zonas vinculadas",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                        text = "Inicia sesión para sincronizar las zonas de tu empresa",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center
                )
            }
        } else {
            // Lista de zonas vinculadas
            LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(zones, key = { it.id }) { zone ->
                    ZoneCard(
                            zone = zone,
                            detectedBeacons = allDetectedBeacons,
                            onRemove = { 
                                zoneViewModel.deleteZone(zone.id)
                                beaconViewModel.toggleFavorite(zone.id) // Remover también de favoritos
                            }
                    )
                }
            }
        }
    }
}

/** Card para mostrar un beacon vinculado */
@Composable
private fun LinkedBeaconCard(beacon: com.akiestoy.beacons.model.BLEScanLog, onRemove: () -> Unit) {
    OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            colors =
                    CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                    ),
            border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
            shape = RoundedCornerShape(12.dp)
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                            text =
                                    beacon.deviceName.ifEmpty {
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

            // Botón de eliminar
            IconButton(onClick = onRemove) {
                Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar beacon",
                        tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** Card para mostrar una zona vinculada */
@Composable
private fun ZoneCard(
    zone: com.akiestoy.beacons.model.Zone, 
    detectedBeacons: List<com.akiestoy.beacons.model.BLEScanLog>,
    onRemove: () -> Unit
) {
    // Buscar si el beacon de esta zona está siendo detectado
    val detectedBeacon = detectedBeacons.find { beacon ->
        beacon.macAddress == zone.id
    }
    
    // Determinar color de borde basado en si está en alcance
    val borderColor = if (detectedBeacon != null) Color(0xFF4CAF50) else Color(0xFFE0E0E0) // Verde si detectado, gris si no
    val borderWidth = if (detectedBeacon != null) 2.dp else 1.dp
    
    OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            colors =
                    CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                    ),
            border = BorderStroke(borderWidth, borderColor),
            shape = RoundedCornerShape(12.dp)
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
            // Información de la zona
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
                            text = zone.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Black
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ID del Beacon (completo)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                            text = "ID:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                            text = zone.id, // ID completo del beacon
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Tipo
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                            text = "Tipo:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                            text = getZoneTypeName(zone.type),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Black
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Distancia (solo si está en alcance) o Umbrales
                if (detectedBeacon != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                                text = "Distancia:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50) // Verde
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                                text = calculateDistanceInMeters(detectedBeacon.rssi),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                                text = "• En alcance",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF4CAF50)
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                                text = "Estado:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                                text = "Fuera de alcance",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                        )
                    }
                }
            }

            // Botón de eliminar
            IconButton(onClick = onRemove) {
                Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar zona",
                        tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Obtiene el nombre amigable del tipo de zona
 */
private fun getZoneTypeName(type: String): String {
    return when (type.lowercase()) {
        "warehouse" -> "Almacén"
        "entrance" -> "Entrada"
        "office" -> "Oficina"
        "exit" -> "Salida"
        "meeting_room" -> "Sala de reuniones"
        "production" -> "Producción"
        else -> type.replaceFirstChar { it.uppercase() }
    }
}

/**
 * Calcula la distancia en metros basada en el RSSI
 * Usa la fórmula: d = 10 ^ ((txPower - RSSI) / (10 * n))
 * @param rssi Valor del RSSI en dBm
 * @return String con la distancia en metros
 */
private fun calculateDistanceInMeters(rssi: Int): String {
    val txPower = -59 // Potencia de transmisión a 1 metro (valor típico para iBeacon)
    val n = 2.0 // Factor de propagación (2 = espacio libre, 2-4 = interior)
    
    val distance = Math.pow(10.0, (txPower - rssi) / (10.0 * n))
    
    return when {
        distance < 1.0 -> String.format("%.1f m", distance)
        distance < 10.0 -> String.format("%.1f m", distance)
        else -> String.format("%.0f m", distance)
    }
}

/**
 * Calcula la distancia aproximada basada en el RSSI (método alternativo)
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


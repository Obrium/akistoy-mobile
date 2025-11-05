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

/** Pantalla completa para mostrar los beacons vinculados (favoritos) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkedBeaconsScreen(beaconViewModel: BeaconViewModel, onNavigateBack: () -> Unit) {
    val favoriteBeacons by beaconViewModel.favoriteBeacons.collectAsState()

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
        if (favoriteBeacons.isEmpty()) {
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
                        contentDescription = "Sin beacons",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                        text = "No hay beacons vinculados",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                        text = "Marca beacons como favoritos en el scanner para verlos aquí",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center
                )
            }
        } else {
            // Lista de beacons vinculados
            LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(favoriteBeacons, key = { it.macAddress }) { beacon ->
                    LinkedBeaconCard(
                            beacon = beacon,
                            onRemove = { beaconViewModel.toggleFavorite(beacon.macAddress) }
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

                // ID (usando formato de iBeacon si está disponible)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                            text = "ID:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                            text =
                                    beacon.iBeaconData?.let {
                                        "${it.uuid.takeLast(8)}-${it.major}-${it.minor}"
                                    } ?: beacon.macAddress,
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


package com.akiestoy.beacons.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.akiestoy.beacons.state.AppState
import com.akiestoy.beacons.ui.BeaconViewModel

/** Pantalla para conectar manualmente a un beacon favorito */
@Composable
fun ConnectManualScreen(
    beaconViewModel: BeaconViewModel,
    onNavigateBack: () -> Unit
) {
    // Observar beacons favoritos
    val favoriteBeacons by beaconViewModel.favoriteBeacons.collectAsState()
    
    // Observar el beacon actualmente seleccionado
    val manuallySelectedBeaconMac by AppState.manuallySelectedBeaconMac.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        
        // Título
        Text(
            text = "Beacons Favoritos",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (favoriteBeacons.isEmpty()) {
            // Estado vacío
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FavoriteBorder,
                    contentDescription = "Sin beacons",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No hay beacons favoritos",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Agrega beacons a favoritos para conectarte manualmente",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // Lista de beacons favoritos
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(favoriteBeacons, key = { it.macAddress }) { beacon ->
                    FavoriteBeaconConnectionCard(
                        beacon = beacon,
                        isSelected = beacon.macAddress == manuallySelectedBeaconMac,
                        onConnect = {
                            AppState.setManuallySelectedBeacon(beacon.macAddress)
                        }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // Botón Reset (volver a selección automática)
        if (manuallySelectedBeaconMac != null) {
            OutlinedButton(
                onClick = {
                    AppState.clearManuallySelectedBeacon()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Reset - Volver a Automático",
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Botón Volver
        Button(
            onClick = onNavigateBack,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text(
                text = "Volver",
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** Card para mostrar un beacon favorito con botón de conectar */
@Composable
private fun FavoriteBeaconConnectionCard(
    beacon: com.akiestoy.beacons.model.BLEScanLog,
    isSelected: Boolean,
    onConnect: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFE0E0E0)
        ),
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
                        text = beacon.iBeaconData?.let {
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

            Spacer(modifier = Modifier.width(16.dp))

            // Botón de conectar
            Button(
                onClick = onConnect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) {
                        Color(0xFF4CAF50) // Verde cuando está seleccionado
                    } else {
                        MaterialTheme.colorScheme.primary // Azul cuando no está seleccionado
                    }
                ),
                modifier = Modifier.width(120.dp),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp)
            ) {
                Text(
                    text = if (isSelected) "Activo" else "Conectar",
                    style = MaterialTheme.typography.labelMedium
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


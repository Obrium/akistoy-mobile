package com.akiestoy.beacons.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akiestoy.beacons.model.BLEScanLog
import com.akiestoy.beacons.model.Zone

/**
 * Diálogo para configurar un beacon detectado
 * Muestra la información del beacon y permite seleccionar nombre y zona
 */
@Composable
fun ConfigureBeaconDialog(
    scanLog: BLEScanLog,
    zones: List<Zone>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (beaconName: String, zoneName: String) -> Unit
) {
    var beaconName by remember { mutableStateOf("") }
    var selectedZone by remember { mutableStateOf<Zone?>(null) }

    val iBeacon = scanLog.iBeaconData

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Text(
                text = "Configurar Beacon",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Información del beacon detectado
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Información del Beacon",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "MAC: ${scanLog.macAddress}",
                            style = MaterialTheme.typography.bodySmall
                        )

                        if (iBeacon != null) {
                            Text(
                                text = "UUID: ${iBeacon.uuid}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Major: ${iBeacon.major} | Minor: ${iBeacon.minor}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "TX Power: ${iBeacon.txPower} dBm",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Text(
                            text = "RSSI: ${scanLog.rssi} dBm",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Campo para nombre del beacon
                OutlinedTextField(
                    value = beaconName,
                    onValueChange = { beaconName = it },
                    label = { Text("Nombre del Beacon") },
                    placeholder = { Text("Ej: Beacon Sala Reuniones") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading
                )

                // Selector de zona
                Text(
                    text = "Seleccionar Zona",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                if (zones.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            text = "No hay zonas disponibles. Crea zonas primero desde el dashboard.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(zones) { zone ->
                                val isSelected = selectedZone?.id == zone.id
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = isSelected,
                                            enabled = !isLoading,
                                            onClick = { selectedZone = zone }
                                        ),
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = zone.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            Text(
                                                text = "Tipo: ${zone.type}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Seleccionado",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Indicador de carga
                if (isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Configurando beacon...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (beaconName.isNotBlank() && selectedZone != null) {
                        onConfirm(beaconName, selectedZone!!.name)
                    }
                },
                enabled = beaconName.isNotBlank() && selectedZone != null && !isLoading
            ) {
                Text("Configurar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancelar")
            }
        }
    )
}

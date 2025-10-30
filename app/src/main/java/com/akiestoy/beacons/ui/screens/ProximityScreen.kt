package com.akiestoy.beacons.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akiestoy.beacons.service.ProximityForegroundService

/**
 * Pantalla de control del servicio de proximidad
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProximityScreen() {
    val context = LocalContext.current
    var isServiceRunning by remember { mutableStateOf(false) }
    var backendUrl by remember { mutableStateOf("http://tu-servidor.com/") }
    var showUrlDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Servicio de Proximidad") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Estado del servicio
            ServiceStatusCard(isRunning = isServiceRunning)

            // Control del servicio
            ServiceControlCard(
                isRunning = isServiceRunning,
                onStartService = {
                    ProximityForegroundService.startService(context)
                    isServiceRunning = true
                },
                onStopService = {
                    ProximityForegroundService.stopService(context)
                    isServiceRunning = false
                }
            )

            // Configuración
            ConfigurationCard(
                backendUrl = backendUrl,
                onConfigureUrl = { showUrlDialog = true }
            )

            // Información
            InfoCard()
        }
    }

    // Diálogo para configurar URL
    if (showUrlDialog) {
        BackendUrlDialog(
            currentUrl = backendUrl,
            onDismiss = { showUrlDialog = false },
            onConfirm = { newUrl ->
                backendUrl = newUrl
                showUrlDialog = false
            }
        )
    }
}

@Composable
fun ServiceStatusCard(isRunning: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Estado del Servicio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isRunning) "ACTIVO" else "DETENIDO",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (isRunning)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ServiceControlCard(
    isRunning: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Control",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (!isRunning) {
                Button(
                    onClick = onStartService,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Iniciar")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Iniciar Servicio")
                }
            } else {
                Button(
                    onClick = onStopService,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Detener")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Detener Servicio")
                }
            }
        }
    }
}

@Composable
fun ConfigurationCard(
    backendUrl: String,
    onConfigureUrl: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Configuración",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "URL del Backend:",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = backendUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = onConfigureUrl,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Configurar URL")
            }
        }
    }
}

@Composable
fun InfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Información del Servicio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            InfoItem(label = "Modo de escaneo", value = "LOW_LATENCY")
            InfoItem(label = "Umbral ENTER", value = "-65 dBm")
            InfoItem(label = "Umbral EXIT", value = "-70 dBm")
            InfoItem(label = "Timeout EXIT", value = "10 segundos")
            InfoItem(label = "Intervalo Heartbeat", value = "5 segundos")
            InfoItem(label = "Ventana promedio RSSI", value = "5 muestras")

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "Eventos enviados al backend:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "• ENTER: Cuando el beacon entra en proximidad\n" +
                        "• EXIT: Cuando sale o no se detecta por 10s\n" +
                        "• HEARTBEAT: Cada 5s si sigue en proximidad",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun InfoItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun BackendUrlDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var url by remember { mutableStateOf(currentUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar URL del Backend") },
        text = {
            Column {
                Text("Ingresa la URL base del backend:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    placeholder = { Text("http://ejemplo.com/") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url) }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

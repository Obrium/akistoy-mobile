package com.akiestoy.beacons.ui.screens

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
import com.akiestoy.beacons.data.FavoritesRepository
import com.akiestoy.beacons.service.ProximityForegroundService

/** Pantalla de control del servicio de proximidad */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProximityScreen() {
    val context = LocalContext.current
    val favoritesRepository = remember { FavoritesRepository(context) }
    val favorites by favoritesRepository.favorites.collectAsState()

    var isServiceRunning by remember { mutableStateOf(false) }
    var backendUrl by remember { mutableStateOf("https://djaxfn1a2gzj8.cloudfront.net/") }
    var showUrlDialog by remember { mutableStateOf(false) }

    Scaffold(
            topBar = {
                TopAppBar(
                        title = { Text("Servicio de Proximidad") },
                        colors =
                                TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        titleContentColor =
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                )
                )
            }
    ) { paddingValues ->
        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .padding(paddingValues)
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Advertencia si no hay favoritos
            if (favorites.isEmpty()) {
                Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                                text = "⚠️ No hay beacons favoritos",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                                text =
                                        "Debes agregar beacons a favoritos antes de iniciar el servicio. Ve a la pestaña Scanner y marca beacons como favoritos.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Estado del servicio
            ServiceStatusCard(isRunning = isServiceRunning, favoritesCount = favorites.size)

            // Control del servicio
            ServiceControlCard(
                    isRunning = isServiceRunning,
                    hasFavorites = favorites.isNotEmpty(),
                    onStartService = {
                        if (favorites.isNotEmpty()) {
                            ProximityForegroundService.startService(context)
                            isServiceRunning = true
                        }
                    },
                    onStopService = {
                        ProximityForegroundService.stopService(context)
                        isServiceRunning = false
                    }
            )

            // Configuración
            ConfigurationCard(backendUrl = backendUrl, onConfigureUrl = { showUrlDialog = true })

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
fun ServiceStatusCard(isRunning: Boolean, favoritesCount: Int) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                    CardDefaults.cardColors(
                            containerColor =
                                    if (isRunning) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                    )
    ) {
        Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                    color =
                            if (isRunning) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (favoritesCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                        text = "📌 Monitoreando $favoritesCount beacon(s) favorito(s)",
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                                if (isRunning) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ServiceControlCard(
        isRunning: Boolean,
        hasFavorites: Boolean,
        onStartService: () -> Unit,
        onStopService: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                        modifier = Modifier.fillMaxWidth(),
                        enabled = hasFavorites
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Iniciar")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Iniciar Servicio")
                }
                if (!hasFavorites) {
                    Text(
                            text = "Agrega beacons a favoritos primero",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Button(
                        onClick = onStopService,
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                                ButtonDefaults.buttonColors(
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
fun ConfigurationCard(backendUrl: String, onConfigureUrl: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                    text = "Configuración",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
            )

            Text(text = "URL del Backend:", style = MaterialTheme.typography.bodyMedium)
            Text(
                    text = backendUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(onClick = onConfigureUrl, modifier = Modifier.fillMaxWidth()) {
                Text("Configurar URL")
            }
        }
    }
}

@Composable
fun InfoCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                    text = "Información del Servicio",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
            )

            InfoItem(label = "Modo de escaneo", value = "LOW_LATENCY")
            InfoItem(label = "Verificación señal", value = "Cada 2 segundos")
            InfoItem(label = "Timeout EXIT", value = "2 minutos")
            InfoItem(label = "Intervalo Heartbeat", value = "60 segundos")

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                    text = "Estados del sistema:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
            )
            Text(
                    text =
                            "• OUTSIDE: Fuera del recinto\n" +
                                    "• ENTERING: Detectó primer beacon\n" +
                                    "• INSIDE: Dentro del recinto (heartbeat activo)\n" +
                                    "• EXITING: Señal perdida, esperando 2min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                    text = "Eventos enviados al backend:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
            )
            Text(
                    text =
                            "• beacon-reading: Cada detección de beacon\n" +
                                    "• IMPLICIT_ENTRY: Al confirmar entrada (2do beacon)\n" +
                                    "• IMPLICIT_EXIT: Al confirmar salida (2min sin señal)\n" +
                                    "• heartbeat: Cada 60s si está INSIDE",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun InfoItem(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = "$label:", style = MaterialTheme.typography.bodyMedium)
        Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun BackendUrlDialog(currentUrl: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
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
            confirmButton = { TextButton(onClick = { onConfirm(url) }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

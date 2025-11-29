package com.akiestoy.beacons.ui.screens

import android.app.Application
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.akiestoy.beacons.data.AppDatabase
import com.akiestoy.beacons.model.RegisteredBeacon
import com.akiestoy.beacons.ui.BeaconViewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

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

    // Observar todos los beacons detectados en tiempo real (únicos, no todos los paquetes)
    val allDetectedBeacons by beaconViewModel.uniqueDevices.collectAsState()

    // Obtener beacons registrados de la base de datos
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context.applicationContext as Application) }
    var registeredBeacons by remember { mutableStateOf<List<RegisteredBeacon>>(emptyList()) }

    // Cargar beacons registrados al inicio (solo los que tienen MAC)
    LaunchedEffect(Unit) {
        launch {
            val allBeacons = database.registeredBeaconDao().getAllBeaconsOnce()
            // Filtrar solo beacons que tienen MAC registrada
            registeredBeacons = allBeacons.filter { !it.mac.isNullOrEmpty() }
            android.util.Log.i("LinkedBeaconsScreen", "📋 Cargados ${registeredBeacons.size} beacons con MAC de ${allBeacons.size} totales")
            registeredBeacons.forEach { beacon ->
                android.util.Log.d("LinkedBeaconsScreen", "   📍 ${beacon.beaconName ?: beacon.zoneName} - MAC: ${beacon.mac} - UUID: ${beacon.advUuid}")
            }
        }
    }

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
        if (registeredBeacons.isEmpty()) {
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
                        text = "Inicia sesión para sincronizar los beacons de tu empresa",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center
                )
            }
        } else {
            // Lista de beacons vinculados
            val scope = rememberCoroutineScope()
            LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(registeredBeacons, key = { it.id }) { beacon ->
                    RegisteredBeaconCard(
                            beacon = beacon,
                            detectedBeacons = allDetectedBeacons,
                            onRemove = {
                                scope.launch {
                                    // Eliminar de la base de datos local
                                    database.registeredBeaconDao().deleteBeacon(beacon)
                                    // Actualizar la lista local
                                    registeredBeacons = registeredBeacons.filter { it.id != beacon.id }
                                    android.util.Log.i("LinkedBeaconsScreen", "🗑️ Beacon eliminado: ${beacon.beaconName ?: beacon.zoneName}")
                                }
                            }
                    )
                }
            }
        }
    }
}

/** Card para mostrar un beacon registrado */
@Composable
private fun RegisteredBeaconCard(
    beacon: RegisteredBeacon,
    detectedBeacons: List<com.akiestoy.beacons.model.BLEScanLog>,
    onRemove: () -> Unit
) {
    // Buscar si este beacon está siendo detectado
    // PRIORIDAD 1: Match por MAC address (más confiable)
    // PRIORIDAD 2: Match por UUID + major + minor
    val detectedBeacon = detectedBeacons.find { detected ->
        // Primero intentar match por MAC
        if (!beacon.mac.isNullOrEmpty() && detected.macAddress.equals(beacon.mac, ignoreCase = true)) {
            return@find true
        }
        // Si no hay MAC, usar UUID + major + minor
        val iBeacon = detected.iBeaconData ?: return@find false
        iBeacon.uuid.lowercase() == beacon.advUuid.lowercase() &&
                iBeacon.major == beacon.major &&
                iBeacon.minor == beacon.minor
    }

    // Determinar color de borde basado en si está en alcance
    val borderColor = if (detectedBeacon != null) Color(0xFF4CAF50) else Color(0xFFE0E0E0)
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
            // Información del beacon
            Column(modifier = Modifier.weight(1f)) {
                // Nombre del beacon
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                            text = "Nombre:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                            text = beacon.beaconName ?: beacon.zoneName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Black
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // MAC Address (si existe)
                if (!beacon.mac.isNullOrEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                                text = "MAC:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                                text = beacon.mac,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Black
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Zona
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                            text = "Zona:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                            text = beacon.zoneName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Black
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Estado y distancia
                if (detectedBeacon != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                                text = "Distancia:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
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
    registeredBeacons: List<RegisteredBeacon>,
    onRemove: () -> Unit
) {
    // Obtener los beacons registrados para esta zona
    val zoneBeacons = registeredBeacons.filter { it.zoneId == zone.id }

    // Buscar si algún beacon de esta zona está siendo detectado
    // Comparar por UUID + major + minor para identificación precisa
    val detectedBeacon = detectedBeacons.find { detected ->
        val iBeacon = detected.iBeaconData ?: return@find false
        // Buscar si hay algún beacon registrado de esta zona con el mismo UUID + major + minor
        val found = zoneBeacons.any { registered ->
            val match = iBeacon.uuid.lowercase() == registered.advUuid.lowercase() &&
                       iBeacon.major == registered.major &&
                       iBeacon.minor == registered.minor
            android.util.Log.d("LinkedBeaconsScreen", "🔍 Comparando beacon: detected=${iBeacon.uuid}:${iBeacon.major}:${iBeacon.minor} vs registered=${registered.advUuid}:${registered.major}:${registered.minor} (zona=${zone.name}) -> match=$match")
            match
        }
        android.util.Log.d("LinkedBeaconsScreen", "📊 Zona ${zone.name}: ${zoneBeacons.size} beacons registrados, found=$found")
        found
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


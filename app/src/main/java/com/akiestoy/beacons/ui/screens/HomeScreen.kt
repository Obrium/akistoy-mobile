package com.akiestoy.beacons.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.akiestoy.beacons.data.AppDatabase
import com.akiestoy.beacons.model.BLEScanLog
import com.akiestoy.beacons.model.RegisteredBeacon
import com.akiestoy.beacons.state.ApiEventLog
import com.akiestoy.beacons.state.AppState
import com.akiestoy.beacons.state.CurrentZoneState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.akiestoy.beacons.ui.BeaconViewModel
import com.akiestoy.beacons.ui.components.PermissionStatusCard
import com.akiestoy.beacons.ui.components.SuperAdminDialog
import com.akiestoy.beacons.viewmodel.SuperAdminViewModel
import com.akiestoy.beacons.viewmodel.UserRegistrationViewModel
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import com.akiestoy.beacons.BuildConfig

/** Pantalla principal de Home Muestra el estado de conexión con beacons favoritos */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
        userViewModel: UserRegistrationViewModel,
        beaconViewModel: BeaconViewModel,
        superAdminViewModel: SuperAdminViewModel,
        onRequestPermissions: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentUser by userViewModel.currentUser.collectAsState()
    val favoriteBeacons by beaconViewModel.favoriteBeacons.collectAsState()

    // Observar el estado de autenticación del super admin
    val isSuperAdminAuthenticated by superAdminViewModel.isAuthenticated.collectAsState()

    // Estado para mostrar el dialog de super admin
    var showSuperAdminDialog by remember { mutableStateOf(false) }

    // Estado para mostrar el dialog de confirmación de logout
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }

    // Estado para pull-to-refresh
    var isRefreshing by remember { mutableStateOf(false) }

    // Estado para forzar recomposición cada 2 segundos
    var updateTrigger by remember { mutableStateOf(0) }

    // Observar el beacon seleccionado manualmente
    val manuallySelectedBeaconMac by AppState.manuallySelectedBeaconMac.collectAsState()

    // Observar estado de zona desde AppState (actualizado ANTES de enviar al backend)
    val zoneState by AppState.zoneState.collectAsState()

    // Observar logs de API desde AppState
    val apiLogs by AppState.apiLogs.collectAsState()

    // Estado para beacons registrados (para obtener zona)
    var registeredBeacons by remember { mutableStateOf<List<RegisteredBeacon>>(emptyList()) }

    // Cargar beacons registrados al inicio (solo los que tienen MAC)
    LaunchedEffect(Unit) {
        try {
            val db = AppDatabase.getDatabase(context)
            val allBeacons = db.registeredBeaconDao().getAllActiveBeaconsOnce()
            // Filtrar solo beacons que tienen MAC registrada
            registeredBeacons = allBeacons.filter { !it.mac.isNullOrEmpty() }
        } catch (e: Exception) {
            android.util.Log.e("HomeScreen", "Error cargando beacons registrados", e)
        }
    }

    // Sincronizar beacons del servidor periódicamente (cada 5 minutos)
    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            while (true) {
                // Refrescar zonas y beacons del servidor
                android.util.Log.i("HomeScreen", "🔄 Sincronizando beacons del servidor...")
                userViewModel.refreshZones()

                // Recargar beacons locales después de la sincronización
                try {
                    val db = AppDatabase.getDatabase(context)
                    registeredBeacons = db.registeredBeaconDao().getAllActiveBeaconsOnce()
                    android.util.Log.i("HomeScreen", "✅ Beacons actualizados: ${registeredBeacons.size}")
                } catch (e: Exception) {
                    android.util.Log.e("HomeScreen", "Error recargando beacons", e)
                }

                // Esperar 5 minutos antes del siguiente refresh
                delay(5 * 60 * 1000)
            }
        }
    }

    // Estado persistente para evitar parpadeos entre ACTIVO/INACTIVO
    // Mantiene el último estado activo conocido durante un "grace period" de 25 segundos
    var lastKnownActiveState by remember { mutableStateOf<ConnectionState?>(null) }

    // PRIORIDAD: Usar zoneState de AppState (actualizado por ZoneEventService ANTES de enviar al backend)
    // Esto asegura que la UI muestre el estado correcto inmediatamente
    val connectionState = remember(zoneState, favoriteBeacons, updateTrigger, registeredBeacons) {
        val currentTime = System.currentTimeMillis()

        // Si zoneState de AppState indica una zona activa, usarla como fuente principal
        if (zoneState.isInsideCompany && zoneState.zoneName != null) {
            val timeSinceUpdate = currentTime - zoneState.lastUpdate
            val lastSeenSeconds = (timeSinceUpdate / 1000).toInt().coerceAtMost(25)

            val state = ConnectionState(
                isActive = true,
                activeBeaconName = zoneState.zoneName,
                zoneName = zoneState.zoneName,
                rssi = zoneState.rssi,
                lastSeenSeconds = lastSeenSeconds,
                timestamp = zoneState.lastUpdate,
                beaconMac = zoneState.beaconMac
            )
            lastKnownActiveState = state
            state
        } else {
            // Fallback: usar cálculo basado en favoriteBeacons
            val newState = calculateConnectionState(favoriteBeacons, registeredBeacons)

            if (newState.isActive) {
                lastKnownActiveState = newState
                newState
            } else {
                // Verificar grace period
                val lastActive = lastKnownActiveState
                if (lastActive != null) {
                    val timeSinceLastActive = currentTime - lastActive.timestamp

                    if (timeSinceLastActive < 25_000) {
                        lastActive.copy(
                            lastSeenSeconds = (timeSinceLastActive / 1000).toInt()
                        )
                    } else {
                        lastKnownActiveState = null
                        newState
                    }
                } else {
                    newState
                }
            }
        }
    }

    // Iniciar escaneo automático DESPUÉS de que el usuario esté autenticado
    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            // Pequeño delay para asegurar que la UI esté lista
            delay(500)
            // Iniciar el escaneo automático de beacons registrados
            // Se conectará automáticamente al beacon más cercano
            beaconViewModel.startAutoScanning()
        }
    }

    // Loop separado para actualizar la UI cada 2 segundos (sin reiniciar el escaneo)
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000) // 2 segundos
            updateTrigger++ // Incrementar trigger para forzar recálculo de la UI
        }
    }

    // Mostrar dialog de super admin si está activo
    if (showSuperAdminDialog) {
        SuperAdminDialog(
                onDismiss = { showSuperAdminDialog = false },
                onLogin = { username, password ->
                    val success = superAdminViewModel.login(username, password)
                    if (success) {
                        showSuperAdminDialog = false
                    }
                    success // Retornar el resultado
                }
        )
    }

    // Mostrar dialog de confirmación de logout
    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            title = { Text("Cerrar Sesión") },
            text = { Text("¿Estás seguro que deseas cerrar sesión? Deberás ingresar tu RUT nuevamente para volver a iniciar sesión.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirmDialog = false
                        userViewModel.clearUser()
                        beaconViewModel.stopScanning()
                    }
                ) {
                    Text("Cerrar Sesión", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { isRefreshing = true },
                modifier = Modifier.fillMaxSize()
        ) {
            // Efecto para manejar la actualización manual (solo refresca UI, no reinicia escaneo)
            LaunchedEffect(isRefreshing) {
                if (isRefreshing) {
                    // Esperar 1 segundo para mostrar el spinner
                    delay(1000)
                    updateTrigger++ // Forzar recálculo de la UI
                    isRefreshing = false
                }
            }

            Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top,
                    modifier =
                            Modifier.fillMaxWidth()
                                    .padding(24.dp)
                                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(40.dp))

                // Título "Bienvenido" + nombre
                Text(
                        text = "Bienvenido",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Nombre del usuario
                Text(
                        text = currentUser?.name ?: "",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Texto "ESTADO:"
                Text(
                        text = "ESTADO:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Rectángulo con el estado
                Card(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                if (connectionState.isActive) {
                                                    Color(0xFF90EE90) // Verde claro
                                                } else {
                                                    Color.Red
                                                }
                                )
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                                text = if (connectionState.isActive) "ACTIVO" else "INACTIVO",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color =
                                        if (connectionState.isActive) {
                                            Color.Black
                                        } else {
                                            Color.White
                                        }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Panel de estado de permisos y servicio
                PermissionStatusCard(
                    onRequestPermissions = onRequestPermissions
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Zona actual y beacon conectado (si está activo)
                if (connectionState.isActive && connectionState.activeBeaconName != null) {
                    // Tarjeta de ZONA
                    Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.primaryContainer
                                    )
                    ) {
                        Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                    text = "ZONA ACTUAL",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                    text = connectionState.zoneName ?: connectionState.activeBeaconName,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    textAlign = TextAlign.Center
                            )

                            // Mostrar tiempo desde última señal
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Última señal: ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = if (connectionState.lastSeenSeconds <= 0) "ahora"
                                           else "hace ${connectionState.lastSeenSeconds}s",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            // RSSI
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Señal: ${connectionState.rssi ?: "?"} dBm",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )

                            // MAC del beacon (para pruebas)
                            connectionState.beaconMac?.let { mac ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "MAC: $mac",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Tenant ID del usuario
                currentUser?.let { user ->
                    Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.tertiaryContainer
                                    )
                    ) {
                        Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                    text = "Tenant ID:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                    text = user.tenantId,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Logs de API (debug) - Solo últimos 2 eventos
                if (apiLogs.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "API Events",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                // Mostrar RUT del usuario
                                currentUser?.rut?.let { rut ->
                                    Text(
                                        text = "RUT: $rut",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            // Mostrar solo los últimos 2 eventos
                            apiLogs.take(2).forEach { log ->
                                ApiLogItem(log = log)
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }

                // Versión de la app (al final)
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Botón de cerrar sesión del usuario (esquina superior izquierda)
        if (currentUser != null) {
            IconButton(
                onClick = { showLogoutConfirmDialog = true },
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Cerrar sesión",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Botón de Super Admin (esquina superior derecha)
        IconButton(
                onClick = {
                    if (isSuperAdminAuthenticated) {
                        // Si está autenticado, cerrar sesión
                        superAdminViewModel.logout()
                    } else {
                        // Si no está autenticado, mostrar dialog de login
                        showSuperAdminDialog = true
                    }
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(
                    imageVector = if (isSuperAdminAuthenticated) {
                        Icons.AutoMirrored.Filled.Logout
                    } else {
                        Icons.Default.Settings
                    },
                    contentDescription = if (isSuperAdminAuthenticated) {
                        "Cerrar sesión SuperAdmin"
                    } else {
                        "Super Admin"
                    },
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
            )
        }
    }
}

/** Estado de conexión con beacons */
data class ConnectionState(
        val isActive: Boolean,
        val activeBeaconName: String? = null,
        val zoneName: String? = null,
        val rssi: Int? = null,
        val lastSeenSeconds: Int = 0,
        val timestamp: Long = System.currentTimeMillis(), // Timestamp de la última detección activa
        val beaconMac: String? = null // MAC del beacon actual para histéresis
)

/**
 * Estado de RSSI suavizado por beacon (para histéresis de zona)
 * Usa EMA (Exponential Moving Average) igual que ZoneManager
 */
data class SmoothedBeaconState(
    val mac: String,
    var zoneName: String,  // Mutable para actualizar si cambia en el servidor
    var smoothedRssi: Double = -100.0,
    var lastTimestamp: Long = 0L,
    var sampleCount: Int = 0
) {
    companion object {
        const val EMA_ALPHA = 0.5 // Factor de suavizado
    }

    fun updateRssi(newRssi: Int, timestamp: Long) {
        lastTimestamp = timestamp
        sampleCount++
        smoothedRssi = if (sampleCount == 1) {
            newRssi.toDouble()
        } else {
            EMA_ALPHA * newRssi + (1 - EMA_ALPHA) * smoothedRssi
        }
    }

    fun isActive(currentTime: Long): Boolean {
        return (currentTime - lastTimestamp) < 15_000 // 15 segundos de ventana activa
    }
}

// Estado global de RSSI suavizado por beacon (persiste entre recomposiciones)
private val smoothedBeaconStates = mutableMapOf<String, SmoothedBeaconState>()

// Zona actualmente confirmada (con histéresis)
private var confirmedZoneMac: String? = null

/**
 * Calcula el estado de conexión basado en beacons detectados
 * PRIORIDAD: Match por MAC address (más confiable que UUID que puede repetirse)
 *
 * Implementa:
 * - Suavizado EMA de RSSI para cada beacon
 * - Histéresis de 6 dB para cambiar de zona
 * - Ventana de 15 segundos para beacons activos
 */
private fun calculateConnectionState(
    favoriteBeacons: List<BLEScanLog>,
    registeredBeacons: List<RegisteredBeacon>
): ConnectionState {
    val currentTime = System.currentTimeMillis()

    if (favoriteBeacons.isEmpty()) {
        return ConnectionState(isActive = false)
    }

    // Actualizar RSSI suavizado SOLO para beacons que tienen MAC registrada
    for (beacon in favoriteBeacons) {
        val mac = beacon.macAddress
        if (mac.isEmpty()) continue

        // Buscar zona registrada por MAC (SOLO procesar si hay match)
        val registered = registeredBeacons.find { reg ->
            !reg.mac.isNullOrEmpty() && reg.mac.equals(mac, ignoreCase = true)
        }

        // Si no hay beacon registrado con esta MAC, ignorar (evita "iBeacon" genéricos)
        if (registered == null) continue

        val zoneName = registered.zoneName

        val state = smoothedBeaconStates.getOrPut(mac) {
            SmoothedBeaconState(mac = mac, zoneName = zoneName)
        }
        // Actualizar zoneName en caso de que haya cambiado en el servidor
        if (state.zoneName != zoneName) {
            state.zoneName = zoneName
        }
        state.updateRssi(beacon.rssi, beacon.timestamp)
    }

    // Filtrar solo beacons activos (señal reciente)
    val activeBeacons = smoothedBeaconStates.values.filter { it.isActive(currentTime) }

    if (activeBeacons.isEmpty()) {
        confirmedZoneMac = null
        return ConnectionState(isActive = false)
    }

    // Encontrar el beacon con mejor RSSI suavizado
    val closestBeacon = activeBeacons.maxByOrNull { it.smoothedRssi } ?: return ConnectionState(isActive = false)

    // Implementar HISTÉRESIS: solo cambiar de zona si el nuevo beacon es significativamente más fuerte
    val HYSTERESIS_DB = 6.0
    val currentConfirmedMac = confirmedZoneMac

    val finalBeacon: SmoothedBeaconState = if (currentConfirmedMac != null) {
        val currentZoneBeacon = activeBeacons.find { it.mac.equals(currentConfirmedMac, ignoreCase = true) }

        if (currentZoneBeacon != null && currentZoneBeacon.isActive(currentTime)) {
            // La zona actual sigue activa, verificar si otra zona es significativamente más fuerte
            val rssiDifference = closestBeacon.smoothedRssi - currentZoneBeacon.smoothedRssi

            if (rssiDifference > HYSTERESIS_DB) {
                // El nuevo beacon supera la histéresis, cambiar zona
                android.util.Log.i("HomeScreen", "🔄 Cambio de zona: ${currentZoneBeacon.zoneName} → ${closestBeacon.zoneName} (diff: +${rssiDifference.toInt()} dB)")
                confirmedZoneMac = closestBeacon.mac
                closestBeacon
            } else {
                // Mantener zona actual (no supera histéresis)
                currentZoneBeacon
            }
        } else {
            // La zona actual ya no está activa, usar la más cercana
            confirmedZoneMac = closestBeacon.mac
            closestBeacon
        }
    } else {
        // No hay zona confirmada, usar la más cercana
        confirmedZoneMac = closestBeacon.mac
        closestBeacon
    }

    val lastSeenSeconds = ((currentTime - finalBeacon.lastTimestamp) / 1000).toInt()

    return ConnectionState(
        isActive = true,
        activeBeaconName = finalBeacon.zoneName,
        zoneName = finalBeacon.zoneName,
        rssi = finalBeacon.smoothedRssi.toInt(),
        lastSeenSeconds = lastSeenSeconds,
        timestamp = finalBeacon.lastTimestamp,
        beaconMac = finalBeacon.mac
    )
}

/**
 * Composable para mostrar un item de log de API
 */
@Composable
private fun ApiLogItem(log: ApiEventLog) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = timeFormat.format(Date(log.timestamp))

    // Color según status
    val statusColor = when (log.status) {
        "SUCCESS" -> Color(0xFF4CAF50) // Verde
        "ERROR" -> Color(0xFFF44336) // Rojo
        "SENDING" -> Color(0xFF2196F3) // Azul
        "SKIPPED" -> Color(0xFFFF9800) // Naranja
        else -> Color.Gray
    }

    // Icono según tipo de evento
    val eventIcon = when (log.eventType) {
        "COMPANY_ENTRY" -> "🚪"
        "COMPANY_EXIT" -> "🚶"
        "ZONE_CHANGE" -> "🔄"
        "STAY" -> "💚"
        else -> "📡"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Hora
        Text(
            text = timeStr,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.width(60.dp)
        )

        // Icono y tipo de evento
        Text(
            text = "$eventIcon ${log.eventType}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )

        // Status con color
        Text(
            text = log.status,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = statusColor
        )
    }

    // Zona y mensaje
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 60.dp)
    ) {
        Text(
            text = buildString {
                append(log.zoneName)
                log.fromZone?.let { append(" (desde: $it)") }
                log.message?.let { append(" - $it") }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            maxLines = 1
        )
    }
}


package com.akiestoy.beacons.ui.components

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.akiestoy.beacons.service.ProximityForegroundService
import com.akiestoy.beacons.utils.BatteryOptimizationHelper
import kotlinx.coroutines.delay

/**
 * Estado de un permiso individual
 */
data class PermissionState(
    val name: String,
    val description: String,
    val isGranted: Boolean,
    val icon: ImageVector,
    val isCritical: Boolean = true,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

/**
 * Tarjeta que muestra el estado de todos los permisos necesarios
 */
@Composable
fun PermissionStatusCard(
    modifier: Modifier = Modifier,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var updateTrigger by remember { mutableStateOf(0) }

    // Actualizar estado cada 2 segundos
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            updateTrigger++
        }
    }

    // Calcular estados de permisos
    val permissionStates = remember(updateTrigger) {
        getPermissionStates(context)
    }

    // Estado del servicio
    val isServiceRunning = remember(updateTrigger) {
        ProximityForegroundService.isServiceRunning(context)
    }

    // Bluetooth activado
    val isBluetoothEnabled = remember(updateTrigger) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothManager?.adapter?.isEnabled == true
    }

    // Contar permisos
    val grantedCount = permissionStates.count { it.isGranted }
    val totalCount = permissionStates.size
    val allGranted = grantedCount == totalCount && isServiceRunning && isBluetoothEnabled

    // Color según estado
    val cardColor = when {
        allGranted -> Color(0xFF4CAF50).copy(alpha = 0.15f)
        grantedCount >= totalCount - 1 -> Color(0xFFFF9800).copy(alpha = 0.15f)
        else -> Color(0xFFF44336).copy(alpha = 0.15f)
    }

    val statusIcon = when {
        allGranted -> Icons.Default.CheckCircle
        else -> Icons.Default.Warning
    }

    val statusColor = when {
        allGranted -> Color(0xFF4CAF50)
        grantedCount >= totalCount - 1 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header - clickable para expandir/colapsar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Estado del Sistema",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (allGranted) "Todo funcionando correctamente"
                                   else "$grantedCount/$totalCount permisos activos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Colapsar" else "Expandir",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // Contenido expandido
            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Estado del servicio
                PermissionRow(
                    name = "Servicio de escaneo",
                    description = if (isServiceRunning) "Activo en segundo plano" else "Detenido",
                    isGranted = isServiceRunning,
                    icon = Icons.Default.PlayArrow,
                    actionLabel = if (!isServiceRunning) "Iniciar" else null,
                    onAction = if (!isServiceRunning) {
                        { ProximityForegroundService.startService(context) }
                    } else null
                )

                // Bluetooth
                PermissionRow(
                    name = "Bluetooth",
                    description = if (isBluetoothEnabled) "Activado" else "Desactivado",
                    isGranted = isBluetoothEnabled,
                    icon = Icons.Default.Bluetooth,
                    actionLabel = if (!isBluetoothEnabled) "Activar" else null,
                    onAction = if (!isBluetoothEnabled) {
                        {
                            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                        }
                    } else null
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Permisos",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Lista de permisos
                permissionStates.forEach { permission ->
                    PermissionRow(
                        name = permission.name,
                        description = permission.description,
                        isGranted = permission.isGranted,
                        icon = permission.icon,
                        actionLabel = permission.actionLabel,
                        onAction = permission.onAction
                    )
                }

                // Botón para abrir configuración si hay permisos faltantes
                if (!allGranted) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRequestPermissions,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Solicitar permisos")
                        }
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Configuración")
                        }
                    }
                }

                // Info del fabricante si tiene optimizaciones especiales
                val manufacturerInfo = BatteryOptimizationHelper.getManufacturerInfo()
                if (manufacturerInfo.hasSpecialSettings) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Dispositivo ${manufacturerInfo.name}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Este dispositivo requiere configuración adicional para que la app funcione en segundo plano.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = { BatteryOptimizationHelper.openBatterySettings(context) }
                            ) {
                                Text("Ver instrucciones")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    name: String,
    description: String,
    isGranted: Boolean,
    icon: ImageVector,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isGranted) Color(0xFF4CAF50) else Color(0xFFF44336),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        if (isGranted) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Concedido",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(20.dp)
            )
        } else if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, style = MaterialTheme.typography.labelSmall)
            }
        } else {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "No concedido",
                tint = Color(0xFFF44336),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Obtiene el estado de todos los permisos necesarios
 */
private fun getPermissionStates(context: Context): List<PermissionState> {
    val states = mutableListOf<PermissionState>()

    // Permiso de ubicación fina
    states.add(
        PermissionState(
            name = "Ubicación precisa",
            description = if (hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION))
                "Permite detectar beacons cercanos"
            else "Necesario para escaneo BLE",
            isGranted = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION),
            icon = Icons.Default.LocationOn,
            isCritical = true
        )
    )

    // Permisos de Bluetooth (Android 12+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        states.add(
            PermissionState(
                name = "Escaneo Bluetooth",
                description = if (hasPermission(context, Manifest.permission.BLUETOOTH_SCAN))
                    "Permite buscar beacons"
                else "Necesario para detectar beacons",
                isGranted = hasPermission(context, Manifest.permission.BLUETOOTH_SCAN),
                icon = Icons.Default.BluetoothSearching,
                isCritical = true
            )
        )

        states.add(
            PermissionState(
                name = "Conexión Bluetooth",
                description = if (hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT))
                    "Permite conectar con beacons"
                else "Necesario para comunicación BLE",
                isGranted = hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT),
                icon = Icons.Default.BluetoothConnected,
                isCritical = true
            )
        )
    }

    // Optimización de batería
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val isIgnoringBatteryOptimization = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } else true

    states.add(
        PermissionState(
            name = "Sin optimización de batería",
            description = if (isIgnoringBatteryOptimization)
                "La app no será suspendida"
            else "Android puede detener la app",
            isGranted = isIgnoringBatteryOptimization,
            icon = Icons.Default.BatteryAlert,
            isCritical = true,
            actionLabel = if (!isIgnoringBatteryOptimization) "Configurar" else null,
            onAction = if (!isIgnoringBatteryOptimization) {
                {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    }
                }
            } else null
        )
    )

    return states
}

private fun hasPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

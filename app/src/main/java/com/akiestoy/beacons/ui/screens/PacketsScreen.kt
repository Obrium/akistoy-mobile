package com.akiestoy.beacons.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
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
    var selectedDevice by remember { mutableStateOf<String?>(null) }
    var showDeviceSelector by remember { mutableStateOf(false) }

    // Obtener lista de dispositivos únicos (tomar el más reciente de cada MAC)
    val uniqueDevices = scanLogs
        .groupBy { it.macAddress }
        .map { (_, logs) -> logs.first() } // El primero es el más reciente
        .sortedByDescending { it.timestamp }

    // Filtrar logs según las opciones seleccionadas
    val filteredLogs = scanLogs.filter { log ->
        val matchesFavorites = !showOnlyFavorites || favorites.contains(log.macAddress)
        val matchesDevice = selectedDevice == null || log.macAddress == selectedDevice
        matchesFavorites && matchesDevice
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "📦 Paquetes BLE",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (showOnlyFavorites) 
                        "${filteredLogs.size} paquete${if (filteredLogs.size != 1) "s" else ""} de favoritos"
                    else 
                        "${filteredLogs.size} paquete${if (filteredLogs.size != 1) "s" else ""} detectados",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            
            // Botones de acción
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Toggle para filtrar favoritos
                FilterChip(
                    selected = showOnlyFavorites,
                    onClick = { showOnlyFavorites = !showOnlyFavorites },
                    label = { Text(if (showOnlyFavorites) "Favoritos" else "Todos") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filtrar",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                
                // Botón para seleccionar dispositivo
                if (uniqueDevices.isNotEmpty()) {
                    FilterChip(
                        selected = selectedDevice != null,
                        onClick = { showDeviceSelector = true },
                        label = { Text("Dispositivo") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Devices,
                                contentDescription = "Filtrar dispositivo",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
                
                // Botón para limpiar logs
                if (scanLogs.isNotEmpty()) {
                    FilledTonalIconButton(
                        onClick = { viewModel.clearLogs() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Limpiar logs"
                        )
                    }
                }
            }
        }

        // Chip de dispositivo seleccionado
        if (selectedDevice != null) {
            Spacer(modifier = Modifier.height(8.dp))
            val deviceName = uniqueDevices.find { it.macAddress == selectedDevice }?.deviceName ?: "Desconocido"
            AssistChip(
                onClick = { selectedDevice = null },
                label = { 
                    Text("Filtrando: $deviceName (${selectedDevice?.take(17)})")
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Limpiar filtro",
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        // Diálogo de selección de dispositivo
        if (showDeviceSelector) {
            AlertDialog(
                onDismissRequest = { showDeviceSelector = false },
                title = { Text("Seleccionar Dispositivo") },
                text = {
                    LazyColumn {
                        items(
                            items = uniqueDevices,
                            key = { device -> device.macAddress } // MAC address es única por dispositivo
                        ) { device ->
                            TextButton(
                                onClick = {
                                    selectedDevice = device.macAddress
                                    showDeviceSelector = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        text = device.deviceName,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = device.macAddress,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    val packetCount = scanLogs.count { it.macAddress == device.macAddress }
                                    Text(
                                        text = "$packetCount paquete${if (packetCount != 1) "s" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDeviceSelector = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

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
            // Lista de paquetes en tiempo real
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = filteredLogs,
                    key = { log -> log.id } // Usar el ID único del modelo
                ) { log ->
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
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isFavorite) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = { expanded = !expanded }
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
                    log.isConnectable?.let { 
                        PacketInfoRow("Conectable", if (it) "Sí ✅" else "No ❌") 
                    }
                    log.advertisingFlags?.let { 
                        PacketInfoRow("Adv. Flags", "0x${it.toString(16).uppercase()}") 
                    }
                }
            )
            
            // Raw Bytes completos
            log.getRawBytesHex()?.let { hexData ->
                Spacer(modifier = Modifier.height(12.dp))
                PacketInfoSection(
                    title = "🔬 Raw Scan Record (${log.rawBytes?.size ?: 0} bytes)",
                    content = {
                        Text(
                            text = hexData,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                        )
                    }
                )
            }

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
            
            // Sección expandida con decodificación y análisis técnico
            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))
                
                DecodedDataSection(log)
            }
            
            // Indicador de expansión
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (expanded) "▲ Ocultar análisis técnico" else "▼ Ver análisis técnico",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun DecodedDataSection(log: BLEScanLog) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "🔓 Análisis y Decodificación Técnica",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Análisis de RSSI
        PacketInfoSection(
            title = "📶 Análisis de Señal (RSSI)",
            content = {
                val rssiQuality = when {
                    log.rssi >= -50 -> "Excelente"
                    log.rssi >= -60 -> "Muy Buena"
                    log.rssi >= -70 -> "Buena"
                    log.rssi >= -80 -> "Regular"
                    else -> "Débil"
                }
                val distance = estimateDistance(log.rssi, log.txPower ?: -59)
                
                Text(
                    text = "Valor: ${log.rssi} dBm",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Calidad: $rssiQuality",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Distancia estimada: ${String.format("%.2f", distance)} metros",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "\nInterpretación: RSSI (Received Signal Strength Indicator) mide la potencia de la señal. Valores más cercanos a 0 indican mejor señal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        
        // Decodificación de Advertising Flags
        log.advertisingFlags?.let { flags ->
            Spacer(modifier = Modifier.height(12.dp))
            PacketInfoSection(
                title = "🏴 Decodificación de Advertising Flags",
                content = {
                    Text(
                        text = "Valor: 0x${flags.toString(16).uppercase()} (${flags} decimal)",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Flags decodificados:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (flags and 0x01 != 0) Text("• LE Limited Discoverable Mode", style = MaterialTheme.typography.bodySmall)
                    if (flags and 0x02 != 0) Text("• LE General Discoverable Mode", style = MaterialTheme.typography.bodySmall)
                    if (flags and 0x04 != 0) Text("• BR/EDR Not Supported", style = MaterialTheme.typography.bodySmall)
                    if (flags and 0x08 != 0) Text("• Simultaneous LE and BR/EDR Controller", style = MaterialTheme.typography.bodySmall)
                    if (flags and 0x10 != 0) Text("• Simultaneous LE and BR/EDR Host", style = MaterialTheme.typography.bodySmall)
                }
            )
        }
        
        // Decodificación de Manufacturer Data
        if (log.manufacturerData.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            PacketInfoSection(
                title = "🏭 Decodificación de Manufacturer Data",
                content = {
                    log.manufacturerData.forEach { (id, data) ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            val companyName = getCompanyName(id)
                            Text(
                                text = "Fabricante: $companyName",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Company ID: 0x${id.toString(16).uppercase().padStart(4, '0')} ($id)",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Data Hex: $data",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                            
                            // Intentar decodificar como texto
                            val decodedText = hexToAscii(data)
                            if (decodedText.isNotEmpty()) {
                                Text(
                                    text = "Como texto: \"$decodedText\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            Divider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                    
                    Text(
                        text = "\nℹ️ Cómo leer Manufacturer Data:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• Cada par de caracteres hex representa 1 byte\n" +
                               "• El formato depende del fabricante\n" +
                               "• Para iBeacons: 02 15 [UUID] [Major] [Minor] [TxPower]",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
        
        // Decodificación de Raw Bytes
        log.rawBytes?.let { bytes ->
            Spacer(modifier = Modifier.height(12.dp))
            PacketInfoSection(
                title = "🔬 Estructura del Paquete BLE",
                content = {
                    Text(
                        text = "Tamaño total: ${bytes.size} bytes",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Parsear la estructura AD
                    parseAdStructure(bytes).forEach { ad ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "Tipo: ${ad.typeName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Tipo AD: 0x${ad.type.toString(16).uppercase()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "Longitud: ${ad.length} bytes",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Data: ${ad.data}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "\nℹ️ Formato AD (Advertising Data):",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Cada sección tiene: [Longitud] [Tipo] [Datos]\n" +
                               "• Longitud: 1 byte (tamaño de Tipo + Datos)\n" +
                               "• Tipo: 1 byte (indica qué contiene)\n" +
                               "• Datos: N bytes (contenido variable)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
        
        // Explicación de iBeacon
        log.iBeaconData?.let { ibeacon ->
            Spacer(modifier = Modifier.height(12.dp))
            PacketInfoSection(
                title = "✅ Decodificación iBeacon",
                content = {
                    Text(
                        text = "UUID: ${ibeacon.uuid}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "→ Identifica el grupo/aplicación del beacon",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Major: ${ibeacon.major} (0x${ibeacon.major.toString(16).uppercase()})",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "→ Identifica un subgrupo (ej: ubicación)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Minor: ${ibeacon.minor} (0x${ibeacon.minor.toString(16).uppercase()})",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "→ Identifica un beacon específico",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "TX Power: ${ibeacon.txPower} dBm",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "→ Potencia a 1 metro (para calcular distancia)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "\nℹ️ Formato iBeacon:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Manufacturer Data de Apple (0x004C):\n" +
                               "• 02 15: Prefijo iBeacon\n" +
                               "• 16 bytes: UUID\n" +
                               "• 2 bytes: Major\n" +
                               "• 2 bytes: Minor\n" +
                               "• 1 byte: TX Power",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
    }
}

// Funciones auxiliares para decodificación
private fun estimateDistance(rssi: Int, txPower: Int): Double {
    if (rssi == 0) return -1.0
    val ratio = rssi * 1.0 / txPower
    return if (ratio < 1.0) {
        Math.pow(ratio, 10.0)
    } else {
        0.89976 * Math.pow(ratio, 7.7095) + 0.111
    }
}

private fun getCompanyName(id: Int): String {
    return when (id) {
        0x004C -> "Apple Inc."
        0x0006 -> "Microsoft"
        0x00E0 -> "Google"
        0x0075 -> "Samsung Electronics Co. Ltd."
        0x0087 -> "Garmin International, Inc."
        0x0157 -> "Xiaomi Inc."
        0x0059 -> "Nordic Semiconductor ASA"
        0x0171 -> "Shenzhen Feasycom Technology Co., Ltd."
        else -> "Desconocido (ID: 0x${id.toString(16).uppercase()})"
    }
}

private fun hexToAscii(hex: String): String {
    val cleaned = hex.replace(" ", "")
    val result = StringBuilder()
    for (i in cleaned.indices step 2) {
        if (i + 1 < cleaned.length) {
            val str = cleaned.substring(i, i + 2)
            val charCode = str.toInt(16)
            val char = charCode.toChar()
            if (char.isLetterOrDigit() || char.isWhitespace() || (charCode in 32..126)) {
                result.append(char)
            }
        }
    }
    return result.toString().trim()
}

data class AdStructure(
    val length: Int,
    val type: Int,
    val typeName: String,
    val data: String
)

private fun parseAdStructure(bytes: ByteArray): List<AdStructure> {
    val structures = mutableListOf<AdStructure>()
    var index = 0
    
    while (index < bytes.size) {
        val length = bytes[index].toInt() and 0xFF
        if (length == 0) break
        
        index++
        if (index >= bytes.size) break
        
        val type = bytes[index].toInt() and 0xFF
        val typeName = getAdTypeName(type)
        
        index++
        val dataLength = length - 1
        if (index + dataLength > bytes.size) break
        
        val data = bytes.sliceArray(index until index + dataLength)
            .joinToString(" ") { "%02X".format(it) }
        
        structures.add(AdStructure(length, type, typeName, data))
        index += dataLength
    }
    
    return structures
}

private fun getAdTypeName(type: Int): String {
    return when (type) {
        0x01 -> "Flags"
        0x02 -> "Incomplete List of 16-bit Service UUIDs"
        0x03 -> "Complete List of 16-bit Service UUIDs"
        0x04 -> "Incomplete List of 32-bit Service UUIDs"
        0x05 -> "Complete List of 32-bit Service UUIDs"
        0x06 -> "Incomplete List of 128-bit Service UUIDs"
        0x07 -> "Complete List of 128-bit Service UUIDs"
        0x08 -> "Shortened Local Name"
        0x09 -> "Complete Local Name"
        0x0A -> "TX Power Level"
        0xFF -> "Manufacturer Specific Data"
        else -> "Unknown Type (0x${type.toString(16).uppercase()})"
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


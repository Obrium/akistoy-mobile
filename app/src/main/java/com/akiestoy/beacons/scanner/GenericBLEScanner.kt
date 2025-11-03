package com.akiestoy.beacons.scanner

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import com.akiestoy.beacons.model.BLEScanLog
import com.akiestoy.beacons.model.IBeaconData
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Scanner BLE genérico para debuggear qué dispositivos están cerca
 * Útil para verificar si los beacons están transmitiendo
 */
class GenericBLEScanner(context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    // Flow para emitir los logs de escaneo a la UI
    private val _scanLogs = MutableSharedFlow<BLEScanLog>(replay = 100)
    val scanLogs: SharedFlow<BLEScanLog> = _scanLogs.asSharedFlow()
    
    // Control de logs (solo cada 5 segundos)
    private var lastLogTime = 0L
    private var packetsSinceLastLog = 0

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val rssi = result.rssi
            val scanRecord = result.scanRecord

            // Recopilar manufacturer data e iBeacon data primero
            val manufacturerDataMap = mutableMapOf<Int, String>()
            var iBeaconData: IBeaconData? = null
            
            scanRecord?.let { record ->
                val manufacturerData = record.manufacturerSpecificData
                if (manufacturerData.size() > 0) {
                    for (i in 0 until manufacturerData.size()) {
                        val manufacturerId = manufacturerData.keyAt(i)
                        val data = manufacturerData.valueAt(i)
                        manufacturerDataMap[manufacturerId] = bytesToHex(data)

                        // Si es Apple (0x004C), intentar parsear como iBeacon
                        if (manufacturerId == 0x004C && data.size >= 23) {
                            iBeaconData = parseIBeaconDataToModel(data)
                        }
                    }
                }
            }

            // Obtener el nombre del dispositivo de múltiples fuentes
            val parsedName = parseCompleteLocalName(scanRecord?.bytes)
            
            // Intentar obtener el nombre real del dispositivo primero
            val realName = device.name ?: scanRecord?.deviceName ?: parsedName
            
            val deviceName = if (realName != null) {
                // Si tiene nombre real, usarlo
                realName
            } else if (iBeaconData != null) {
                // Si es un iBeacon sin nombre, generar nombre descriptivo
                "iBeacon [${iBeaconData.major}:${iBeaconData.minor}]"
            } else if (manufacturerDataMap.isNotEmpty()) {
                // Si tiene manufacturer data, mostrar el fabricante
                val manufacturerId = manufacturerDataMap.keys.first()
                when (manufacturerId) {
                    0x004C -> "Apple (0x004C)"
                    0x0006 -> "Microsoft (0x0006)"
                    0x00E0 -> "Google (0x00E0)"
                    0x0075 -> "Samsung (0x0075)"
                    0x0087 -> "Garmin (0x0087)"
                    0x0157 -> "Xiaomi (0x0157)"
                    else -> "Manufacturer 0x${manufacturerId.toString(16).uppercase()}"
                }
            } else {
                // Fallback final
                "Unknown BLE Device"
            }

            // Recopilar service UUIDs
            val serviceUuidsList = scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()

            // Capturar raw bytes completos del scan record
            val rawBytes = scanRecord?.bytes
            
            // Capturar advertising flags
            val advFlags = scanRecord?.advertiseFlags
            
            // Determinar si es conectable
            val isConnectable = result.isConnectable

            // Crear el log
            val scanLog = BLEScanLog(
                deviceName = deviceName,
                macAddress = device.address,
                rssi = rssi,
                txPower = scanRecord?.txPowerLevel,
                manufacturerData = manufacturerDataMap,
                serviceUuids = serviceUuidsList,
                iBeaconData = iBeaconData,
                rawBytes = rawBytes,
                advertisingFlags = advFlags,
                isConnectable = isConnectable
            )

            // Emitir el log (no bloqueante)
            try {
                _scanLogs.tryEmit(scanLog)
            } catch (e: Exception) {
                Log.e(TAG, "Error emitting scan log: ${e.message}")
            }

            // Contar paquetes y log resumido cada 5 segundos
            packetsSinceLastLog++
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastLogTime >= 5000) { // 5 segundos
                val isBeacon = iBeaconData != null
                Log.d(TAG, "📊 BLE scan: $packetsSinceLastLog packets in 5s | Latest: $deviceName${if (isBeacon) " ✅ iBeacon" else ""}")
                lastLogTime = currentTime
                packetsSinceLastLog = 0
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            // Log solo si hay resultados significativos
            if (results.size > 0) {
                Log.d(TAG, "📦 Batch scan: ${results.size} devices")
            }
            results.forEach { result ->
                onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan failed with error code: $errorCode")
        }
    }

    private fun parseIBeaconDataToModel(data: ByteArray): IBeaconData? {
        return try {
            // iBeacon format: 02 15 [UUID 16 bytes] [Major 2 bytes] [Minor 2 bytes] [TX Power 1 byte]
            if (data[0] == 0x02.toByte() && data[1] == 0x15.toByte()) {
                // Extract UUID (bytes 2-17)
                val uuid = StringBuilder()
                for (i in 2..17) {
                    uuid.append(String.format("%02x", data[i]))
                    if (i == 5 || i == 7 || i == 9 || i == 11) uuid.append("-")
                }

                // Extract Major (bytes 18-19)
                val major = ((data[18].toInt() and 0xFF) shl 8) or (data[19].toInt() and 0xFF)

                // Extract Minor (bytes 20-21)
                val minor = ((data[20].toInt() and 0xFF) shl 8) or (data[21].toInt() and 0xFF)

                // Extract TX Power (byte 22)
                val txPower = data[22].toInt()

                IBeaconData(
                    uuid = uuid.toString(),
                    major = major,
                    minor = minor,
                    txPower = txPower
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing iBeacon data: ${e.message}")
            null
        }
    }

    fun startScanning() {
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleScanner?.startScan(null, settings, scanCallback)
        Log.d(TAG, "Started generic BLE scanning...")
    }

    fun stopScanning() {
        bleScanner?.stopScan(scanCallback)
        Log.d(TAG, "Stopped generic BLE scanning")
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { String.format("%02X", it) }
    }

    /**
     * Parsea el campo "Complete Local Name" (0x09) del advertisement data
     */
    private fun parseCompleteLocalName(bytes: ByteArray?): String? {
        if (bytes == null || bytes.isEmpty()) return null

        var index = 0
        while (index < bytes.size) {
            val length = bytes[index].toInt() and 0xFF
            if (length == 0 || index + length >= bytes.size) break

            val type = bytes[index + 1].toInt() and 0xFF

            // 0x09 = Complete Local Name, 0x08 = Shortened Local Name
            if (type == 0x09 || type == 0x08) {
                val nameBytes = bytes.copyOfRange(index + 2, index + 1 + length)
                return String(nameBytes, Charsets.UTF_8)
            }

            index += length + 1
        }
        return null
    }

    companion object {
        private const val TAG = "GenericBLEScanner"
    }
}

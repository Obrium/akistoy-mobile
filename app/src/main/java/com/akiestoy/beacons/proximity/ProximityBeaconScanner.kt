package com.akiestoy.beacons.proximity

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Scanner BLE en modo passive (sin conexión) con LOW_LATENCY
 * Diseñado para escanear beacons y notificar detecciones
 * Solo procesa beacons que estén en la lista de favoritos
 */
class ProximityBeaconScanner(
    private val context: Context,
    private val onBeaconDetected: (macAddress: String, rssi: Int) -> Unit,
    private val isFavorite: (String) -> Boolean
) {
    private val TAG = "ProximityBeaconScanner"

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private var scanCallback: ScanCallback? = null

    // Estado del scanner
    private val _isScanning = MutableStateFlow(false)
    val isScanning: Flow<Boolean> = _isScanning.asStateFlow()

    companion object {
        // UUID de los beacons iBeacon (formato Apple)
        private const val IBEACON_UUID = "e2c56db5-dffb-48d2-b060-d0f5a71096e0"
    }

    /**
     * Inicia el escaneo BLE en modo LOW_LATENCY
     */
    @SuppressLint("MissingPermission")
    fun startScanning(scope: CoroutineScope) {
        if (_isScanning.value) {
            Log.w(TAG, "⚠️ Scanning already in progress")
            return
        }

        if (bluetoothAdapter == null || bluetoothLeScanner == null) {
            Log.e(TAG, "❌ Bluetooth not available on this device")
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "❌ Bluetooth is disabled")
            return
        }

        Log.i(TAG, "🔍 Starting BLE scanning in BALANCED mode...")
        Log.d(TAG, "📍 Looking for iBeacon UUID: $IBEACON_UUID")

        // Configurar ScanSettings para BALANCED (balance entre batería y rendimiento)
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)  // Escaneo balanceado - ahorra ~60% batería
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setReportDelay(0L)  // Reportar inmediatamente
            .build()

        // Configurar filtros para detectar solo nuestros beacons
        val scanFilters = buildScanFilters()

        // Crear callback para recibir resultados
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result, scope)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { result ->
                    handleScanResult(result, scope)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed with error code: $errorCode")
                _isScanning.value = false
            }
        }

        // Iniciar escaneo
        try {
            bluetoothLeScanner.startScan(scanFilters, scanSettings, scanCallback)
            _isScanning.value = true
            Log.i(TAG, "✅ BLE scan started successfully")

        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Permission denied for BLE scanning", e)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting BLE scan", e)
        }
    }

    /**
     * Detiene el escaneo BLE
     */
    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (!_isScanning.value) {
            Log.w(TAG, "⚠️ Scanning is not active")
            return
        }

        Log.i(TAG, "🛑 Stopping BLE scanning...")

        try {
            scanCallback?.let { callback ->
                bluetoothLeScanner?.stopScan(callback)
            }
            _isScanning.value = false

            Log.i(TAG, "✅ BLE scan stopped successfully")

        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Permission denied for stopping BLE scan", e)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping BLE scan", e)
        }
    }

    /**
     * Construye filtros de escaneo para nuestros beacons
     */
    private fun buildScanFilters(): List<ScanFilter> {
        val filters = mutableListOf<ScanFilter>()

        // Filtro 1: Por UUID del servicio (si los beacons lo anuncian)
        try {
            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(UUID.fromString(IBEACON_UUID)))
                .build()
            filters.add(filter)
        } catch (e: Exception) {
            Log.w(TAG, "Could not create UUID filter", e)
        }

        // Si no hay filtros específicos, retornar lista vacía (escanear todo)
        // En producción, puedes agregar más filtros específicos
        return filters
    }

    /**
     * Procesa un resultado de escaneo
     */
    private fun handleScanResult(result: ScanResult, scope: CoroutineScope) {
        val device = result.device
        val rssi = result.rssi
        val macAddress = device.address

        // Verificar si es un iBeacon válido
        val scanRecord = result.scanRecord
        if (scanRecord == null) {
            Log.v(TAG, "❌ No scan record for device: $macAddress")
            return
        }

        // Extraer datos del beacon (iBeacon parsing)
        val beaconData = parseIBeaconData(scanRecord.bytes)
        if (beaconData != null) {
            val (uuid, major, minor, txPower) = beaconData
            Log.d(TAG, "📡 Found iBeacon: MAC=$macAddress, UUID=$uuid, Major=$major, Minor=$minor, RSSI=$rssi")

            // Verificar que sea nuestro UUID
            if (uuid.equals(IBEACON_UUID, ignoreCase = true)) {
                Log.d(TAG, "✅ UUID matches! Checking if favorite...")
                // Verificar si el beacon está en favoritos
                if (isFavorite(macAddress)) {
                    Log.i(TAG, "⭐ Beacon is favorite! Notifying detection")
                    // Notificar detección mediante callback
                    onBeaconDetected(macAddress, rssi)
                } else {
                    Log.d(TAG, "⚠️ Beacon not in favorites: $macAddress")
                }
            } else {
                Log.v(TAG, "⚠️ UUID doesn't match. Expected: $IBEACON_UUID, Got: $uuid")
            }
        } else {
            Log.v(TAG, "⚠️ Not an iBeacon or invalid format: $macAddress")
        }
    }

    /**
     * Parsea los datos de advertising de un iBeacon
     * Formato iBeacon: [Prefix][UUID][Major][Minor][TX Power]
     */
    private fun parseIBeaconData(scanData: ByteArray?): IBeaconData? {
        if (scanData == null || scanData.size < 30) return null

        try {
            // Buscar el prefijo de iBeacon (0x02 0x15)
            // El formato completo es: [0x02, 0x01, 0x06, 0x1A, 0xFF, 0x4C, 0x00, 0x02, 0x15, UUID...]
            var startIndex = -1
            for (i in 0 until scanData.size - 1) {
                if (scanData[i] == 0x02.toByte() && scanData[i + 1] == 0x15.toByte()) {
                    startIndex = i
                    break
                }
            }

            if (startIndex == -1 || startIndex + 25 > scanData.size) {
                return null
            }

            // UUID (16 bytes)
            val uuidBytes = scanData.sliceArray((startIndex + 2)..(startIndex + 17))
            val uuid = bytesToUUID(uuidBytes)

            // Major (2 bytes)
            val major = ((scanData[startIndex + 18].toInt() and 0xFF) shl 8) or
                    (scanData[startIndex + 19].toInt() and 0xFF)

            // Minor (2 bytes)
            val minor = ((scanData[startIndex + 20].toInt() and 0xFF) shl 8) or
                    (scanData[startIndex + 21].toInt() and 0xFF)

            // TX Power (1 byte signed)
            val txPower = scanData[startIndex + 22].toInt()

            return IBeaconData(uuid, major, minor, txPower)

        } catch (e: Exception) {
            Log.w(TAG, "Error parsing iBeacon data", e)
            return null
        }
    }

    /**
     * Convierte bytes a formato UUID
     */
    private fun bytesToUUID(bytes: ByteArray): String {
        val hex = bytes.joinToString("") { "%02x".format(it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
                "${hex.substring(16, 20)}-${hex.substring(20, 32)}"
    }

    /**
     * Clase de datos para información de iBeacon
     */
    private data class IBeaconData(
        val uuid: String,
        val major: Int,
        val minor: Int,
        val txPower: Int
    )
}

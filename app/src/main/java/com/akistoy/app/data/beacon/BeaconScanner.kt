package com.akistoy.app.data.beacon

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.akistoy.app.domain.model.BeaconEvent
import com.akistoy.app.domain.model.BeaconProximity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

interface BeaconScanner {
    fun startScanning(uuids: List<String>)
    fun stopScanning()
    val detections: Flow<BeaconEvent>
}

@Singleton
class RealBeaconScanner @Inject constructor(
    @ApplicationContext private val context: Context
) : BeaconScanner {

    private val scope = CoroutineScope(Dispatchers.Default)
    private val _detections = MutableSharedFlow<BeaconEvent>(extraBufferCapacity = 64)
    override val detections: Flow<BeaconEvent> = _detections
        .shareIn(scope, SharingStarted.WhileSubscribed(5000), replay = 0)

    private var scanner: BluetoothLeScanner? = null
    private var callback: ScanCallback? = null

    @SuppressLint("MissingPermission")
    override fun startScanning(uuids: List<String>) {
        // ESTABILIDAD: Validación temprana de pre-condiciones
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val adapter = bluetoothManager?.adapter

        if (adapter == null) {
            Log.e("RealBeaconScanner", "Bluetooth adapter not available")
            return
        }

        if (!adapter.isEnabled) {
            Log.w("RealBeaconScanner", "Bluetooth is disabled, cannot start scanning")
            return
        }

        scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e("RealBeaconScanner", "BluetoothLeScanner not available")
            return
        }

        // ESTABILIDAD: Validar y filtrar UUIDs inválidos
        val validUuids = uuids.filter { it.isNotBlank() }
        if (validUuids.isEmpty()) {
            Log.w("RealBeaconScanner", "No valid UUIDs provided, scanning without filters")
        }

        val filters = validUuids.mapNotNull { uuid ->
            try {
                // Validar formato UUID antes de crear ParcelUuid
                java.util.UUID.fromString(uuid)  // Lanza IllegalArgumentException si es inválido
                ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(uuid)).build()
            } catch (ex: IllegalArgumentException) {
                Log.e("RealBeaconScanner", "Invalid UUID format: $uuid", ex)
                null
            }
        }

        Log.d("RealBeaconScanner", "Starting scan with ${filters.size} valid filters from ${validUuids.size} UUIDs")

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .build()

        if (callback != null) {
            stopScanning()
        }
        callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                scope.launch {
                    parseResult(result)?.let { _detections.emit(it) }
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                scope.launch {
                    results.mapNotNull(::parseResult).forEach { _detections.emit(it) }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                // ESTABILIDAD: Manejo detallado de errores de scan
                val errorMessage = when (errorCode) {
                    android.bluetooth.le.ScanCallback.SCAN_FAILED_ALREADY_STARTED ->
                        "Scan already started"
                    android.bluetooth.le.ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED ->
                        "App registration failed"
                    android.bluetooth.le.ScanCallback.SCAN_FAILED_INTERNAL_ERROR ->
                        "Internal error"
                    android.bluetooth.le.ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED ->
                        "Feature unsupported"
                    else -> "Unknown error code: $errorCode"
                }
                Log.e("RealBeaconScanner", "Scan failed: $errorMessage")

                // ESTABILIDAD: Auto-recovery - intentar detener y limpiar
                callback?.let {
                    try {
                        scanner?.stopScan(it)
                    } catch (e: Exception) {
                        Log.w("RealBeaconScanner", "Error stopping scan after failure", e)
                    }
                }
                callback = null
            }
        }

        // ESTABILIDAD: Manejo robusto de excepciones al iniciar scan
        try {
            if (filters.isNullOrEmpty()) {
                scanner?.startScan(callback)
            } else {
                scanner?.startScan(filters, settings, callback)
            }
            Log.i("RealBeaconScanner", "Scan started successfully")
        } catch (security: SecurityException) {
            Log.e("RealBeaconScanner", "Missing Bluetooth permission", security)
            callback = null
        } catch (illegal: IllegalStateException) {
            Log.e("RealBeaconScanner", "Bluetooth OFF or unavailable", illegal)
            callback = null
        } catch (e: Exception) {
            Log.e("RealBeaconScanner", "Unexpected error starting scan", e)
            callback = null
        }
    }

    @SuppressLint("MissingPermission")
    override fun stopScanning() {
        try {
            callback?.let { scanner?.stopScan(it) }
        } catch (security: SecurityException) {
            Log.e("RealBeaconScanner", "stopScan missing permission", security)
        }
        callback = null
    }

    @SuppressLint("MissingPermission")
    private fun parseResult(result: ScanResult): BeaconEvent? {
        // Usar MAC address como beaconId para consistencia
        val beaconId = result.device.address ?: return null

        val proximity = when {
            result.rssi >= -60 -> BeaconProximity.Immediate
            result.rssi in -80..-61 -> BeaconProximity.Near
            result.rssi < -80 -> BeaconProximity.Far
            else -> BeaconProximity.Unknown
        }
        val distance = calculateDistance(result.rssi)

        // Obtener el nombre del dispositivo desde múltiples fuentes
        val deviceName = result.device?.name  // Prioridad 1: nombre del dispositivo
            ?: result.scanRecord?.deviceName  // Prioridad 2: nombre del scan record
            ?: null  // Si no hay nombre, será null

        return BeaconEvent(
            beaconId = beaconId,
            namespace = result.scanRecord?.serviceUuids?.firstOrNull()?.uuid?.toString(),
            rssi = result.rssi,
            timestamp = Clock.System.now(),
            proximity = proximity,
            distanceMeters = distance,
            zoneName = deviceName
        )
    }

    /**
     * Calcula la distancia en metros basándose en el RSSI
     * Fórmula: distance = 10 ^ ((txPower - rssi) / (10 * n))
     * txPower: -59 dBm (potencia típica de beacons BLE a 1 metro)
     * n: 2.0 (factor de propagación para ambientes abiertos)
     */
    private fun calculateDistance(rssi: Int): Double {
        val txPower = -59.0 // Potencia de transmisión a 1 metro
        val n = 2.0 // Factor de propagación

        if (rssi == 0) return -1.0 // Señal no válida

        val ratio = (txPower - rssi) / (10.0 * n)
        val distance = Math.pow(10.0, ratio)

        // Redondear a 2 decimales
        return (distance * 100).toInt() / 100.0
    }

    private fun parseIdentifier(result: ScanResult): String {
        val record = result.scanRecord ?: return result.device.address ?: "unknown"
        val serviceData = record.serviceData
        if (serviceData != null && serviceData.isNotEmpty()) {
            return serviceData.entries.first().value.joinToString(separator = "") {
                String.format("%02x", it)
            }
        }
        return record.deviceName ?: result.device.address ?: "unknown"
    }
}

class FakeBeaconScanner @Inject constructor() : BeaconScanner {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val _detections = MutableSharedFlow<BeaconEvent>(extraBufferCapacity = 16)
    override val detections: Flow<BeaconEvent> = _detections
        .shareIn(scope, SharingStarted.WhileSubscribed(5000), replay = 0)

    override fun startScanning(uuids: List<String>) {
        scope.launch {
            uuids.takeIf { it.isNotEmpty() }?.forEachIndexed { index, uuid ->
                val rssi = -60 + index
                _detections.emit(
                    BeaconEvent(
                        beaconId = uuid.takeLast(12),
                        namespace = uuid,
                        rssi = rssi,
                        timestamp = Clock.System.now(),
                        proximity = BeaconProximity.Near,
                        distanceMeters = 1.0 + (index * 0.5)
                    )
                )
            }
        }
    }

    override fun stopScanning() {
        // No-op for fake implementation
    }
}

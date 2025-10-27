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
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val adapter = bluetoothManager?.adapter ?: return
        if (!adapter.isEnabled) return
        scanner = adapter.bluetoothLeScanner
        val filters = uuids.takeIf { it.isNotEmpty() }?.mapNotNull { uuid ->
            try {
                ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(uuid)).build()
            } catch (ex: IllegalArgumentException) {
                Log.w("RealBeaconScanner", "Invalid UUID $uuid", ex)
                null
            }
        }

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
                Log.e("RealBeaconScanner", "Scan failed with $errorCode")
            }
        }

        try {
            if (filters.isNullOrEmpty()) {
                scanner?.startScan(callback)
            } else {
                scanner?.startScan(filters, settings, callback)
            }
        } catch (security: SecurityException) {
            Log.e("RealBeaconScanner", "Missing permission", security)
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

    private fun parseResult(result: ScanResult): BeaconEvent? {
        val beaconId = when {
            result.scanRecord == null -> result.device.address ?: return null
            else -> parseIdentifier(result)
        }
        val proximity = when {
            result.rssi >= -60 -> BeaconProximity.Immediate
            result.rssi in -80..-61 -> BeaconProximity.Near
            result.rssi < -80 -> BeaconProximity.Far
            else -> BeaconProximity.Unknown
        }
        val distance = calculateDistance(result.rssi)
        return BeaconEvent(
            beaconId = beaconId,
            namespace = result.scanRecord?.serviceUuids?.firstOrNull()?.uuid?.toString(),
            rssi = result.rssi,
            timestamp = Clock.System.now(),
            proximity = proximity,
            distanceMeters = distance
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

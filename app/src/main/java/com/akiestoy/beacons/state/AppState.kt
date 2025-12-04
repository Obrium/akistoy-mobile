package com.akiestoy.beacons.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Log de evento de API para mostrar en la UI
 */
data class ApiEventLog(
    val timestamp: Long,
    val eventType: String,
    val zoneName: String,
    val fromZone: String? = null,
    val status: String, // "SENDING", "SUCCESS", "ERROR", "SKIPPED"
    val message: String? = null
)

/**
 * Estado actual de zona detectada (se actualiza ANTES de enviar al backend)
 */
data class CurrentZoneState(
    val zoneName: String?,
    val beaconMac: String?,
    val rssi: Int?,
    val isInsideCompany: Boolean,
    val lastUpdate: Long
)

/**
 * Estado global de la aplicación (similar a Redux)
 * Mantiene información sobre la zona actual basada en el beacon más cercano
 */
object AppState {

    /**
     * Zona actual detectada (beacon más cercano)
     */
    private val _currentZone = MutableStateFlow<ZoneInfo?>(null)
    val currentZone: StateFlow<ZoneInfo?> = _currentZone.asStateFlow()

    /**
     * Beacon seleccionado manualmente (MAC address)
     * Cuando está establecido, este beacon tiene prioridad sobre la detección automática
     */
    private val _manuallySelectedBeaconMac = MutableStateFlow<String?>(null)
    val manuallySelectedBeaconMac: StateFlow<String?> = _manuallySelectedBeaconMac.asStateFlow()

    /**
     * Logs de eventos API (para debug en UI)
     */
    private val _apiLogs = MutableStateFlow<List<ApiEventLog>>(emptyList())
    val apiLogs: StateFlow<List<ApiEventLog>> = _apiLogs.asStateFlow()

    /**
     * Estado actual de zona (se actualiza ANTES de enviar al backend)
     */
    private val _zoneState = MutableStateFlow(CurrentZoneState(
        zoneName = null,
        beaconMac = null,
        rssi = null,
        isInsideCompany = false,
        lastUpdate = 0L
    ))
    val zoneState: StateFlow<CurrentZoneState> = _zoneState.asStateFlow()

    // Máximo de logs a mantener
    private const val MAX_LOGS = 20

    /**
     * Actualiza la zona actual basándose en el beacon más cercano
     */
    fun updateCurrentZone(zone: ZoneInfo?) {
        _currentZone.value = zone
    }

    /**
     * Limpia la zona actual
     */
    fun clearCurrentZone() {
        _currentZone.value = null
    }

    /**
     * Establece el beacon seleccionado manualmente
     */
    fun setManuallySelectedBeacon(macAddress: String?) {
        _manuallySelectedBeaconMac.value = macAddress
    }

    /**
     * Limpia la selección manual de beacon
     */
    fun clearManuallySelectedBeacon() {
        _manuallySelectedBeaconMac.value = null
    }

    /**
     * Agrega un log de evento API
     */
    fun addApiLog(log: ApiEventLog) {
        val currentLogs = _apiLogs.value.toMutableList()
        currentLogs.add(0, log) // Agregar al inicio
        if (currentLogs.size > MAX_LOGS) {
            currentLogs.removeAt(currentLogs.size - 1)
        }
        _apiLogs.value = currentLogs
    }

    /**
     * Limpia los logs de API
     */
    fun clearApiLogs() {
        _apiLogs.value = emptyList()
    }

    /**
     * Actualiza el estado de zona (llamado ANTES de enviar al backend)
     */
    fun updateZoneState(state: CurrentZoneState) {
        _zoneState.value = state
    }
}

/**
 * Información de la zona actual
 */
data class ZoneInfo(
    val beaconName: String,
    val beaconMac: String,
    val rssi: Int,
    val distance: Double? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Distancia estimada en metros basada en RSSI
     * Fórmula aproximada: d = 10 ^ ((txPower - RSSI) / (10 * n))
     * donde n es el factor de propagación (típicamente 2-4)
     */
    fun estimatedDistance(txPower: Int = -59): Double {
        val n = 2.0 // Factor de propagación para ambiente interior
        return Math.pow(10.0, (txPower - rssi) / (10.0 * n))
    }

    /**
     * Clasificación de proximidad
     */
    fun proximityLevel(): ProximityLevel {
        return when {
            rssi >= -50 -> ProximityLevel.IMMEDIATE
            rssi >= -70 -> ProximityLevel.NEAR
            rssi >= -90 -> ProximityLevel.FAR
            else -> ProximityLevel.UNKNOWN
        }
    }
}

enum class ProximityLevel {
    IMMEDIATE,  // Muy cerca (< 0.5m)
    NEAR,       // Cerca (0.5m - 3m)
    FAR,        // Lejos (3m+)
    UNKNOWN     // Señal muy débil
}

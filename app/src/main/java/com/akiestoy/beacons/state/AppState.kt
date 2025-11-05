package com.akiestoy.beacons.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

package com.akiestoy.beacons.model.proximity

/**
 * Estado interno de proximidad para un beacon específico
 * Usado por el ProximityManager para tracking de cada beacon
 */
data class BeaconState(
    /**
     * MAC address del beacon
     */
    val beaconId: String,

    /**
     * Si el beacon está actualmente en proximidad (dentro del rango)
     */
    var isInProximity: Boolean = false,

    /**
     * Buffer circular para RSSI (media móvil)
     */
    val rssiBuffer: MutableList<Int> = mutableListOf(),

    /**
     * Timestamp de la última detección del beacon (en ms)
     */
    var lastSeenTimestamp: Long = 0L,

    /**
     * Timestamp del último evento enviado (en ms)
     */
    var lastEventTimestamp: Long = 0L,

    /**
     * Timestamp del último heartbeat enviado (en ms)
     */
    var lastHeartbeatTimestamp: Long = 0L,

    /**
     * Configuración de umbrales específica para este beacon
     */
    val config: ProximityConfig = ProximityConfig.default(beaconId)
) {
    /**
     * Agrega una nueva lectura de RSSI al buffer circular
     */
    fun addRssiSample(rssi: Int) {
        rssiBuffer.add(rssi)

        // Mantener solo las últimas N muestras (ventana móvil)
        if (rssiBuffer.size > config.movingAverageWindow) {
            rssiBuffer.removeAt(0)
        }
    }

    /**
     * Calcula el RSSI promedio actual
     */
    fun getAverageRssi(): Double {
        if (rssiBuffer.isEmpty()) return 0.0
        return rssiBuffer.average()
    }

    /**
     * Verifica si el beacon debe considerarse perdido (timeout)
     */
    fun isLost(currentTime: Long): Boolean {
        return (currentTime - lastSeenTimestamp) > config.exitTimeoutMs
    }

    /**
     * Verifica si es momento de enviar un heartbeat
     */
    fun shouldSendHeartbeat(currentTime: Long): Boolean {
        if (!isInProximity) return false
        return (currentTime - lastHeartbeatTimestamp) >= config.heartbeatIntervalMs
    }

    /**
     * Verifica si el RSSI promedio supera el umbral de entrada
     */
    fun isAboveEnterThreshold(): Boolean {
        if (rssiBuffer.size < 3) return false  // Necesita al menos 3 muestras
        return getAverageRssi() >= config.enterThreshold
    }

    /**
     * Verifica si el RSSI promedio está bajo el umbral de salida
     */
    fun isBelowExitThreshold(): Boolean {
        if (rssiBuffer.isEmpty()) return true
        return getAverageRssi() < config.exitThreshold
    }

    /**
     * Actualiza el timestamp de última detección
     */
    fun updateLastSeen(timestamp: Long = System.currentTimeMillis()) {
        lastSeenTimestamp = timestamp
    }

    /**
     * Marca que se envió un evento
     */
    fun markEventSent(timestamp: Long = System.currentTimeMillis()) {
        lastEventTimestamp = timestamp
    }

    /**
     * Marca que se envió un heartbeat
     */
    fun markHeartbeatSent(timestamp: Long = System.currentTimeMillis()) {
        lastHeartbeatTimestamp = timestamp
    }
}

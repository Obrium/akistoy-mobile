package com.akiestoy.beacons.model

/**
 * Representa una detección de beacon con toda su información
 * Basado en el protocolo iBeacon de Apple para beacons ESP32
 */
data class BeaconDetection(
    val uuid: String,                    // e2c56db5-dffb-48d2-b060-d0f5a71096e0
    val major: Int,                      // 100 (Baño) o 101 (Sala)
    val minor: Int,                      // 1
    val rssi: Int,                       // Intensidad de señal recibida (dBm)
    val txPower: Int,                    // Potencia transmitida (-59 dBm)
    val distance: Double,                // Distancia calculada en metros
    val location: BeaconLocation,        // Ubicación mapeada
    val proximity: ProximityZone,        // Zona de proximidad
    val macAddress: String? = null,      // MAC address del ESP32
    val timestamp: Long = System.currentTimeMillis()  // Timestamp de detección
) {
    /**
     * Obtiene una descripción legible de la distancia
     */
    fun getDistanceDescription(): String {
        return when {
            distance < 0 -> "Desconocida"
            distance < 1 -> "${String.format("%.1f", distance * 100)} cm"
            else -> "${String.format("%.1f", distance)} m"
        }
    }

    /**
     * Obtiene la calidad de la señal en porcentaje (aproximado)
     */
    fun getSignalQuality(): Int {
        // RSSI típicamente va de -100 (peor) a -40 (mejor)
        return ((100 + rssi) * 100 / 60).coerceIn(0, 100)
    }

    companion object {
        /**
         * UUID común para todos los beacons ESP32 del sistema
         */
        const val AKIESTOY_UUID = "e2c56db5-dffb-48d2-b060-d0f5a71096e0"
    }
}

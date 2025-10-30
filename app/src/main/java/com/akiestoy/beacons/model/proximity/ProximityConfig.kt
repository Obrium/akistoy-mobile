package com.akiestoy.beacons.model.proximity

/**
 * Configuración de umbrales de proximidad por beacon
 * Permite calibrar los umbrales individualmente para cada beacon
 */
data class ProximityConfig(
    /**
     * MAC address del beacon
     */
    val beaconId: String,

    /**
     * Umbral de entrada en dBm (por defecto -65)
     * El RSSI promedio debe SUPERAR este valor para generar evento ENTER
     */
    val enterThreshold: Int = DEFAULT_ENTER_THRESHOLD,

    /**
     * Umbral de salida en dBm (por defecto -70)
     * El RSSI promedio debe BAJAR de este valor para generar evento EXIT
     */
    val exitThreshold: Int = DEFAULT_EXIT_THRESHOLD,

    /**
     * Tiempo en milisegundos sin detectar el beacon para considerarlo EXIT (por defecto 10s)
     */
    val exitTimeoutMs: Long = DEFAULT_EXIT_TIMEOUT_MS,

    /**
     * Intervalo de heartbeat en milisegundos (por defecto 5s)
     */
    val heartbeatIntervalMs: Long = DEFAULT_HEARTBEAT_INTERVAL_MS,

    /**
     * Número de muestras para el promedio móvil (por defecto 5)
     */
    val movingAverageWindow: Int = DEFAULT_MOVING_AVERAGE_WINDOW
) {
    companion object {
        // Valores por defecto
        const val DEFAULT_ENTER_THRESHOLD = -65
        const val DEFAULT_EXIT_THRESHOLD = -70
        const val DEFAULT_EXIT_TIMEOUT_MS = 10_000L  // 10 segundos
        const val DEFAULT_HEARTBEAT_INTERVAL_MS = 5_000L  // 5 segundos
        const val DEFAULT_MOVING_AVERAGE_WINDOW = 5

        /**
         * Configuración por defecto para cualquier beacon
         */
        fun default(beaconId: String): ProximityConfig {
            return ProximityConfig(beaconId = beaconId)
        }
    }

    /**
     * Valida que los umbrales sean coherentes (exit < enter)
     */
    fun isValid(): Boolean {
        return exitThreshold < enterThreshold
    }
}
